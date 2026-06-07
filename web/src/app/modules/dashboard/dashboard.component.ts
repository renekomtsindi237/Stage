import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { KpiService } from './kpi.service';
import { SseService } from '@core/services/sse.service';
import { AuthService } from '@core/services/auth.service';
import { DashboardSummary, ParStat, CollecteStat } from './models/kpi.model';
import { formatDate } from '@angular/common';
import { fadeInUp, staggerIn, reveal } from '../../shared/animations';

interface DashboardKpi {
  label: string;
  value: string;
  icon: string;
  iconBg: string;
  iconColor: string;
  trend?: string;
  trendPositive?: boolean;
  footnote?: string;
}

@Component({
  selector: 'imf-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
  animations: [fadeInUp, staggerIn, reveal]
})
export class DashboardComponent implements OnInit, OnDestroy {

  summary: DashboardSummary | null = null;
  parStats: ParStat[] = [];
  collecteStats: CollecteStat[] = [];
  loading = true;
  error = '';
  nouvelleAlerte = false;
  readonly today = new Date();

  statutPretsData: any[] = [];
  agencesPerformanceData: any[] = [];
  evolutionMensuelleLabels: string[] = [];
  evolutionMensuelleDatasets: any[] = [];

  private sseSub?: Subscription;

  readonly dateDebut = formatDate(
    new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
    'yyyy-MM-dd', 'en'
  );
  readonly dateFin = formatDate(new Date(), 'yyyy-MM-dd', 'en');

  readonly dsiLinks = [
    { label: 'Utilisateurs', icon: 'manage_accounts', route: '/admin/users',     color: '#2563EB', bg: 'rgba(37,99,235,0.1)' },
    { label: 'Agences',      icon: 'business',         route: '/admin/agences',   color: '#0D9488', bg: 'rgba(13,148,136,0.1)' },
    { label: 'Reporting',    icon: 'bar_chart',         route: '/admin/reporting', color: '#8B5CF6', bg: 'rgba(139,92,246,0.1)' },
    { label: 'Alertes',      icon: 'notifications_active', route: '/admin/alertes', color: '#F59E0B', bg: 'rgba(245,158,11,0.1)' },
  ];

  constructor(
    private kpiService: KpiService,
    private sseService: SseService,
    public auth: AuthService,
    private router: Router,
  ) {}

  // ── Role helpers ────────────────────────────────────────────────
  get role(): string    { return this.auth.getRole() ?? ''; }
  get username(): string { return this.auth.getUsername() ?? 'Utilisateur'; }
  get imfNom(): string  { return this.auth.getImfNom() ?? 'Votre IMF'; }

  get isDsi(): boolean         { return this.role === 'DSI'; }
  get isDirecteur(): boolean   { return this.role === 'DIRECTEUR'; }
  get isResponsable(): boolean { return this.role === 'RESPONSABLE_RECOUVREMENT'; }
  get isAnalyste(): boolean    { return this.role === 'ANALYSTE'; }
  get isAgent(): boolean       { return this.role === 'AGENT'; }

  get roleIcon(): string {
    if (this.isDsi)         return 'admin_panel_settings';
    if (this.isDirecteur)   return 'business_center';
    if (this.isResponsable) return 'manage_search';
    if (this.isAnalyste)    return 'analytics';
    return 'person';
  }

  get roleLabel(): string {
    if (this.isDsi)         return 'DSI';
    if (this.isDirecteur)   return 'Directeur';
    if (this.isResponsable) return 'Responsable Recouvrement';
    if (this.isAnalyste)    return 'Analyste';
    if (this.isAgent)       return 'Agent';
    return this.role;
  }

  get roleBadgeClass(): string {
    if (this.isDsi)         return 'badge-dsi';
    if (this.isDirecteur)   return 'badge-directeur';
    if (this.isResponsable) return 'badge-responsable';
    if (this.isAnalyste)    return 'badge-analyste';
    return 'badge-agent';
  }

  get greeting(): string {
    const hour = new Date().getHours();
    const salut = hour < 12 ? 'Bonjour' : hour < 18 ? 'Bon après-midi' : 'Bonsoir';
    return `${salut}, ${this.username} !`;
  }

  get subgreeting(): string {
    if (this.isDsi)         return `Administration de ${this.imfNom}`;
    if (this.isDirecteur)   return `Vue d'ensemble — ${this.imfNom}`;
    if (this.isResponsable) return 'Suivi du portefeuille et recouvrement';
    if (this.isAnalyste)    return 'Tableau de bord analytique';
    if (this.isAgent)       return 'Vos statistiques personnelles';
    return 'Tableau de bord';
  }

  // ── Chart visibility ────────────────────────────────────────────
  get showParChart(): boolean      { return true; }
  get showCanauxChart(): boolean   { return this.isDirecteur || this.isAnalyste; }
  get showStatutChart(): boolean   { return this.isDirecteur || this.isResponsable || this.isAnalyste; }
  get showAgencesChart(): boolean  { return this.isDirecteur || this.isAnalyste; }
  get showEvolutionChart(): boolean { return !this.isAgent; }
  get showTasksSection(): boolean  { return this.isDirecteur; }
  get showAgentsTable(): boolean   { return this.isDirecteur || this.isResponsable; }

  // ── KPIs per role ────────────────────────────────────────────────
  get kpis(): DashboardKpi[] {
    if (!this.summary) return [];
    const s = this.summary;

    if (this.isDsi) return [
      { label: 'Encours total',  value: this.fmtM(s.encoursTotal),    icon: 'account_balance',     iconBg: 'rgba(37,99,235,0.12)',   iconColor: '#2563EB', footnote: 'Portefeuille actif' },
      { label: 'Alertes actives', value: String(s.nbAlertesActives),  icon: 'notifications_active', iconBg: 'rgba(245,158,11,0.12)', iconColor: '#F59E0B', trend: s.nbAlertesActives > 0 ? 'Attention requise' : 'Aucune alerte', trendPositive: s.nbAlertesActives === 0 },
      { label: 'Collectes MTD',  value: this.fmtM(s.totalCollectes),  icon: 'payments',            iconBg: 'rgba(16,185,129,0.12)', iconColor: '#10B981', trend: '+8.5%', trendPositive: true },
      { label: 'Prêts actifs',   value: (s.nbCollectes || 0).toLocaleString('fr'), icon: 'assignment', iconBg: 'rgba(139,92,246,0.12)', iconColor: '#8B5CF6', footnote: 'Dossiers en cours' },
    ];

    if (this.isDirecteur) return [
      { label: 'PAR30',          value: this.fmtM(s.encoursPar30),   icon: 'warning',             iconBg: 'rgba(239,68,68,0.12)', iconColor: '#EF4444', trend: 'Risque portefeuille', trendPositive: false },
      { label: 'Collectes MTD',  value: this.fmtM(s.totalCollectes), icon: 'payments',            iconBg: 'rgba(16,185,129,0.12)', iconColor: '#10B981', trend: '+8.5%', trendPositive: true },
      { label: 'Encours total',  value: this.fmtM(s.encoursTotal),   icon: 'account_balance',     iconBg: 'rgba(37,99,235,0.12)', iconColor: '#2563EB' },
      { label: 'Alertes',        value: String(s.nbAlertesActives),  icon: 'notifications_active', iconBg: 'rgba(245,158,11,0.12)', iconColor: '#F59E0B', footnote: 'dossiers critiques' },
    ];

    if (this.isResponsable) {
      const par30Rate = s.encoursTotal > 0 ? ((s.encoursPar30 / s.encoursTotal) * 100).toFixed(1) : '0.0';
      return [
        { label: 'PAR30',          value: this.fmtM(s.encoursPar30),  icon: 'warning',             iconBg: 'rgba(239,68,68,0.12)', iconColor: '#EF4444', trend: `${par30Rate}% du portefeuille`, trendPositive: false },
        { label: 'PAR90',          value: this.fmtM(s.encoursPar90),  icon: 'error_outline',        iconBg: 'rgba(239,68,68,0.08)', iconColor: '#F87171', footnote: 'Contentieux probable' },
        { label: 'Alertes actives', value: String(s.nbAlertesActives), icon: 'notifications_active', iconBg: 'rgba(245,158,11,0.12)', iconColor: '#F59E0B', trend: 'À traiter', trendPositive: false },
        { label: 'Encours total',  value: this.fmtM(s.encoursTotal),  icon: 'account_balance',     iconBg: 'rgba(37,99,235,0.12)', iconColor: '#2563EB' },
      ];
    }

    if (this.isAnalyste) return [
      { label: 'Encours total',  value: this.fmtM(s.encoursTotal),   icon: 'account_balance',     iconBg: 'rgba(37,99,235,0.12)',   iconColor: '#2563EB' },
      { label: 'PAR30',          value: this.fmtM(s.encoursPar30),   icon: 'warning',             iconBg: 'rgba(239,68,68,0.12)',   iconColor: '#EF4444' },
      { label: 'Collectes MTD',  value: this.fmtM(s.totalCollectes), icon: 'payments',            iconBg: 'rgba(16,185,129,0.12)', iconColor: '#10B981', trend: '+8.5%', trendPositive: true },
      { label: 'Prêts actifs',   value: (s.nbCollectes || 0).toLocaleString('fr'), icon: 'assignment', iconBg: 'rgba(139,92,246,0.12)', iconColor: '#8B5CF6' },
    ];

    // AGENT
    return [
      { label: 'Mes collectes MTD', value: this.fmtM(s.totalCollectes), icon: 'payments',            iconBg: 'rgba(16,185,129,0.12)', iconColor: '#10B981', trend: '+8.5%', trendPositive: true },
      { label: 'Dossiers actifs',   value: (s.nbCollectes || 0).toLocaleString('fr'), icon: 'assignment', iconBg: 'rgba(37,99,235,0.12)', iconColor: '#2563EB' },
      { label: 'Alertes',           value: String(s.nbAlertesActives), icon: 'notifications_active', iconBg: 'rgba(245,158,11,0.12)', iconColor: '#F59E0B', footnote: 'À traiter' },
    ];
  }

  ngOnInit(): void {
    if (this.auth.isSuperAdmin()) {
      this.router.navigate(['/platform'], { replaceUrl: true });
      return;
    }
    this.loadData();
    this.sseSub = this.sseService.connect().subscribe({
      next: (event) => {
        if (event.type === 'ALERTE_CREATED') {
          this.nouvelleAlerte = true;
          if (this.summary) {
            this.summary = { ...this.summary, nbAlertesActives: this.summary.nbAlertesActives + 1 };
          }
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.sseSub?.unsubscribe();
  }

  loadData(): void {
    this.loading = true;
    this.error = '';

    this.kpiService.getDashboardSummary().subscribe({
      next: (data) => { this.summary = data; this.loading = false; },
      error: () => { this.error = 'Une erreur est survenue'; this.loading = false; }
    });

    this.kpiService.getParStats(this.dateDebut, this.dateFin).subscribe({
      next: (stats) => {
        this.parStats = stats;
        this.prepareStatutPretsData(stats);
        this.prepareEvolutionMensuelleData(stats);
      },
      error: () => {}
    });

    this.kpiService.getCollecteStats(this.dateDebut, this.dateFin).subscribe({
      next: (stats) => {
        this.collecteStats = stats;
        this.prepareAgencesPerformanceData(stats);
      },
      error: () => {}
    });
  }

  private prepareStatutPretsData(stats: ParStat[]): void {
    const statutCount = new Map<string, number>();
    stats.forEach(s => {
      const count = statutCount.get(s.statutPret) || 0;
      statutCount.set(s.statutPret, count + 1);
    });
    this.statutPretsData = Array.from(statutCount.entries()).map(([label, value]) => ({
      label: this.formatStatutLabel(label),
      value,
      color: this.getStatutColor(label)
    }));
  }

  get agencesTotalMCFA(): string {
    return (this.agencesPerformanceData.reduce((sum, d) => sum + d.value, 0)).toFixed(1);
  }

  private prepareAgencesPerformanceData(stats: CollecteStat[]): void {
    const agenceData = new Map<string, number>();
    stats.forEach(s => {
      const total = agenceData.get(s.nomAgence) || 0;
      agenceData.set(s.nomAgence, total + (s.montantTotal / 1_000_000));
    });
    const sorted = Array.from(agenceData.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5);
    this.agencesPerformanceData = sorted.map(([label, value]) => ({
      label,
      value: Math.round(value * 100) / 100
    }));
  }

  private prepareEvolutionMensuelleData(stats: ParStat[]): void {
    const monthlyData = new Map<string, { collectes: number; remboursements: number }>();
    stats.forEach(s => {
      const month = s.dateValeur?.substring(0, 7) || '';
      const existing = monthlyData.get(month) || { collectes: 0, remboursements: 0 };
      existing.collectes += s.montantPret / 1_000_000;
      existing.remboursements += s.montantRembourse / 1_000_000;
      monthlyData.set(month, existing);
    });
    const sortedMonths = Array.from(monthlyData.keys()).sort();
    this.evolutionMensuelleLabels = sortedMonths.map(m => this.formatMonth(m));
    this.evolutionMensuelleDatasets = [
      { label: 'Prêts accordés',  data: sortedMonths.map(m => Math.round(monthlyData.get(m)!.collectes * 100) / 100),      color: '#2563EB' },
      { label: 'Remboursements',  data: sortedMonths.map(m => Math.round(monthlyData.get(m)!.remboursements * 100) / 100), color: '#10B981' },
    ];
  }

  private formatStatutLabel(statut: string): string {
    const labels: Record<string, string> = {
      'EN_COURS': 'En cours', 'SOLDE': 'Soldé', 'IMPAYE': 'Impayé', 'CONTENTIEUX': 'Contentieux'
    };
    return labels[statut] || statut;
  }

  private getStatutColor(statut: string): string {
    const colors: Record<string, string> = {
      'EN_COURS': '#2563EB', 'SOLDE': '#10B981', 'IMPAYE': '#F59E0B', 'CONTENTIEUX': '#EF4444'
    };
    return colors[statut] || '#6B7280';
  }

  private formatMonth(month: string): string {
    const [year, m] = month.split('-');
    const months = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Juin', 'Juil', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'];
    return `${months[parseInt(m) - 1]} ${year}`;
  }

  private fmtM(value: number): string {
    if (value >= 1_000_000) return (value / 1_000_000).toFixed(1) + ' MFCFA';
    if (value >= 1_000)     return (value / 1_000).toFixed(0) + ' kFCFA';
    return value.toFixed(0) + ' FCFA';
  }

  dismissAlerteBadge(): void {
    this.nouvelleAlerte = false;
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('fr-CM', { style: 'decimal', maximumFractionDigits: 0 }).format(value) + ' FCFA';
  }
}

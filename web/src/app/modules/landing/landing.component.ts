import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { fadeInUp, staggerIn, reveal } from '../../shared/animations';

@Component({
  selector: 'imf-landing',
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss'],
  animations: [fadeInUp, staggerIn, reveal]
})
export class LandingComponent implements OnInit, OnDestroy {

  headerScrolled  = false;
  scrollProgress  = 0;    // 0 – 100
  showScrollTop   = false;

  readonly SCROLL_RADIUS = 22;
  get scrollCircumference(): number { return 2 * Math.PI * this.SCROLL_RADIUS; }
  get strokeDashoffset(): number {
    return this.scrollCircumference * (1 - this.scrollProgress / 100);
  }

  readonly trustBadges = [
    { icon: 'lock',                 label: 'Donnees chiffrees' },
    { icon: 'notifications_active', label: 'Alertes temps reel' },
    { icon: 'cloud_done',           label: 'Disponible 24/7' },
    { icon: 'devices',              label: 'Web & mobile' },
  ];

  readonly metrics = [
    { icon: 'corporate_fare',      value: 'Multi-IMF',  label: 'Architecture multi-tenant' },
    { icon: 'warning_amber',       value: 'Instantane', label: 'Detection des impayes' },
    { icon: 'bar_chart',           value: 'KPI live',   label: 'Tableaux de bord en temps reel' },
    { icon: 'sync',                value: 'Automatise', label: 'Pipeline de donnees ETL' },
  ];

  readonly steps = [
    {
      icon: 'login',
      title: 'Connexion sécurisée',
      desc: 'Accédez à la plateforme avec votre identifiant IMF. Chaque utilisateur dispose d\'un rôle et d\'un périmètre défini.',
      color: '#2563EB',
    },
    {
      icon: 'sync_alt',
      title: 'Synchronisation des données',
      desc: 'Vos données de prêts et clients sont automatiquement importées et traitées par notre pipeline ETL.',
      color: '#0D9488',
    },
    {
      icon: 'insights',
      title: 'Pilotage et action',
      desc: 'Suivez vos KPI, gérez les alertes d\'impayés et exportez vos rapports — tout depuis un seul tableau de bord.',
      color: '#C8923A',
    },
  ];

  activeStepIndex: number | null = null;
  stepOverlayVisible = false;
  private stepOverlayTimer?: ReturnType<typeof setTimeout>;

  readonly features = [
    {
      icon: 'dashboard',
      title: 'Tableau de bord KPI',
      desc: 'Visualisez en temps réel le PAR, les collectes du mois et tous vos indicateurs de performance clés.',
      color: '#2563EB',
    },
    {
      icon: 'warning_amber',
      title: 'Alertes d\'impayés',
      desc: 'Recevez des notifications push et e-mail dès qu\'un prêt en retard est détecté dans votre portefeuille.',
      color: '#F59E0B',
    },
    {
      icon: 'people',
      title: 'Gestion clients & prets',
      desc: 'Consultez l\'historique complet de chaque client et suivez l\'état de tous vos prêts en un clic.',
      color: '#0D9488',
    },
    {
      icon: 'bar_chart',
      title: 'Reporting & exports',
      desc: 'Générez des rapports PDF et CSV détaillés — collectes, prêts en retard, rapport KPI — en quelques secondes.',
      color: '#8B5CF6',
    },
    {
      icon: 'smartphone',
      title: 'Application mobile',
      desc: 'Les agents terrain enregistrent leurs collectes hors ligne. La synchronisation se fait automatiquement à la reconnexion.',
      color: '#EC4899',
    },
    {
      icon: 'admin_panel_settings',
      title: 'Gestion des accès',
      desc: 'Créez et gérez les comptes de votre IMF avec des rôles et périmètres précis : DSI, Directeur, Analyste, Agent.',
      color: '#10B981',
    },
  ];

  readonly benefits = [
    {
      icon: 'speed',
      title: 'Temps d\'action reduit',
      desc: 'Priorisez les dossiers critiques grace aux scores de risque et aux relances automatises.',
      color: '#8BD1FF',
    },
    {
      icon: 'shield',
      title: 'Traçabilite complete',
      desc: 'Chaque action est historisee: alertes, interactions, paiements et decisions.',
      color: '#B2F5EA',
    },
    {
      icon: 'auto_graph',
      title: 'KPI en temps reel',
      desc: 'Des indicateurs simples pour suivre le PAR, les retards et la performance globale.',
      color: '#A5B4FC',
    },
    {
      icon: 'groups',
      title: 'Collaboration fluide',
      desc: 'Commentaires, assignations et workflow partages pour accelerer les resolutions.',
      color: '#FDE68A',
    },
  ];

  readonly integrations = [
    { name: 'CBS / Core Banking', desc: 'Import planifie et mapping automatise des portefeuilles.' },
    { name: 'Mobile Collect', desc: 'Synchronisation des collectes terrain en quelques minutes.' },
    { name: 'Messaging', desc: 'Envoi SMS, e-mail et WhatsApp selon vos regles.' },
    { name: 'Export Data', desc: 'CSV, PDF et API pour vos reporting internes.' },
    { name: 'Data Pipeline', desc: 'ETL supervise pour garder la qualite des donnees.' },
    { name: 'SSO', desc: 'Connexion simplifiee pour vos equipes.' },
  ];

  readonly faqs = [
    {
      q: 'En combien de temps la plateforme peut-elle etre operationnelle?',
      a: 'Apres configuration et import initial, la mise en route peut se faire en quelques jours.',
    },
    {
      q: 'Nos donnees sont-elles securisees?',
      a: 'Oui. Chiffrement, journaux d\'audit et segregation logique par IMF sont inclus.',
    },
    {
      q: 'Peut-on garder nos outils existants?',
      a: 'Oui. L\'API permet d\'integrer vos flux actuels sans changer vos processus.',
    },
    {
      q: 'Que se passe-t-il si la connexion internet est instable?',
      a: 'Le mobile peut travailler hors ligne et synchroniser automatiquement au retour reseau.',
    },
  ];

  constructor(private router: Router) {}

  ngOnInit(): void {}
  ngOnDestroy(): void {}

  @HostListener('window:scroll')
  onScroll(): void {
    const scrollY  = window.scrollY;
    const docH     = document.documentElement.scrollHeight - window.innerHeight;
    this.headerScrolled = scrollY > 60;
    this.showScrollTop  = scrollY > 300;
    this.scrollProgress = docH > 0 ? Math.round((scrollY / docH) * 100) : 0;
  }

  goToLogin(): void { this.router.navigate(['/login']); }

  scrollToTop(): void { window.scrollTo({ top: 0, behavior: 'smooth' }); }

  toggleStep(index: number): void {
    this.activeStepIndex = this.activeStepIndex === index ? null : index;
  }

  playStepAnimation(index: number): void {
    this.activeStepIndex = index;
    this.stepOverlayVisible = true;
    if (this.stepOverlayTimer) clearTimeout(this.stepOverlayTimer);
    this.stepOverlayTimer = setTimeout(() => {
      this.stepOverlayVisible = false;
    }, 1400);
  }

  scrollTo(id: string, event?: Event): void {
    event?.preventDefault();
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}

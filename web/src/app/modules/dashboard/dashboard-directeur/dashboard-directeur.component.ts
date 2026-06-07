import { Component, OnInit, OnDestroy } from "@angular/core";
import { Subscription } from "rxjs";
import { KpiService } from "../kpi.service";
import { SseService } from "@core/services/sse.service";
import {
  DashboardDirecteur,
  ParStat,
  CollecteStat,
  TendancePrixProduit,
  BenchmarkAgence,
} from "../models/kpi.model";
import { fadeInUp, staggerIn } from "../../../shared/animations";

@Component({
  selector: "imf-dashboard-directeur",
  templateUrl: "./dashboard-directeur.component.html",
  styleUrls: ["./dashboard-directeur.component.scss"],
  animations: [fadeInUp, staggerIn],
})
export class DashboardDirecteurComponent implements OnInit, OnDestroy {
  summary: DashboardDirecteur | null = null;
  parStats: ParStat[] = [];
  collecteStats: CollecteStat[] = [];
  tendancesPrix: TendancePrixProduit[] = [];
  benchmarks: BenchmarkAgence[] = [];

  loading = true;
  error = "";
  readonly today = new Date();

  // Cards KPI collecte
  get kpiCardsCollecte() {
    if (!this.summary) return [];
    return [
      {
        label: "Collectes du jour",
        value:
          this.summary.montantCollecteJour.toLocaleString("fr-FR") + " FCFA",
        icon: "savings",
        iconBg: "rgba(37,99,235,0.12)",
        iconColor: "#2563EB",
        footnote: `${this.summary.collecteJour} transactions`,
        trend: this.formatTrend(this.summary.variationCollecteSemaine),
        trendPositive: this.summary.variationCollecteSemaine >= 0,
      },
      {
        label: "Réalisation objectif",
        value: `${this.summary.tauxRealisationObjectifPct?.toFixed(1)}%`,
        icon: "track_changes",
        iconBg: this.objectifColor(this.summary.tauxRealisationObjectifPct),
        iconColor: this.objectifIconColor(
          this.summary.tauxRealisationObjectifPct,
        ),
        footnote: "Mois en cours",
      },
      {
        label: "Clients risque CRITIQUE",
        value: this.summary.nbClientsRisqueCritique.toString(),
        icon: "warning",
        iconBg: "rgba(239,68,68,0.12)",
        iconColor: "#DC2626",
        footnote: `${this.summary.nbClientsRisqueEleve} en risque ÉLEVÉ`,
        trend:
          this.summary.nbAlertesMlActives > 0
            ? `${this.summary.nbAlertesMlActives} alertes ML actives`
            : undefined,
        trendPositive: false,
      },
    ];
  }

  // Cards KPI recouvrement
  get kpiCardsRecouvrement() {
    if (!this.summary) return [];
    return [
      {
        label: "PAR 30",
        value: this.summary.encoursPar30.toLocaleString("fr-FR") + " FCFA",
        icon: "account_balance",
        iconBg: "rgba(245,158,11,0.12)",
        iconColor: "#D97706",
        footnote: `${this.summary.tauxPar30Pct?.toFixed(2)}% de l'encours`,
      },
      {
        label: "PAR 90",
        value: this.summary.encoursPar90.toLocaleString("fr-FR") + " FCFA",
        icon: "error_outline",
        iconBg: "rgba(239,68,68,0.12)",
        iconColor: "#DC2626",
        footnote: `${this.summary.tauxPar90Pct?.toFixed(2)}% de l'encours`,
      },
      {
        label: "Taux de recouvrement",
        value: `${this.summary.tauxRecouvrementPct?.toFixed(1)}%`,
        icon: "trending_up",
        iconBg: "rgba(16,185,129,0.12)",
        iconColor: "#059669",
        footnote: `Provisions: ${this.summary.totalProvisions?.toLocaleString("fr-FR")} FCFA`,
      },
      {
        label: "Rang agence",
        value: this.summary.rangAgence
          ? `#${this.summary.rangAgence}/${this.summary.nbAgencesComparees}`
          : "N/A",
        icon: "leaderboard",
        iconBg: "rgba(139,92,246,0.12)",
        iconColor: "#7C3AED",
        footnote: "Benchmark inter-agences",
      },
    ];
  }

  // Données graphique PAR (bar chart)
  get parChartData() {
    const labels = this.parStats.map((s) => s.nomAgence);
    return {
      labels,
      datasets: [
        {
          label: "PAR 30",
          data: this.parStats.map((s) => s.encoursPar30),
          backgroundColor: "rgba(245,158,11,0.7)",
        },
        {
          label: "PAR 90",
          data: this.parStats.map((s) => s.encoursPar90),
          backgroundColor: "rgba(239,68,68,0.7)",
        },
        {
          label: "Encours sain",
          data: this.parStats.map((s) =>
            Math.max(s.encoursTotal - s.encoursPar30, 0),
          ),
          backgroundColor: "rgba(16,185,129,0.7)",
        },
      ],
    };
  }

  // Données graphique collectes (line chart)
  get collecteChartData() {
    const byDate = this.collecteStats.reduce(
      (acc, s) => {
        acc[s.dateValeur] = (acc[s.dateValeur] || 0) + s.montantTotal;
        return acc;
      },
      {} as Record<string, number>,
    );
    const labels = Object.keys(byDate).sort();
    return {
      labels,
      datasets: [
        {
          label: "Montant collectes",
          data: labels.map((d) => byDate[d]),
          borderColor: "#2563EB",
          backgroundColor: "rgba(37,99,235,0.1)",
          fill: true,
          tension: 0.4,
        },
      ],
    };
  }

  private sseSub?: Subscription;

  constructor(
    private kpiService: KpiService,
    private sseService: SseService,
  ) {}

  ngOnInit(): void {
    this.charger();
    this.sseSub = this.sseService.events$.subscribe((evt) => {
      if (
        [
          "kpi_collecte_updated",
          "recouvrement_updated",
          "scoring_updated",
        ].includes(evt.type)
      ) {
        this.charger();
      }
    });
  }

  ngOnDestroy(): void {
    this.sseSub?.unsubscribe();
  }

  charger(): void {
    this.loading = true;
    const d30 = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
      .toISOString()
      .split("T")[0];
    const today = new Date().toISOString().split("T")[0];

    this.kpiService.getDashboardDirecteur().subscribe({
      next: (s) => {
        this.summary = s;
        this.loading = false;
      },
      error: (e) => {
        this.error = e.message;
        this.loading = false;
      },
    });
    this.kpiService
      .getParStats(d30, today)
      .subscribe((s) => (this.parStats = s));
    this.kpiService
      .getCollecteStats(d30, today)
      .subscribe((s) => (this.collecteStats = s));
    this.kpiService.getBenchmarks().subscribe((b) => (this.benchmarks = b));
    this.kpiService
      .getTendancesPrix(undefined, undefined, 90)
      .subscribe((t) => (this.tendancesPrix = t));
  }

  private formatTrend(val: number): string {
    return `${val >= 0 ? "+" : ""}${val?.toFixed(1)}% vs sem. précédente`;
  }

  private objectifColor(taux: number): string {
    if (taux >= 90) return "rgba(16,185,129,0.12)";
    if (taux >= 70) return "rgba(245,158,11,0.12)";
    return "rgba(239,68,68,0.12)";
  }

  private objectifIconColor(taux: number): string {
    if (taux >= 90) return "#059669";
    if (taux >= 70) return "#D97706";
    return "#DC2626";
  }
}

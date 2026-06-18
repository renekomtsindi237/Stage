import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";

interface FoldMetric {
  fold: number;
  auc_roc: number;
  gini: number;
  ks: number;
  f1: number;
  brier: number;
  seuil_optimal: number;
}

interface ClusterProfil {
  cluster: number;
  nom: string;
  n_clients: number;
  regularite_moy: number;
  montant_moy: number;
  taux_remboursement_moy: number;
  retard_max_moy: number;
  revenu_moy: number;
}

interface ScoreClient {
  client_id: string;
  score_crs: number;
  score_rps: number;
  score_csi: number;
  score_mcrs: number;
  classe_risque: "FAIBLE" | "MODERE" | "ELEVE" | "CRITIQUE";
  priorite: number;
  proba_defaut_90j: number;
  label_reel: number;
  top_feature: string;
}

interface Rapport {
  meta: {
    imf: string;
    imf_code: string;
    devise: string;
    modele: string;
    entrainement: string;
    n_clients: number;
    n_defaut: number;
    taux_defaut_pct: number;
    composantes: { CRS_weight: string; RPS_weight: string; CSI_weight: string };
  };
  performances: {
    cross_validation: {
      n_folds: number;
      strategie: string;
      auc_roc_moyen: number;
      gini_moyen: number;
      ks_moyen: number;
      f1_moyen: number;
      brier_moyen: number;
      detail_folds: FoldMetric[];
    };
    modele_final: { auc_roc: number; gini: number };
  };
  feature_importances: {
    shap_top10: Record<string, number>;
  };
  clustering: {
    profils: ClusterProfil[];
    anomalies: { n_detectees: number; clients: string[] };
  };
  scoring_clients: ScoreClient[];
  distribution_risque: Record<string, { n: number; description: string }>;
  interpretation: {
    top_features_explicatives: string[];
    recommandation: string;
  };
}

@Component({
  selector: "app-anl-mcrs-demo",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./anl-mcrs-demo.component.html",
  styleUrls: ["./anl-mcrs-demo.component.scss"],
})
export class AnlMcrsDemoComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly loading = signal(true);
  readonly rapport = signal<Rapport | null>(null);
  readonly activeTab = signal<"overview" | "scoring" | "clustering" | "folds">(
    "overview",
  );

  readonly totalClients = computed(
    () => this.rapport()?.scoring_clients.length ?? 0,
  );
  readonly distRisque = computed(
    () => this.rapport()?.distribution_risque ?? {},
  );

  readonly shapTop10 = computed(() => {
    const fi = this.rapport()?.feature_importances?.shap_top10 ?? {};
    const entries = Object.entries(fi).slice(0, 10);
    const max = Math.max(...entries.map(([, v]) => v));
    return entries.map(([k, v]) => ({
      name: k,
      value: v,
      pct: max > 0 ? (v / max) * 100 : 0,
    }));
  });

  ngOnInit(): void {
    this.http.get<Rapport>("assets/mcrs_fintech_rapport.json").subscribe({
      next: (r) => {
        this.rapport.set(r);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  classeColor(c: string): string {
    const m: Record<string, string> = {
      FAIBLE: "badge--success",
      MODERE: "badge--warning",
      ELEVE: "badge--orange",
      CRITIQUE: "badge--danger",
    };
    return m[c] ?? "badge--neutral";
  }

  distColor(c: string): string {
    const m: Record<string, string> = {
      FAIBLE: "#10b981",
      MODERE: "#f59e0b",
      ELEVE: "#f97316",
      CRITIQUE: "#ef4444",
    };
    return m[c] ?? "#6b7280";
  }

  distPct(n: number): number {
    const total = this.totalClients();
    return total > 0 ? Math.round((n / total) * 100) : 0;
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString("fr-FR", {
      day: "2-digit",
      month: "long",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  formatMontant(n: number): string {
    return new Intl.NumberFormat("fr-CM", {
      style: "currency",
      currency: "XAF",
      maximumFractionDigits: 0,
    }).format(n);
  }

  clusterColor(k: number): string {
    return ["#6366f1", "#10b981", "#f59e0b", "#ef4444"][k] ?? "#6b7280";
  }

  prioriteStars(p: number): string {
    return "★".repeat(p) + "☆".repeat(5 - p);
  }
}

import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ApiService } from "../../../core/http/api.service";

interface ScoreRow {
  id: number;
  client_id: number;
  imf_id: number;
  score_mcrs: number;
  classe_risque: string;
  niveau_risque: string;
  probabilite_defaut: number;
  modele_version: string;
  calculated_at: string;
}

interface ScorePage {
  content: ScoreRow[];
  totalElements: number;
  totalPages: number;
  number: number;
}

interface DashboardData {
  totalClients: number;
  scoresMoyen: number;
  alertesOuvertes: number;
  driftPsi: number;
  scoringDistribution: { label: string; count: number }[];
  alertesRecentes: {
    id: string;
    nomClient: string;
    severite: string;
    message: string;
    encours: number;
    createdAt: string;
  }[];
}

interface DriftInfo {
  psiActuel: number;
  seuilCritique: number;
  driftDetecte: boolean;
  modeleActif: string;
  dernierEntrainement: string;
  evolutionPsi: { date: string; psi: number }[];
  contributionFeatures: { nom: string; psi: number; contribution: number }[];
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
  private readonly api = inject(ApiService);

  loading     = signal(true);
  dashboard   = signal<DashboardData | null>(null);
  drift       = signal<DriftInfo | null>(null);
  scores      = signal<ScorePage | null>(null);
  currentPage = signal(0);
  activeTab   = signal<"scores" | "distribution" | "drift" | "alertes">("scores");

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    Promise.all([
      this.api.get<{ data: DashboardData }>("/api/v1/analyste/dashboard").toPromise(),
      this.api.get<{ data: DriftInfo }>("/api/v1/analyste/ml/drift").toPromise(),
      this.loadScores(0),
    ])
      .then(([dash, dr]) => {
        const d = dash as { data: DashboardData };
        const dv = dr as { data: DriftInfo };
        this.dashboard.set(d?.data ?? (d as unknown as DashboardData));
        this.drift.set(dv?.data ?? (dv as unknown as DriftInfo));
        this.loading.set(false);
      })
      .catch(() => this.loading.set(false));
  }

  async loadScores(page: number) {
    this.currentPage.set(page);
    const r = await this.api
      .get<{ data: ScorePage }>("/api/v1/analyste/scoring", { page, size: 15 })
      .toPromise();
    const p = (r as { data: ScorePage })?.data ?? (r as unknown as ScorePage);
    this.scores.set(p);
  }

  goPage(n: number) { this.loadScores(n); }

  niveauClass(n: string): string {
    return {
      FAIBLE: "badge-green", MODERE: "badge-moyenne",
      ELEVE: "badge-haute",  TRES_ELEVE: "badge-haute",
      CRITIQUE: "badge-critique",
    }[n] ?? "badge-moyenne";
  }

  psiClass(psi: number): string {
    if (psi < 0.1) return "text-green";
    if (psi < 0.2) return "text-warning";
    return "text-danger";
  }

  psiLabel(psi: number): string {
    if (psi < 0.1) return "Stable";
    if (psi < 0.2) return "Légère dérive";
    return "Dérive significative";
  }

  barWidth(count: number): number {
    const dist = this.dashboard()?.scoringDistribution ?? [];
    const max  = Math.max(...dist.map((d) => Number(d.count)), 1);
    return (Number(count) / max) * 100;
  }
}

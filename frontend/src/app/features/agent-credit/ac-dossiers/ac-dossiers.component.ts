import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterModule } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";
import { AppDatePipe } from "../../../shared/pipes/app-date.pipe";
import { EmptyStateComponent } from "../../../shared/components/empty-state/empty-state.component";
import { downloadCsv } from "../../../shared/utils/csv-export";
import { sortRows } from "../../../shared/utils/sort-rows";

interface DossierCredit {
  uid: string;
  numeroReference: string;
  clientNom: string;
  clientPrenom: string;
  montant: number;
  duree: number;
  objectif: string;
  statut:
    | "BROUILLON"
    | "SOUMIS"
    | "EN_ETUDE"
    | "VALIDE_CHEF"
    | "VALIDE_DIRECTEUR"
    | "REFUSE"
    | "DECAISSE";
  createdAt: string;
  scoreCredit?: number;
}

interface DossierPage {
  content: DossierCredit[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-ac-dossiers",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterModule,
    TranslatePipe,
    StatutLabelPipe,
    AppDatePipe,
    EmptyStateComponent,
  ],
  templateUrl: "./ac-dossiers.component.html",
  styleUrls: ["./ac-dossiers.component.scss"],
})
export class AcDossiersComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  page = signal<DossierPage | null>(null);
  currentPage = signal(0);
  activeTab = signal<string>("TOUS");
  sortKey = signal("createdAt");
  sortDir = signal<"asc" | "desc">("desc");

  readonly tabs = [
    "TOUS",
    "BROUILLON",
    "SOUMIS",
    "EN_ETUDE",
    "VALIDE_CHEF",
    "VALIDE_DIRECTEUR",
    "DECAISSE",
    "REFUSE",
  ];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const statut = this.activeTab() === "TOUS" ? undefined : this.activeTab();
    this.api
      .get<DossierPage>("/api/v1/credit/dossiers", {
        page: this.currentPage(),
        size: 20,
        statut,
      })
      .subscribe({
        next: (p: DossierPage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  switchTab(t: string) {
    this.activeTab.set(t);
    this.currentPage.set(0);
    this.load();
  }
  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  toggleSort(key: string) {
    if (this.sortKey() === key) {
      this.sortDir.update((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      this.sortKey.set(key);
      this.sortDir.set("asc");
    }
  }

  sorted(): DossierCredit[] {
    return sortRows(this.page()?.content ?? [], this.sortKey(), this.sortDir());
  }

  exportCsv() {
    downloadCsv(
      "dossiers-credit",
      this.sorted().map((d) => ({
        reference: d.numeroReference,
        client: `${d.clientPrenom} ${d.clientNom}`,
        montant: d.montant,
        duree: d.duree,
        statut: d.statut,
        date: d.createdAt,
      })),
    );
  }

  statutClass(s: string) {
    const map: Record<string, string> = {
      BROUILLON: "badge-secondary",
      SOUMIS: "badge-info",
      EN_ETUDE: "badge-warning",
      VALIDE_CHEF: "badge-primary",
      VALIDE_DIRECTEUR: "badge-primary",
      DECAISSE: "badge-success",
      REFUSE: "badge-danger",
    };
    return map[s] ?? "";
  }

  scoreClass(score?: number) {
    if (!score) return "";
    if (score >= 700) return "score-good";
    if (score >= 500) return "score-medium";
    return "score-bad";
  }
}

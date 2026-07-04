import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";

interface ScoringResult {
  clientId: string;
  nom: string;
  score: number;
  classe: string;
  niveauRisque: string;
  probabiliteDefaut: number;
  actionRecommandee?: string;
}

interface ScoringPage {
  content: ScoringResult[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-dir-scoring",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: "./dir-scoring.component.html",
  styleUrls: ["./dir-scoring.component.scss"],
})
export class DirScoringComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  loading = signal(true);
  page = signal<ScoringPage | null>(null);
  currentPage = signal(0);

  filterForm = this.fb.group({ niveauRisque: [""], search: [""] });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const { niveauRisque, search } = this.filterForm.value;
    this.api
      .get<{ data: ScoringPage }>("/api/v1/analyste/scoring", {
        page: this.currentPage(),
        size: 20,
        niveauRisque: niveauRisque || undefined,
        search: search || undefined,
      })
      .subscribe({
        next: (r: any) => {
          this.page.set(r?.data ?? r);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  niveauLabel(n: string): string {
    const map: Record<string, string> = {
      CRITIQUE: "dir_scoring.filter_critique",
      ELEVE: "dir_scoring.filter_eleve",
      MODERE: "dir_scoring.filter_modere",
      FAIBLE: "dir_scoring.filter_faible",
    };
    return map[n] ?? n;
  }

  niveauClass(n: string): string {
    const map: Record<string, string> = {
      CRITIQUE: "badge-critique",
      ELEVE: "badge-haute",
      MODERE: "badge-moyenne",
      FAIBLE: "badge-basse",
    };
    return map[n] ?? "";
  }
}

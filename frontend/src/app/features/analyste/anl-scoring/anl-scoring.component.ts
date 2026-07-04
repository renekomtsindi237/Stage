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
  classe: "TRES_FAIBLE" | "FAIBLE" | "MOYEN" | "BON" | "TRES_BON";
  probabiliteDefaut: number;
  facteurPrincipal?: string;
}

interface ScoringPage {
  content: ScoringResult[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-anl-scoring",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: "./anl-scoring.component.html",
  styleUrls: ["./anl-scoring.component.scss"],
})
export class AnlScoringComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  loading = signal(true);
  page = signal<ScoringPage | null>(null);
  currentPage = signal(0);

  filterForm = this.fb.group({ classe: [""], search: [""] });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const { classe, search } = this.filterForm.value;
    this.api
      .get<ScoringPage>("/api/v1/analyste/scoring", {
        page: this.currentPage(),
        size: 15,
        classe,
        search,
      })
      .subscribe({
        next: (p: ScoringPage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  classeLabel(c: string): string {
    const map: Record<string, string> = {
      TRES_FAIBLE: "anl_scoring.filter_tres_faible",
      FAIBLE: "anl_scoring.filter_faible",
      MOYEN: "anl_scoring.filter_moyen",
      BON: "anl_scoring.filter_bon",
      TRES_BON: "anl_scoring.filter_tres_bon",
    };
    return map[c] ?? c;
  }

  classeClass(c: string) {
    const map: Record<string, string> = {
      TRES_FAIBLE: "badge-critique",
      FAIBLE: "badge-haute",
      MOYEN: "badge-moyenne",
      BON: "badge-basse",
      TRES_BON: "badge-success",
    };
    return map[c] ?? "";
  }
}

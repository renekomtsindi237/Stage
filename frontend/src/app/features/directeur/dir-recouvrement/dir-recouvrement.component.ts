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
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";

interface DossierRecouvrement {
  uid: string;
  clientNom: string;
  montantDu: number;
  joursRetard: number;
  phase: string;
  clos: boolean;
  createdAt: string;
  updatedAt: string;
}

interface DossierPage {
  content: DossierRecouvrement[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-dir-recouvrement",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, FcfaPipe, TranslatePipe],
  templateUrl: "./dir-recouvrement.component.html",
  styleUrls: ["./dir-recouvrement.component.scss"],
})
export class DirRecouvrementComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  loading = signal(true);
  page = signal<DossierPage | null>(null);
  currentPage = signal(0);

  filterForm = this.fb.group({ phase: [""], clos: ["false"] });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const { phase, clos } = this.filterForm.value;
    const params: Record<string, any> = {
      page: this.currentPage(),
      size: 20,
    };
    if (phase) params["phase"] = phase;
    if (clos !== "") params["clos"] = clos;

    this.api
      .get<{ data: DossierPage }>("/api/v1/recouvrement/dossiers", params)
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

  phaseLabel(p: string): string {
    const map: Record<string, string> = {
      RELANCE_AMIABLE: "dir_recouvrement.filter_relance",
      MEDIATION_AMIABLE: "dir_recouvrement.filter_mediation",
      MISE_EN_DEMEURE: "dir_recouvrement.filter_med_form",
      CONTENTIEUX: "dir_recouvrement.filter_contentieux",
      REECHELONNEMENT: "dir_recouvrement.filter_reechelonnement",
      PERTE: "dir_recouvrement.filter_perte",
    };
    return map[p] ?? p;
  }

  phaseClass(p: string): string {
    const map: Record<string, string> = {
      RELANCE_AMIABLE: "badge-basse",
      MEDIATION_AMIABLE: "badge-moyenne",
      MISE_EN_DEMEURE: "badge-haute",
      CONTENTIEUX: "badge-critique",
      REECHELONNEMENT: "badge-info",
      PERTE: "badge-muted",
    };
    return map[p] ?? "";
  }

  retardClass(j: number): string {
    if (j > 90) return "text-danger";
    if (j > 30) return "text-warning";
    return "";
  }
}

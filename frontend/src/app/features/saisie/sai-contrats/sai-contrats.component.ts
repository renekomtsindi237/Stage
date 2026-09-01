import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { TranslatePipe } from "@ngx-translate/core";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";
import { AppDatePipe } from "../../../shared/pipes/app-date.pipe";
import { EmptyStateComponent } from "../../../shared/components/empty-state/empty-state.component";
import { EscCloseDirective } from "../../../shared/directives/esc-close.directive";
import { downloadCsv } from "../../../shared/utils/csv-export";
import { sortRows } from "../../../shared/utils/sort-rows";

interface Contrat {
  uid: string;
  numeroContrat: string;
  clientNom: string;
  clientPrenom: string;
  typeContrat: "CREDIT" | "EPARGNE" | "ASSURANCE";
  montant: number;
  dateDebut: string;
  dateFin: string;
  statut: "BROUILLON" | "SOUMIS" | "VALIDE" | "REJETE" | "ACTIF" | "CLOS";
  observations?: string;
}

interface ContratPage {
  content: Contrat[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-sai-contrats",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslatePipe,
    StatutLabelPipe,
    AppDatePipe,
    EmptyStateComponent,
    EscCloseDirective,
  ],
  templateUrl: "./sai-contrats.component.html",
  styleUrls: ["./sai-contrats.component.scss"],
})
export class SaiContratsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  page = signal<ContratPage | null>(null);
  currentPage = signal(0);
  showModal = signal(false);
  submitting = signal(false);
  activeTab = signal<string>("BROUILLON");
  sortKey = signal("dateDebut");
  sortDir = signal<"asc" | "desc">("desc");

  createForm = this.fb.group({
    clientId: ["", Validators.required],
    typeContrat: ["CREDIT", Validators.required],
    montant: [0, [Validators.required, Validators.min(1)]],
    dateDebut: ["", Validators.required],
    dateFin: ["", Validators.required],
    observations: [""],
  });

  readonly tabs = ["BROUILLON", "SOUMIS", "VALIDE", "ACTIF", "REJETE", "TOUS"];
  readonly types = ["CREDIT", "EPARGNE", "ASSURANCE"];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const statut = this.activeTab() === "TOUS" ? undefined : this.activeTab();
    this.api
      .get<ContratPage>("/api/v1/contrats", {
        page: this.currentPage(),
        size: 20,
        statut,
      })
      .subscribe({
        next: (p: ContratPage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  submit() {
    if (this.createForm.invalid) return;
    this.submitting.set(true);
    this.api.post("/api/v1/contrats", this.createForm.value).subscribe({
      next: () => {
        this.submitting.set(false);
        this.showModal.set(false);
        this.toast.showI18nSuccess(
          "sai_contrats.toast_create_title",
          "sai_contrats.toast_create_body",
        );
        this.createForm.reset({ typeContrat: "CREDIT", montant: 0 });
        this.load();
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.toast.showApiError(err, "sai_contrats.toast_create_error");
      },
    });
  }

  soumettre(uid: string) {
    this.api.patch(`/api/v1/contrats/${uid}/soumettre`, {}).subscribe({
      next: () => {
        this.toast.showI18nSuccess(
          "sai_contrats.toast_submit_title",
          "sai_contrats.toast_submit_body",
        );
        this.load();
      },
      error: (err: unknown) =>
        this.toast.showApiError(err, "sai_contrats.toast_submit_error"),
    });
  }

  switchTab(tab: string) {
    this.activeTab.set(tab);
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

  sorted(): Contrat[] {
    return sortRows(this.page()?.content ?? [], this.sortKey(), this.sortDir());
  }

  exportCsv() {
    downloadCsv(
      "contrats",
      this.sorted().map((c) => ({
        numero: c.numeroContrat,
        client: `${c.clientPrenom} ${c.clientNom}`,
        type: c.typeContrat,
        montant: c.montant,
        statut: c.statut,
      })),
    );
  }

  statutClass(s: string) {
    return (
      {
        BROUILLON: "badge-secondary",
        SOUMIS: "badge-info",
        VALIDE: "badge-primary",
        ACTIF: "badge-success",
        REJETE: "badge-danger",
        CLOS: "badge-warning",
      }[s] ?? ""
    );
  }
}

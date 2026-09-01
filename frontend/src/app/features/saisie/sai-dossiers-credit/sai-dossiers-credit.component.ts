import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { TranslatePipe } from "@ngx-translate/core";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";
import { AppDatePipe } from "../../../shared/pipes/app-date.pipe";
import { EmptyStateComponent } from "../../../shared/components/empty-state/empty-state.component";
import { EscCloseDirective } from "../../../shared/directives/esc-close.directive";

interface DossierRow {
  uid: string;
  clientId: string;
  clientNom: string;
  montantDemande: number;
  dureeMois: number;
  objetFinancement: string;
  statut: string;
  dateSoumission: string | null;
  dateDecision: string | null;
}

interface ContratCredit {
  uid: string;
  dossierId: number;
  referenceContrat: string;
  dateSignature: string | null;
  montantFinal: number;
  tauxInteret: number;
  fraisDossier: number | null;
  nbEcheances: number;
  periodicite: string;
  signaturesConformes: boolean;
  dateGeneration: string;
  urlContratPdf: string | null;
  statut: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-sai-dossiers-credit",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    TranslatePipe,
    StatutLabelPipe,
    AppDatePipe,
    EmptyStateComponent,
    EscCloseDirective,
  ],
  templateUrl: "./sai-dossiers-credit.component.html",
  styleUrls: ["./sai-dossiers-credit.component.scss"],
})
export class SaiDossiersCreditComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  page = signal<Page<DossierRow> | null>(null);
  currentPage = signal(0);
  filterStatut = signal("APPROUVE");

  selectedDossier = signal<DossierRow | null>(null);
  contratLoading = signal(false);
  contrat = signal<ContratCredit | null>(null);
  contratAbsent = signal(false);

  showGenererForm = signal(false);
  genererForm = {
    montantFinal: 0,
    tauxInteret: 0,
    fraisDossier: null as number | null,
    nbEcheances: 12,
    periodicite: "MENSUEL",
    dateSignature: "",
  };
  generating = signal(false);
  validating = signal(false);

  readonly statuts = ["APPROUVE", "SOUMIS", "EN_INSTRUCTION", "REJETE", "TOUS"];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const statut =
      this.filterStatut() === "TOUS" ? undefined : this.filterStatut();
    this.api
      .get<Page<DossierRow>>("/api/v1/dossiers-credit", {
        page: this.currentPage(),
        size: 20,
        statut,
      })
      .subscribe({
        next: (p) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  openDetail(d: DossierRow) {
    this.selectedDossier.set(d);
    this.contrat.set(null);
    this.contratAbsent.set(false);
    this.showGenererForm.set(false);
    if (d.statut === "APPROUVE") {
      this.loadContrat(d.uid);
    }
  }

  closeDetail() {
    this.selectedDossier.set(null);
  }

  loadContrat(uid: string) {
    this.contratLoading.set(true);
    this.api
      .get<ContratCredit>(`/api/v1/back-office/contrats/dossier/${uid}`)
      .subscribe({
        next: (c) => {
          this.contrat.set(c);
          this.contratLoading.set(false);
        },
        error: () => {
          this.contratAbsent.set(true);
          this.contratLoading.set(false);
        },
      });
  }

  submitGenerer() {
    const d = this.selectedDossier();
    if (
      !d ||
      !this.genererForm.montantFinal ||
      !this.genererForm.tauxInteret ||
      !this.genererForm.nbEcheances
    )
      return;
    this.generating.set(true);
    const body = {
      montantFinal: this.genererForm.montantFinal,
      tauxInteret: this.genererForm.tauxInteret,
      fraisDossier: this.genererForm.fraisDossier ?? null,
      nbEcheances: this.genererForm.nbEcheances,
      periodicite: this.genererForm.periodicite,
      dateSignature: this.genererForm.dateSignature || null,
    };
    this.api
      .post<ContratCredit>(
        `/api/v1/back-office/contrats/dossier/${d.uid}/generer`,
        body,
      )
      .subscribe({
        next: (c) => {
          this.contrat.set(c);
          this.contratAbsent.set(false);
          this.showGenererForm.set(false);
          this.generating.set(false);
          this.toast.showI18nSuccess("sai_dossiers.toast_gen_title", "sai_dossiers.toast_gen_body", {
            ref: c.referenceContrat,
          });
        },
        error: (err: unknown) => {
          this.generating.set(false);
          this.toast.showApiError(err, "sai_dossiers.toast_gen_error");
        },
      });
  }

  submitValiderSignatures() {
    const c = this.contrat();
    if (!c) return;
    this.validating.set(true);
    this.api
      .patch(`/api/v1/back-office/contrats/${c.uid}/valider-signatures`, {})
      .subscribe({
        next: () => {
          this.validating.set(false);
          this.toast.showI18nSuccess(
            "sai_dossiers.toast_sign_title",
            "sai_dossiers.toast_sign_body",
          );
          this.loadContrat(this.selectedDossier()!.uid);
        },
        error: (err: unknown) => {
          this.validating.set(false);
          this.toast.showApiError(err, "sai_dossiers.toast_sign_error");
        },
      });
  }

  changeFilter(s: string) {
    this.filterStatut.set(s);
    this.currentPage.set(0);
    this.selectedDossier.set(null);
    this.load();
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  statutClass(s: string): string {
    return (
      {
        APPROUVE: "badge-success",
        SOUMIS: "badge-warning",
        EN_INSTRUCTION: "badge-info",
        REJETE: "badge-danger",
      }[s] ?? "badge-secondary"
    );
  }

  get pages(): number[] {
    const total = this.page()?.totalPages ?? 0;
    return Array.from({ length: total }, (_, i) => i);
  }
}

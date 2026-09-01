import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
  HostListener,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink, ActivatedRoute, Router } from "@angular/router";
import { TranslatePipe, TranslateService } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { apiErrorMessage } from "../../../core/http/api-error";
import { ToastService } from "../../../core/services/toast.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";
import { AppDatePipe } from "../../../shared/pipes/app-date.pipe";
import { EmptyStateComponent } from "../../../shared/components/empty-state/empty-state.component";
import { EscCloseDirective } from "../../../shared/directives/esc-close.directive";
import { downloadCsv } from "../../../shared/utils/csv-export";

interface DossierRow {
  uid: string;
  idPret: string;
  nomClient: string;
  montantImpaye: number;
  joursRetard: number;
  categorieCobtac: string;
  tauxProvision: number;
  montantProvision: number;
  datePremiereEcheanceImpayee: string | null;
  nomCaution: string | null;
  telephoneCaution: string | null;
  typeGarantie: string | null;
  fraisRecouvrement: number;
  phase: string;
  dateOuverture: string;
  dateDerniereAction: string | null;
  agentResponsableUsername: string | null;
  clos: boolean;
  dateCloture: string | null;
  motifCloture: string | null;
}

interface ActionRow {
  uid: string;
  typeAction: string;
  dateAction: string;
  agentUsername: string;
  resultat: string | null;
  observation: string | null;
  promesseMontant: number | null;
  fraisEngages: number | null;
  canalPaiement: string | null;
  referenceTransaction: string | null;
}

interface AccordRow {
  uid: string;
  nouveauMontantMensuel: number;
  nombreNouvellesEcheances: number;
  dateDebutNouvelEcheancier: string;
  tauxInteretAnnuel: number | null;
  approuveParUsername: string | null;
  dateSignature: string | null;
  observations: string | null;
  actif: boolean;
  createdAt: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

const PHASES = [
  "RELANCE_AMIABLE",
  "MEDIATION_AMIABLE",
  "MISE_EN_DEMEURE",
  "CONTENTIEUX",
  "REECHELONNEMENT",
  "PERTE",
];

const PHASE_LABELS: Record<string, string> = {
  RELANCE_AMIABLE: "common.phase_relance_amiable",
  MEDIATION_AMIABLE: "common.phase_mediation_amiable",
  MISE_EN_DEMEURE: "common.phase_mise_en_demeure",
  CONTENTIEUX: "common.phase_contentieux",
  REECHELONNEMENT: "common.phase_reechelonnement",
  PERTE: "common.phase_perte",
};

const GARANTIE_LABELS: Record<string, string> = {
  NANTISSEMENT: "rec_creances.garantie_nantissement",
  HYPOTHEQUE: "rec_creances.garantie_hypotheque",
  CAUTION_SOLIDAIRE: "rec_creances.garantie_caution_solidaire",
  AUTRE: "rec_creances.garantie_autre",
};

@Component({
  selector: "app-rec-creances",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    FcfaPipe,
    TranslatePipe,
    StatutLabelPipe,
    AppDatePipe,
    EmptyStateComponent,
    EscCloseDirective,
  ],
  templateUrl: "./rec-creances.component.html",
  styleUrls: ["./rec-creances.component.scss"],
})
export class RecCreancesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  readonly phases = PHASES;
  readonly phaseLabels = PHASE_LABELS;

  garantieLabelKey(type: string | null | undefined): string {
    if (!type) return "";
    return GARANTIE_LABELS[type] ?? type;
  }

  // ── List state ─────────────────────────────────────────────────────────────
  loading = signal(true);
  page = signal<Page<DossierRow> | null>(null);
  currentPage = signal(0);
  filterPhase = signal("");
  filterClos = signal(false);
  searchQuery = signal("");

  // ── Detail panel ────────────────────────────────────────────────────────────
  selectedDossier = signal<DossierRow | null>(null);
  detailTab = signal<"actions" | "accords">("actions");

  actionsLoading = signal(false);
  actions = signal<ActionRow[]>([]);

  accordsLoading = signal(false);
  accords = signal<AccordRow[]>([]);

  // ── Nouveau dossier ─────────────────────────────────────────────────────────
  showCreate = signal(false);
  saving = signal(false);
  form = {
    idPret: "",
    nomClient: "",
    montantImpaye: "",
    joursRetard: "",
    datePremiereEcheanceImpayee: "",
    nomCaution: "",
    telephoneCaution: "",
    typeGarantie: "",
  };

  // ── Escalade ────────────────────────────────────────────────────────────────
  showEscalade = signal(false);
  escaladePhase = signal("");
  escaladeMotif = signal("");
  escalading = signal(false);

  // ── Clôture ─────────────────────────────────────────────────────────────────
  showCloture = signal(false);
  clotureMotif = signal("");
  closing = signal(false);

  // ── Accord de rééchelonnement ───────────────────────────────────────────────
  showAccordForm = signal(false);
  accordSaving = signal(false);
  accordForm = {
    nouveauMontantMensuel: "",
    nombreNouvellesEcheances: "",
    dateDebutNouvelEcheancier: "",
    tauxInteretAnnuel: "",
    observations: "",
  };

  ngOnInit() {
    const qp = this.route.snapshot.queryParamMap;
    this.filterPhase.set(qp.get("phase") ?? "");
    this.searchQuery.set(qp.get("q") ?? "");
    this.filterClos.set(qp.get("clos") === "true");
    this.load();
  }

  @HostListener("document:keydown.escape")
  onEsc() {
    if (this.showCreate()) this.closeCreate();
    else if (this.selectedDossier()) this.closeDetail();
  }

  load() {
    this.loading.set(true);
    const params: Record<string, string | number | boolean | undefined> = {
      page: this.currentPage(),
      size: 20,
      clos: this.filterClos(),
    };
    if (this.filterPhase()) params["phase"] = this.filterPhase();
    const q = this.searchQuery().trim();
    if (q) params["q"] = q;

    this.api
      .get<Page<DossierRow>>("/api/v1/recouvrement/dossiers", params)
      .subscribe({
        next: (p) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.toast.showError(
            this.translate.instant("common.error"),
            apiErrorMessage(err),
          );
          this.loading.set(false);
        },
      });
  }

  get filteredContent(): DossierRow[] {
    return this.page()?.content ?? [];
  }

  onSearchChange(q: string) {
    this.searchQuery.set(q);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.applyFilter(), 350);
  }

  applyFilter() {
    this.currentPage.set(0);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        phase: this.filterPhase() || null,
        q: this.searchQuery().trim() || null,
        clos: this.filterClos() ? true : null,
      },
      queryParamsHandling: "merge",
    });
    this.load();
  }

  exportCsv() {
    const rows = this.filteredContent.map((d) => ({
      client: d.nomClient,
      pret: d.idPret,
      montant: d.montantImpaye,
      retard: d.joursRetard,
      cobac: d.categorieCobtac,
      phase: d.phase,
      derniereAction: d.dateDerniereAction,
      clos: d.clos,
    }));
    downloadCsv("creances", rows);
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  // ── Detail panel ────────────────────────────────────────────────────────────

  openDetail(d: DossierRow) {
    this.selectedDossier.set(d);
    this.showEscalade.set(false);
    this.showCloture.set(false);
    this.showAccordForm.set(false);
    this.detailTab.set("actions");
    this.loadActions(d.uid);
  }

  closeDetail() {
    this.selectedDossier.set(null);
    this.actions.set([]);
    this.accords.set([]);
  }

  switchDetailTab(tab: "actions" | "accords") {
    this.detailTab.set(tab);
    const d = this.selectedDossier();
    if (!d) return;
    if (tab === "actions" && this.actions().length === 0)
      this.loadActions(d.uid);
    if (tab === "accords") this.loadAccords(d.uid);
  }

  loadActions(uid: string) {
    this.actionsLoading.set(true);
    this.api
      .get<ActionRow[]>(`/api/v1/recouvrement/dossiers/${uid}/actions`)
      .subscribe({
        next: (a) => {
          this.actions.set(a);
          this.actionsLoading.set(false);
        },
        error: () => this.actionsLoading.set(false),
      });
  }

  loadAccords(uid: string) {
    this.accordsLoading.set(true);
    this.api
      .get<AccordRow[]>(`/api/v1/recouvrement/dossiers/${uid}/accords`)
      .subscribe({
        next: (a) => {
          this.accords.set(a);
          this.accordsLoading.set(false);
        },
        error: () => this.accordsLoading.set(false),
      });
  }

  // ── Nouveau dossier ─────────────────────────────────────────────────────────

  openCreate() {
    this.showCreate.set(true);
  }

  closeCreate() {
    this.showCreate.set(false);
    this.resetForm();
  }

  resetForm() {
    this.form = {
      idPret: "",
      nomClient: "",
      montantImpaye: "",
      joursRetard: "",
      datePremiereEcheanceImpayee: "",
      nomCaution: "",
      telephoneCaution: "",
      typeGarantie: "",
    };
  }

  submitCreate() {
    if (
      !this.form.idPret ||
      !this.form.montantImpaye ||
      !this.form.joursRetard
    ) {
      this.toast.showError(
        this.translate.instant("rec_creances.toast_required_title"),
        this.translate.instant("rec_creances.toast_required_body"),
      );
      return;
    }
    this.saving.set(true);
    const body: Record<string, unknown> = {
      idPret: this.form.idPret.trim(),
      montantImpaye: parseFloat(this.form.montantImpaye),
      joursRetard: parseInt(this.form.joursRetard, 10),
    };
    if (this.form.nomClient.trim())
      body["nomClient"] = this.form.nomClient.trim();
    if (this.form.datePremiereEcheanceImpayee)
      body["datePremiereEcheanceImpayee"] =
        this.form.datePremiereEcheanceImpayee;
    if (this.form.nomCaution.trim())
      body["nomCaution"] = this.form.nomCaution.trim();
    if (this.form.telephoneCaution.trim())
      body["telephoneCaution"] = this.form.telephoneCaution.trim();
    if (this.form.typeGarantie) body["typeGarantie"] = this.form.typeGarantie;

    this.api.post<DossierRow>("/api/v1/recouvrement/dossiers", body).subscribe({
      next: () => {
        this.toast.showSuccess(
          this.translate.instant("rec_creances.toast_create_title"),
          this.translate.instant("rec_creances.toast_create_body"),
        );
        this.saving.set(false);
        this.closeCreate();
        this.load();
      },
      error: (err: unknown) => {
        this.toast.showError(
          this.translate.instant("common.error"),
          apiErrorMessage(
            err,
            this.translate.instant("rec_creances.toast_create_error"),
          ),
        );
        this.saving.set(false);
      },
    });
  }

  // ── Escalade ────────────────────────────────────────────────────────────────

  openEscalade() {
    this.showEscalade.set(true);
    this.showCloture.set(false);
    this.showAccordForm.set(false);
    this.escaladePhase.set("");
    this.escaladeMotif.set("");
  }

  submitEscalade() {
    const d = this.selectedDossier();
    if (!d || !this.escaladePhase()) {
      this.toast.showError(
        this.translate.instant("common.error"),
        this.translate.instant("rec_creances.toast_escalade_required"),
      );
      return;
    }
    this.escalading.set(true);
    this.api
      .put<DossierRow>(`/api/v1/recouvrement/dossiers/${d.uid}/escalader`, {
        nouvellePhase: this.escaladePhase(),
        motif: this.escaladeMotif() || undefined,
      })
      .subscribe({
        next: () => {
          this.toast.showSuccess(
            this.translate.instant("rec_creances.toast_escalade_title"),
            this.translate.instant(
              this.phaseLabels[this.escaladePhase()] ?? this.escaladePhase(),
            ),
          );
          this.escalading.set(false);
          this.showEscalade.set(false);
          this.closeDetail();
          this.load();
        },
        error: (err: unknown) => {
          this.toast.showError(
            this.translate.instant("common.error"),
            apiErrorMessage(
              err,
              this.translate.instant("rec_creances.toast_escalade_error"),
            ),
          );
          this.escalading.set(false);
        },
      });
  }

  // ── Clôture ─────────────────────────────────────────────────────────────────

  openCloture() {
    this.showCloture.set(true);
    this.showEscalade.set(false);
    this.showAccordForm.set(false);
    this.clotureMotif.set("");
  }

  submitCloture() {
    const d = this.selectedDossier();
    if (!d) return;
    this.closing.set(true);
    const motif = encodeURIComponent(this.clotureMotif());
    this.api
      .put<DossierRow>(
        `/api/v1/recouvrement/dossiers/${d.uid}/clore?motif=${motif}`,
      )
      .subscribe({
        next: () => {
          this.toast.showSuccess(
            this.translate.instant("rec_creances.toast_cloture_title"),
            this.translate.instant("rec_creances.toast_cloture_body"),
          );
          this.closing.set(false);
          this.showCloture.set(false);
          this.closeDetail();
          this.load();
        },
        error: (err: unknown) => {
          this.toast.showError(
            this.translate.instant("common.error"),
            apiErrorMessage(
              err,
              this.translate.instant("rec_creances.toast_cloture_error"),
            ),
          );
          this.closing.set(false);
        },
      });
  }

  // ── Accord de rééchelonnement ───────────────────────────────────────────────

  openAccordForm() {
    this.showAccordForm.set(true);
    this.showEscalade.set(false);
    this.showCloture.set(false);
    this.accordForm = {
      nouveauMontantMensuel: "",
      nombreNouvellesEcheances: "",
      dateDebutNouvelEcheancier: "",
      tauxInteretAnnuel: "",
      observations: "",
    };
  }

  submitAccord() {
    const d = this.selectedDossier();
    if (!d) return;
    if (
      !this.accordForm.nouveauMontantMensuel ||
      !this.accordForm.nombreNouvellesEcheances ||
      !this.accordForm.dateDebutNouvelEcheancier
    ) {
      this.toast.showError(
        this.translate.instant("rec_creances.toast_accord_required_title"),
        this.translate.instant("rec_creances.toast_accord_required_body"),
      );
      return;
    }
    this.accordSaving.set(true);
    const body: Record<string, unknown> = {
      nouveauMontantMensuel: parseFloat(this.accordForm.nouveauMontantMensuel),
      nombreNouvellesEcheances: parseInt(
        this.accordForm.nombreNouvellesEcheances,
        10,
      ),
      dateDebutNouvelEcheancier: this.accordForm.dateDebutNouvelEcheancier,
    };
    if (this.accordForm.tauxInteretAnnuel)
      body["tauxInteretAnnuel"] = parseFloat(this.accordForm.tauxInteretAnnuel);
    if (this.accordForm.observations.trim())
      body["observations"] = this.accordForm.observations.trim();

    this.api
      .post<AccordRow>(`/api/v1/recouvrement/dossiers/${d.uid}/accords`, body)
      .subscribe({
        next: () => {
          this.toast.showSuccess(
            this.translate.instant("rec_creances.toast_accord_title"),
            this.translate.instant("rec_creances.toast_accord_body"),
          );
          this.accordSaving.set(false);
          this.showAccordForm.set(false);
          this.loadAccords(d.uid);
        },
        error: (err: unknown) => {
          this.toast.showError(
            this.translate.instant("common.error"),
            apiErrorMessage(
              err,
              this.translate.instant("rec_creances.toast_accord_error"),
            ),
          );
          this.accordSaving.set(false);
        },
      });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  phaseClass(phase: string): string {
    const m: Record<string, string> = {
      RELANCE_AMIABLE: "badge-basse",
      MEDIATION_AMIABLE: "badge-moyenne",
      MISE_EN_DEMEURE: "badge-haute",
      CONTENTIEUX: "badge-critique",
      REECHELONNEMENT: "badge-primary",
      PERTE: "badge-dark",
    };
    return m[phase] ?? "badge-moyenne";
  }

  cobacClass(cat: string): string {
    const m: Record<string, string> = {
      A: "badge-basse",
      B: "badge-moyenne",
      C: "badge-haute",
      D: "badge-critique",
      E: "badge-dark",
    };
    return m[cat] ?? "";
  }

  nextPhases(current: string): string[] {
    const idx = PHASES.indexOf(current);
    if (idx < 0) return PHASES;
    return PHASES.slice(idx + 1);
  }

  actionIcon(type: string): string {
    const m: Record<string, string> = {
      APPEL_TELEPHONIQUE: "call",
      SMS_RELANCE: "sms",
      EMAIL_RELANCE: "email",
      VISITE_TERRAIN: "directions_walk",
      MEDIATION_CHEF_QUARTIER: "groups",
      MEDIATION_FAMILLE: "family_restroom",
      CONTACT_CAUTION: "person_search",
      SAISIE_GARANTIE: "gavel",
      MISE_EN_DEMEURE_LETTRE: "mail",
      INTERVENTION_HUISSIER: "account_balance",
      COMITE_RECOUVREMENT: "meeting_room",
      ASSIGNATION_TRIBUNAL: "balance",
      ENCAISSEMENT_PARTIEL: "payments",
      ENCAISSEMENT_TOTAL: "check_circle",
      ACCORD_REECHELONNEMENT: "handshake",
      CESSION_CREANCE: "swap_horiz",
      RADIATION: "remove_circle",
    };
    return m[type] ?? "task_alt";
  }
}

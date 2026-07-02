import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";

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
  RELANCE_AMIABLE: "Relance amiable",
  MEDIATION_AMIABLE: "Médiation amiable",
  MISE_EN_DEMEURE: "Mise en demeure",
  CONTENTIEUX: "Contentieux",
  REECHELONNEMENT: "Rééchelonnement",
  PERTE: "Perte",
};

const GARANTIE_LABELS: Record<string, string> = {
  NANTISSEMENT: "Nantissement",
  HYPOTHEQUE: "Hypothèque",
  CAUTION_SOLIDAIRE: "Caution solidaire",
  AUTRE: "Autre",
};

@Component({
  selector: "app-rec-creances",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, FcfaPipe, TranslatePipe],
  templateUrl: "./rec-creances.component.html",
  styleUrls: ["./rec-creances.component.scss"],
})
export class RecCreancesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly phases = PHASES;
  readonly phaseLabels = PHASE_LABELS;
  readonly garantieLabels = GARANTIE_LABELS;

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
    this.load();
  }

  load() {
    this.loading.set(true);
    const params: Record<string, string | number | boolean | undefined> = {
      page: this.currentPage(),
      size: 20,
      clos: this.filterClos(),
    };
    if (this.filterPhase()) params["phase"] = this.filterPhase();

    this.api
      .get<Page<DossierRow>>("/api/v1/recouvrement/dossiers", params)
      .subscribe({
        next: (p) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  get filteredContent(): DossierRow[] {
    const q = this.searchQuery().trim().toLowerCase();
    const content = this.page()?.content ?? [];
    if (!q) return content;
    return content.filter(
      (d) =>
        d.nomClient?.toLowerCase().includes(q) ||
        d.idPret?.toLowerCase().includes(q),
    );
  }

  applyFilter() {
    this.currentPage.set(0);
    this.load();
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
        "Champs requis",
        "ID prêt, montant et jours de retard sont obligatoires.",
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
          "Dossier ouvert",
          "Le dossier de recouvrement a été créé.",
        );
        this.saving.set(false);
        this.closeCreate();
        this.load();
      },
      error: () => {
        this.toast.showError("Erreur", "Impossible de créer le dossier.");
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
      this.toast.showError("Requis", "Choisir la nouvelle phase.");
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
            "Phase mise à jour",
            `Dossier passé en ${this.phaseLabels[this.escaladePhase()] ?? this.escaladePhase()}.`,
          );
          this.escalading.set(false);
          this.showEscalade.set(false);
          this.closeDetail();
          this.load();
        },
        error: () => {
          this.toast.showError("Erreur", "Escalade impossible.");
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
            "Dossier clôturé",
            "Le dossier a été clôturé.",
          );
          this.closing.set(false);
          this.showCloture.set(false);
          this.closeDetail();
          this.load();
        },
        error: () => {
          this.toast.showError("Erreur", "Clôture impossible.");
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
        "Champs requis",
        "Montant mensuel, nombre d'échéances et date de début sont obligatoires.",
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
            "Accord enregistré",
            "L'accord de rééchelonnement a été créé.",
          );
          this.accordSaving.set(false);
          this.showAccordForm.set(false);
          this.loadAccords(d.uid);
        },
        error: () => {
          this.toast.showError("Erreur", "Impossible de créer l'accord.");
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

import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";
import { AppDatePipe } from "../../../shared/pipes/app-date.pipe";

interface DossierSummary {
  uid: string;
  idPret: string;
  nomClient: string;
  montantImpaye: number;
  joursRetard: number;
  phase: string;
  clos: boolean;
}

interface ActionRow {
  uid: string;
  typeAction: string;
  dateAction: string;
  agentUsername?: string;
  resultat: string | null;
  observation: string | null;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

const ACTION_TYPES = [
  "APPEL_TELEPHONIQUE",
  "SMS_RELANCE",
  "EMAIL_RELANCE",
  "VISITE_TERRAIN",
  "MEDIATION_CHEF_QUARTIER",
  "MEDIATION_FAMILLE",
  "CONTACT_CAUTION",
  "SAISIE_GARANTIE",
  "MISE_EN_DEMEURE_LETTRE",
  "INTERVENTION_HUISSIER",
  "COMITE_RECOUVREMENT",
  "ASSIGNATION_TRIBUNAL",
  "ENCAISSEMENT_PARTIEL",
  "ENCAISSEMENT_TOTAL",
  "ACCORD_REECHELONNEMENT",
  "CESSION_CREANCE",
  "RADIATION",
];

const CANAUX_PAIEMENT = ["ESPECES", "MTN", "ORANGE", "VIREMENT"];

const PREFILL_BY_PHASE: Record<
  string,
  { typeAction: string; resultat: string }
> = {
  RELANCE_AMIABLE: {
    typeAction: "APPEL_TELEPHONIQUE",
    resultat: "CONTACT_ETABLI",
  },
  MEDIATION_AMIABLE: {
    typeAction: "MEDIATION_CHEF_QUARTIER",
    resultat: "EN_ATTENTE",
  },
  MISE_EN_DEMEURE: {
    typeAction: "MISE_EN_DEMEURE_LETTRE",
    resultat: "EN_ATTENTE",
  },
  CONTENTIEUX: {
    typeAction: "ASSIGNATION_TRIBUNAL",
    resultat: "EN_ATTENTE",
  },
  REECHELONNEMENT: {
    typeAction: "ACCORD_REECHELONNEMENT",
    resultat: "ACCORD_OBTENU",
  },
};

@Component({
  selector: "app-rec-actions",
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
  ],
  templateUrl: "./rec-actions.component.html",
  styleUrls: ["./rec-actions.component.scss"],
})
export class RecActionsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  readonly actionTypes = ACTION_TYPES;
  readonly canauxPaiement = CANAUX_PAIEMENT;

  dossierLoading = signal(false);
  selectedDossier = signal<DossierSummary | null>(null);
  searchQuery = signal("");
  searchResults = signal<DossierSummary[]>([]);
  showResults = signal(false);
  timeline = signal<ActionRow[]>([]);
  timelineLoading = signal(false);
  nextDossierUid = signal<string | null>(null);

  form = {
    typeAction: "",
    resultat: "",
    promesseDate: "",
    promesseMontant: "",
    canalPaiement: "",
    referenceTransaction: "",
    numeroTelephonePaiement: "",
    fraisEngages: "",
    observation: "",
  };

  saving = signal(false);
  success = signal(false);

  get isEncaissement(): boolean {
    return (
      this.form.typeAction === "ENCAISSEMENT_PARTIEL" ||
      this.form.typeAction === "ENCAISSEMENT_TOTAL"
    );
  }

  get isMomo(): boolean {
    return (
      this.form.canalPaiement === "MTN" || this.form.canalPaiement === "ORANGE"
    );
  }

  ngOnInit() {
    const dossierUid = this.route.snapshot.queryParamMap.get("dossierUid");
    if (dossierUid) {
      this.loadDossier(dossierUid);
    }
  }

  loadDossier(uid: string) {
    this.dossierLoading.set(true);
    this.api
      .get<DossierSummary>(`/api/v1/recouvrement/dossiers/${uid}`)
      .subscribe({
        next: (d) => {
          this.applyDossier(d);
          this.dossierLoading.set(false);
        },
        error: (err: unknown) => {
          this.toast.showApiError(err, "rec_actions.toast_load_error");
          this.dossierLoading.set(false);
        },
      });
  }

  searchDossiers() {
    const q = this.searchQuery().trim();
    if (this.searchTimer) clearTimeout(this.searchTimer);
    if (!q) {
      this.searchResults.set([]);
      this.showResults.set(false);
      return;
    }
    this.searchTimer = setTimeout(() => {
      this.api
        .get<Page<DossierSummary>>("/api/v1/recouvrement/dossiers", {
          clos: false,
          size: 10,
          q,
        })
        .subscribe({
          next: (p) => {
            this.searchResults.set(p.content);
            this.showResults.set(true);
          },
          error: () => {},
        });
    }, 280);
  }

  selectDossier(d: DossierSummary) {
    this.applyDossier(d);
    this.showResults.set(false);
    this.searchQuery.set("");
  }

  private applyDossier(d: DossierSummary) {
    this.selectedDossier.set(d);
    this.success.set(false);
    this.prefillFromPhase(d.phase);
    this.loadTimeline(d.uid);
  }

  private prefillFromPhase(phase: string) {
    const pref = PREFILL_BY_PHASE[phase];
    if (!pref) return;
    if (!this.form.typeAction) this.form.typeAction = pref.typeAction;
    if (!this.form.resultat) this.form.resultat = pref.resultat;
  }

  private loadTimeline(uid: string) {
    this.timelineLoading.set(true);
    this.api
      .get<ActionRow[]>(`/api/v1/recouvrement/dossiers/${uid}/actions`)
      .subscribe({
        next: (a) => {
          this.timeline.set(a.slice(0, 8));
          this.timelineLoading.set(false);
        },
        error: () => {
          this.timeline.set([]);
          this.timelineLoading.set(false);
        },
      });
  }

  clearDossier() {
    this.selectedDossier.set(null);
    this.success.set(false);
    this.timeline.set([]);
    this.nextDossierUid.set(null);
    this.resetForm();
  }

  submitAction() {
    const d = this.selectedDossier();
    if (!d) {
      this.toast.showI18nError(
        "common.required",
        "rec_actions.toast_need_dossier",
      );
      return;
    }
    if (!this.form.typeAction) {
      this.toast.showI18nError(
        "common.required",
        "rec_actions.toast_need_type",
      );
      return;
    }
    if (this.isEncaissement) {
      const montant = parseFloat(this.form.promesseMontant);
      if (!Number.isFinite(montant) || montant <= 0) {
        this.toast.showI18nError(
          "common.required",
          "rec_actions.toast_need_amount",
        );
        return;
      }
    }
    if (this.saving()) return;
    this.saving.set(true);

    const body: Record<string, unknown> = {
      typeAction: this.form.typeAction,
    };
    const resultat =
      this.form.resultat ||
      (this.form.typeAction === "ENCAISSEMENT_TOTAL"
        ? "PAIEMENT_EFFECTUE"
        : this.form.typeAction === "ENCAISSEMENT_PARTIEL"
          ? "PAIEMENT_PARTIEL"
          : "");
    if (resultat) body["resultat"] = resultat;
    if (this.form.promesseDate) body["promesseDate"] = this.form.promesseDate;
    if (this.form.promesseMontant)
      body["promesseMontant"] = parseFloat(this.form.promesseMontant);
    if (this.form.canalPaiement)
      body["canalPaiement"] = this.form.canalPaiement;
    if (this.form.referenceTransaction)
      body["referenceTransaction"] = this.form.referenceTransaction.trim();
    if (this.form.numeroTelephonePaiement)
      body["numeroTelephonePaiement"] =
        this.form.numeroTelephonePaiement.trim();
    if (this.form.fraisEngages)
      body["fraisEngages"] = parseFloat(this.form.fraisEngages);
    if (this.form.observation)
      body["observation"] = this.form.observation.trim();

    this.api
      .post<unknown>(`/api/v1/recouvrement/dossiers/${d.uid}/actions`, body)
      .subscribe({
        next: () => {
          this.toast.showI18nSuccess(
            "rec_actions.toast_saved_title",
            "rec_actions.toast_saved_body",
          );
          this.saving.set(false);
          this.success.set(true);
          this.resetForm();
          this.loadTimeline(d.uid);
          this.loadNextDossier(d.uid);
        },
        error: (err: unknown) => {
          this.toast.showApiError(err, "rec_actions.toast_save_error", 7000);
          this.saving.set(false);
        },
      });
  }

  private loadNextDossier(currentUid: string) {
    this.api
      .get<Page<DossierSummary>>("/api/v1/recouvrement/dossiers", {
        clos: false,
        size: 8,
      })
      .subscribe({
        next: (p) => {
          const next = p.content.find((x) => x.uid !== currentUid);
          this.nextDossierUid.set(next?.uid ?? null);
        },
        error: () => this.nextDossierUid.set(null),
      });
  }

  openNext() {
    const uid = this.nextDossierUid();
    if (!uid) return;
    this.resetForm();
    this.loadDossier(uid);
  }

  resetForm() {
    this.form = {
      typeAction: "",
      resultat: "",
      promesseDate: "",
      promesseMontant: "",
      canalPaiement: "",
      referenceTransaction: "",
      numeroTelephonePaiement: "",
      fraisEngages: "",
      observation: "",
    };
  }

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
}

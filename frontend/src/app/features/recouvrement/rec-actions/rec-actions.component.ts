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
import { apiErrorMessage } from "../../../core/http/api-error";
import { ToastService } from "../../../core/services/toast.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";

interface DossierSummary {
  uid: string;
  idPret: string;
  nomClient: string;
  montantImpaye: number;
  joursRetard: number;
  phase: string;
  clos: boolean;
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

@Component({
  selector: "app-rec-actions",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink, FcfaPipe, TranslatePipe],
  templateUrl: "./rec-actions.component.html",
  styleUrls: ["./rec-actions.component.scss"],
})
export class RecActionsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);

  readonly actionTypes = ACTION_TYPES;
  readonly canauxPaiement = CANAUX_PAIEMENT;

  // dossier selection
  dossierLoading = signal(false);
  selectedDossier = signal<DossierSummary | null>(null);
  searchQuery = signal("");
  searchResults = signal<DossierSummary[]>([]);
  showResults = signal(false);

  // form
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
    return this.form.canalPaiement === "MTN" || this.form.canalPaiement === "ORANGE";
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
          this.selectedDossier.set(d);
          this.dossierLoading.set(false);
        },
        error: () => this.dossierLoading.set(false),
      });
  }

  searchDossiers() {
    const q = this.searchQuery().trim();
    if (!q) {
      this.searchResults.set([]);
      this.showResults.set(false);
      return;
    }
    this.api
      .get<Page<DossierSummary>>("/api/v1/recouvrement/dossiers", {
        clos: false,
        size: 10,
      })
      .subscribe({
        next: (p) => {
          const filtered = p.content.filter(
            (d) =>
              d.nomClient.toLowerCase().includes(q.toLowerCase()) ||
              d.idPret.toLowerCase().includes(q.toLowerCase()),
          );
          this.searchResults.set(filtered);
          this.showResults.set(true);
        },
        error: () => {},
      });
  }

  selectDossier(d: DossierSummary) {
    this.selectedDossier.set(d);
    this.showResults.set(false);
    this.searchQuery.set("");
  }

  clearDossier() {
    this.selectedDossier.set(null);
    this.success.set(false);
  }

  submitAction() {
    const d = this.selectedDossier();
    if (!d) {
      this.toast.showError("Requis", "Sélectionner un dossier.");
      return;
    }
    if (!this.form.typeAction) {
      this.toast.showError("Requis", "Choisir un type d'action.");
      return;
    }
    if (this.isEncaissement) {
      const montant = parseFloat(this.form.promesseMontant);
      if (!Number.isFinite(montant) || montant <= 0) {
        this.toast.showError(
          "Requis",
          "Indiquer le montant encaissé (FCFA).",
        );
        return;
      }
    }
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
          this.toast.showSuccess(
            "Action enregistrée",
            "L'action a été enregistrée.",
          );
          this.saving.set(false);
          this.success.set(true);
          this.resetForm();
        },
        error: (err: unknown) => {
          this.toast.showError(
            "Action non enregistrée",
            apiErrorMessage(
              err,
              "Impossible d'enregistrer l'action. Vérifiez les champs saisis.",
            ),
            7000,
          );
          this.saving.set(false);
        },
      });
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

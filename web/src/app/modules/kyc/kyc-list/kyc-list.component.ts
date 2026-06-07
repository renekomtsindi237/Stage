import { Component, OnInit } from "@angular/core";
import { PageEvent } from "@angular/material/paginator";
import { trigger, style, transition, animate } from "@angular/animations";
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from "@angular/forms";
import { KycService } from "../kyc.service";
import {
  KycDossierResponse,
  StatutKyc,
  NiveauKyc,
  NiveauRisque,
  TypeDocumentKyc,
  InitierKycRequest,
} from "@core/models/kyc.model";

@Component({
  selector: "imf-kyc-list",
  templateUrl: "./kyc-list.component.html",
  styleUrls: ["./kyc-list.component.scss"],
  animations: [
    trigger("slideIn", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(-12px)" }),
        animate(
          "250ms ease-out",
          style({ opacity: 1, transform: "translateY(0)" }),
        ),
      ]),
    ]),
  ],
})
export class KycListComponent implements OnInit {
  dossiers: KycDossierResponse[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  error = "";

  statutFiltre: StatutKyc | "" = "";
  niveauFiltre: NiveauKyc | "" = "";
  risqueFiltre: NiveauRisque | "" = "";

  showInitierForm = false;
  saving = false;
  initierForm!: UntypedFormGroup;

  readonly statutOptions: Array<{ value: StatutKyc | ""; label: string }> = [
    { value: "", label: "Tous les statuts" },
    { value: "EN_ATTENTE", label: "En attente" },
    { value: "DOCUMENTS_SOUMIS", label: "Documents soumis" },
    { value: "EN_COURS_VERIFICATION", label: "En vérification" },
    { value: "COMPLEMENT_REQUIS", label: "Complément requis" },
    { value: "APPROUVE", label: "Approuvé" },
    { value: "REJETE", label: "Rejeté" },
    { value: "EXPIRE", label: "Expiré" },
    { value: "SUSPENDU", label: "Suspendu" },
  ];

  readonly niveauOptions: Array<{ value: NiveauKyc | ""; label: string }> = [
    { value: "", label: "Tous les niveaux" },
    { value: "NIVEAU_1", label: "Niveau 1 — Simplifié" },
    { value: "NIVEAU_2", label: "Niveau 2 — Standard" },
    { value: "NIVEAU_3", label: "Niveau 3 — Renforcé (PPE/LBC)" },
  ];

  readonly risqueOptions: Array<{ value: NiveauRisque | ""; label: string }> = [
    { value: "", label: "Tout risque" },
    { value: "FAIBLE", label: "Faible" },
    { value: "MOYEN", label: "Moyen" },
    { value: "ELEVE", label: "Élevé" },
    { value: "TRES_ELEVE", label: "Très élevé" },
  ];

  readonly typePieceOptions: TypeDocumentKyc[] = [
    "CNI_RECTO",
    "PASSEPORT",
    "PERMIS_CONDUIRE",
    "CARTE_SEJOUR",
  ];

  readonly displayedColumns = [
    "client",
    "niveau",
    "statut",
    "risque",
    "pep",
    "expiration",
    "actions",
  ];

  constructor(
    private kycService: KycService,
    private fb: UntypedFormBuilder,
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.loadDossiers();
  }

  private initForm(): void {
    this.initierForm = this.fb.group({
      clientId: ["", Validators.required],
      nomClient: ["", Validators.required],
      prenomClient: [""],
      dateNaissance: [""],
      nationalite: ["Camerounaise"],
      telephone: [""],
      email: [""],
      adresse: [""],
      ville: [""],
      profession: [""],
      revenuMensuelEstim: [null],
      typePieceIdentite: [""],
      numeroPiece: [""],
      dateExpirationPiece: [""],
      niveauDemande: ["NIVEAU_1", Validators.required],
      estPep: [false],
      observations: [""],
    });
  }

  loadDossiers(): void {
    this.loading = true;
    this.error = "";
    this.kycService
      .listDossiers(
        this.statutFiltre || undefined,
        this.niveauFiltre || undefined,
        this.risqueFiltre || undefined,
        this.page,
        this.pageSize,
      )
      .subscribe({
        next: (data) => {
          this.dossiers = data.content;
          this.total = data.totalElements;
          this.loading = false;
        },
        error: () => {
          this.error = "error";
          this.loading = false;
        },
      });
  }

  onPageChange(event: PageEvent): void {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadDossiers();
  }

  onFiltreChange(): void {
    this.page = 0;
    this.loadDossiers();
  }

  initierDossier(): void {
    if (this.initierForm.invalid) return;
    this.saving = true;
    const val = this.initierForm.value;
    const req: InitierKycRequest = {
      clientId: val.clientId,
      nomClient: val.nomClient,
      prenomClient: val.prenomClient || undefined,
      dateNaissance: val.dateNaissance || undefined,
      nationalite: val.nationalite || "Camerounaise",
      telephone: val.telephone || undefined,
      email: val.email || undefined,
      adresse: val.adresse || undefined,
      ville: val.ville || undefined,
      profession: val.profession || undefined,
      revenuMensuelEstim: val.revenuMensuelEstim || undefined,
      typePieceIdentite: val.typePieceIdentite || undefined,
      numeroPiece: val.numeroPiece || undefined,
      dateExpirationPiece: val.dateExpirationPiece || undefined,
      niveauDemande: val.niveauDemande,
      estPep: val.estPep,
      observations: val.observations || undefined,
    };
    this.kycService.initierDossier(req).subscribe({
      next: () => {
        this.saving = false;
        this.showInitierForm = false;
        this.initierForm.reset({
          niveauDemande: "NIVEAU_1",
          nationalite: "Camerounaise",
          estPep: false,
        });
        this.loadDossiers();
      },
      error: () => (this.saving = false),
    });
  }

  getStatutLabel(s: StatutKyc): string {
    const map: Record<StatutKyc, string> = {
      EN_ATTENTE: "En attente",
      DOCUMENTS_SOUMIS: "Docs soumis",
      EN_COURS_VERIFICATION: "En vérification",
      COMPLEMENT_REQUIS: "Complément requis",
      APPROUVE: "Approuvé",
      REJETE: "Rejeté",
      EXPIRE: "Expiré",
      SUSPENDU: "Suspendu",
    };
    return map[s] ?? s;
  }

  getStatutClass(s: StatutKyc): string {
    const map: Record<StatutKyc, string> = {
      EN_ATTENTE: "kyc-attente",
      DOCUMENTS_SOUMIS: "kyc-soumis",
      EN_COURS_VERIFICATION: "kyc-verif",
      COMPLEMENT_REQUIS: "kyc-complement",
      APPROUVE: "kyc-approuve",
      REJETE: "kyc-rejete",
      EXPIRE: "kyc-expire",
      SUSPENDU: "kyc-suspendu",
    };
    return map[s] ?? "";
  }

  getNiveauLabel(n: NiveauKyc): string {
    return (
      { NIVEAU_1: "Niveau 1", NIVEAU_2: "Niveau 2", NIVEAU_3: "Niveau 3" }[n] ??
      n
    );
  }

  getNiveauClass(n: NiveauKyc): string {
    return { NIVEAU_1: "niv-1", NIVEAU_2: "niv-2", NIVEAU_3: "niv-3" }[n] ?? "";
  }

  getRisqueClass(r: NiveauRisque): string {
    const map: Record<NiveauRisque, string> = {
      FAIBLE: "risk-faible",
      MOYEN: "risk-moyen",
      ELEVE: "risk-eleve",
      TRES_ELEVE: "risk-tres-eleve",
    };
    return map[r] ?? "";
  }

  isExpirant(d: KycDossierResponse): boolean {
    if (!d.dateExpirationKyc) return false;
    const diff = new Date(d.dateExpirationKyc).getTime() - Date.now();
    return diff > 0 && diff < 30 * 24 * 60 * 60 * 1000;
  }

  isExpire(d: KycDossierResponse): boolean {
    if (!d.dateExpirationKyc) return false;
    return new Date(d.dateExpirationKyc).getTime() < Date.now();
  }

  getPieceLabel(t?: string): string {
    const map: Record<string, string> = {
      CNI_RECTO: "CNI",
      PASSEPORT: "Passeport",
      PERMIS_CONDUIRE: "Permis",
      CARTE_SEJOUR: "Carte séjour",
    };
    return t ? (map[t] ?? t) : "";
  }
}

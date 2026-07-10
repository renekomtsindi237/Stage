import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  ChangeDetectionStrategy,
  ViewChild,
  ElementRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  FormsModule,
  ReactiveFormsModule,
  FormBuilder,
  Validators,
} from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import {
  KycDossier,
  KycDocument,
  StatutKyc,
  NiveauKyc,
  ResultatVerif,
} from "../../../core/models/client.model";
import { environment } from "../../../../environments/environment";

// ── Interfaces ───────────────────────────────────────────────────────────────

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

interface ApiWrapped<T> {
  data: T;
}

// ── Constantes ───────────────────────────────────────────────────────────────

const NIVEAU_ORDER: Record<NiveauKyc, number> = {
  NIVEAU_1: 1,
  NIVEAU_2: 2,
  NIVEAU_3: 3,
};

const DOCS_PAR_NIVEAU: Record<NiveauKyc, string[]> = {
  NIVEAU_1: [
    "CNI_RECTO",
    "CNI_VERSO",
    "PASSEPORT",
    "PHOTO_BIOMETRIQUE",
    "PERMIS_CONDUIRE",
    "CARTE_SEJOUR",
  ],
  NIVEAU_2: [
    "JUSTIFICATIF_DOMICILE",
    "CERTIFICAT_RESIDENCE",
    "CONTRAT_BAIL",
    "FICHE_PAIE",
    "CONTRAT_TRAVAIL",
    "DECLARATION_ACTIVITE",
    "REGISTRE_COMMERCE",
    "EXTRAIT_BANCAIRE",
  ],
  NIVEAU_3: ["DECLARATION_SOURCE_FONDS", "ATTESTATION_PPE", "AUTRE"],
};

const TYPE_LABEL: Record<string, string> = {
  CNI_RECTO: "CNI — Recto",
  CNI_VERSO: "CNI — Verso",
  PASSEPORT: "Passeport",
  PERMIS_CONDUIRE: "Permis de conduire",
  CARTE_SEJOUR: "Carte de séjour",
  PHOTO_BIOMETRIQUE: "Photo biométrique",
  JUSTIFICATIF_DOMICILE: "Just. domicile",
  CERTIFICAT_RESIDENCE: "Certificat résidence",
  CONTRAT_BAIL: "Contrat de bail",
  FICHE_PAIE: "Fiche de paie",
  CONTRAT_TRAVAIL: "Contrat de travail",
  DECLARATION_ACTIVITE: "Décl. activité",
  REGISTRE_COMMERCE: "RCCM",
  EXTRAIT_BANCAIRE: "Extrait bancaire",
  DECLARATION_SOURCE_FONDS: "Décl. origine fonds",
  ATTESTATION_PPE: "Attestation PPE",
  AUTRE: "Autre document",
};

// ── Composant ────────────────────────────────────────────────────────────────

@Component({
  selector: "app-dir-kyc",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: "./dir-kyc.component.html",
  styleUrls: ["./dir-kyc.component.scss"],
})
export class DirKycComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  @ViewChild("fileInput") fileInputRef!: ElementRef<HTMLInputElement>;

  // ── State ───────────────────────────────────────────────────────────────────
  loading = signal(true);
  page = signal<PageResponse<KycDossier> | null>(null);
  selected = signal<KycDossier | null>(null);
  documents = signal<KycDocument[]>([]);
  docsLoading = signal(false);

  showCreatePanel = signal(false);
  createSubmitting = signal(false);
  createError = signal("");
  createSuccess = signal("");

  createForm = this.fb.group({
    clientId: ["", Validators.required],
    nomClient: ["", Validators.required],
    prenomClient: [""],
    dateNaissance: [""],
    lieuNaissance: [""],
    nationalite: [""],
    telephone: [""],
    email: [""],
    adresse: [""],
    ville: [""],
    profession: [""],
    employeur: [""],
    revenuMensuelEstim: [""],
    typePieceIdentite: ["CNI_RECTO"],
    numeroPiece: [""],
    dateEmissionPiece: [""],
    dateExpirationPiece: [""],
    lieuEmissionPiece: [""],
    niveauDemande: ["NIVEAU_1", Validators.required],
    estPep: [false],
    observations: [""],
  });

  filterStatut = signal<StatutKyc | "TOUS">("TOUS");
  filterNiveau = signal<NiveauKyc | "TOUS">("TOUS");
  currentPage = signal(0);

  // ── Verification form ───────────────────────────────────────────────────────
  showVerifPanel = signal(false);
  verifResultat = signal<ResultatVerif>("APPROUVE");
  verifCommentaire = signal("");
  verifMotifRejet = signal("");
  verifNiveauApprouve = signal<NiveauKyc>("NIVEAU_1");
  verifSubmitting = signal(false);

  // ── Risk form ───────────────────────────────────────────────────────────────
  showRisquePanel = signal(false);
  risqueEstPep = signal(false);
  risqueSanctions = signal(false);
  risqueListesNoires = signal(false);
  risqueScoreManuel = signal<number | null>(null);
  risqueMotif = signal("");
  risqueSubmitting = signal(false);

  // ── Document upload form ────────────────────────────────────────────────────
  showUploadPanel = signal(false);
  uploadTypeDoc = signal("CNI_RECTO");
  uploadNomFichier = signal("");
  uploadMimeType = signal("application/pdf");
  uploadBase64 = signal("");
  uploadTaille = signal(0);
  uploadSubmitting = signal(false);
  uploadError = signal("");

  // ── Document validation ─────────────────────────────────────────────────────
  docValidating = signal<string | null>(null);
  docMotifRejet = signal("");
  showMotifFor = signal<string | null>(null);

  // ── Lookups ─────────────────────────────────────────────────────────────────
  readonly niveaux: NiveauKyc[] = ["NIVEAU_1", "NIVEAU_2", "NIVEAU_3"];
  readonly statuts: StatutKyc[] = [
    "EN_ATTENTE",
    "DOCUMENTS_SOUMIS",
    "EN_COURS_VERIFICATION",
    "COMPLEMENT_REQUIS",
    "APPROUVE",
    "REJETE",
    "EXPIRE",
    "SUSPENDU",
  ];

  niveauProgress = computed(() => {
    const d = this.selected();
    if (!d) return 0;
    return ((NIVEAU_ORDER[d.niveauActuel] - 1) / 2) * 100;
  });

  ngOnInit() {
    this.load();
  }

  private resetCreateForm() {
    this.createForm.reset({
      clientId: "",
      nomClient: "",
      prenomClient: "",
      dateNaissance: "",
      lieuNaissance: "",
      nationalite: "",
      telephone: "",
      email: "",
      adresse: "",
      ville: "",
      profession: "",
      employeur: "",
      revenuMensuelEstim: "",
      typePieceIdentite: "CNI_RECTO",
      numeroPiece: "",
      dateEmissionPiece: "",
      dateExpirationPiece: "",
      lieuEmissionPiece: "",
      niveauDemande: "NIVEAU_1",
      estPep: false,
      observations: "",
    });
  }

  openCreatePanel() {
    this.showCreatePanel.set(true);
    this.showVerifPanel.set(false);
    this.showRisquePanel.set(false);
    this.createError.set("");
    this.createSuccess.set("");
  }

  closeCreatePanel() {
    this.showCreatePanel.set(false);
    this.createSubmitting.set(false);
    this.createError.set("");
    this.resetCreateForm();
  }

  // ── Chargement ──────────────────────────────────────────────────────────────
  load(p = 0) {
    this.loading.set(true);
    this.currentPage.set(p);
    const params: Record<string, string | number | boolean | null | undefined> =
      { page: p, size: 15 };
    if (this.filterStatut() !== "TOUS") params["statut"] = this.filterStatut();
    if (this.filterNiveau() !== "TOUS") params["niveau"] = this.filterNiveau();

    this.api
      .get<ApiWrapped<PageResponse<KycDossier>>>("/api/v1/kyc/dossiers", params)
      .subscribe({
        next: (r) => {
          this.page.set(r.data ?? (r as unknown as PageResponse<KycDossier>));
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  selectDossier(d: KycDossier) {
    this.selected.set(d);
    this.showCreatePanel.set(false);
    this.showVerifPanel.set(false);
    this.showRisquePanel.set(false);
    this.showUploadPanel.set(false);
    this.loadDocuments(d.uid);
    this.risqueEstPep.set(d.estPep);
    this.risqueSanctions.set(d.verifSanctions);
    this.risqueListesNoires.set(d.verifListesNoires);
    this.risqueMotif.set(d.motifRisqueEleve ?? "");
    this.verifNiveauApprouve.set(d.niveauDemande);
  }

  submitCreateDossier() {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.createSubmitting.set(true);
    this.createError.set("");
    this.createSuccess.set("");

    const value = this.createForm.getRawValue();
    const payload = {
      clientId: value.clientId?.trim(),
      nomClient: value.nomClient?.trim(),
      prenomClient: value.prenomClient?.trim() || null,
      dateNaissance: value.dateNaissance || null,
      lieuNaissance: value.lieuNaissance?.trim() || null,
      nationalite: value.nationalite?.trim() || null,
      telephone: value.telephone?.trim() || null,
      email: value.email?.trim() || null,
      adresse: value.adresse?.trim() || null,
      ville: value.ville?.trim() || null,
      profession: value.profession?.trim() || null,
      employeur: value.employeur?.trim() || null,
      revenuMensuelEstim:
        value.revenuMensuelEstim === "" || value.revenuMensuelEstim == null
          ? null
          : Number(value.revenuMensuelEstim),
      typePieceIdentite: value.typePieceIdentite || null,
      numeroPiece: value.numeroPiece?.trim() || null,
      dateEmissionPiece: value.dateEmissionPiece || null,
      dateExpirationPiece: value.dateExpirationPiece || null,
      lieuEmissionPiece: value.lieuEmissionPiece?.trim() || null,
      niveauDemande: value.niveauDemande,
      estPep: value.estPep,
      observations: value.observations?.trim() || null,
    };

    this.api
      .post<ApiWrapped<KycDossier>>("/api/v1/kyc/dossiers", payload)
      .subscribe({
        next: (r) => {
          const dossier = r.data ?? (r as unknown as KycDossier);
          this.selected.set(dossier);
          this.createSubmitting.set(false);
          this.createSuccess.set("Dossier KYC créé avec succès.");
          this.closeCreatePanel();
          this.load(0);
        },
        error: (err: { error?: { message?: string } }) => {
          this.createError.set(
            err?.error?.message ?? "Impossible de créer le dossier KYC.",
          );
          this.createSubmitting.set(false);
        },
      });
  }

  closeDetail() {
    this.selected.set(null);
    this.documents.set([]);
  }

  loadDocuments(uid: string) {
    this.docsLoading.set(true);
    this.api
      .get<ApiWrapped<KycDocument[]>>(`/api/v1/kyc/dossiers/${uid}/documents`)
      .subscribe({
        next: (r) => {
          this.documents.set(r.data ?? (r as unknown as KycDocument[]));
          this.docsLoading.set(false);
        },
        error: () => this.docsLoading.set(false),
      });
  }

  // ── Vérification ────────────────────────────────────────────────────────────
  submitVerification() {
    const d = this.selected();
    if (!d) return;
    this.verifSubmitting.set(true);
    const body: Record<string, unknown> = {
      resultat: this.verifResultat(),
      commentaire: this.verifCommentaire() || null,
      niveauApprouve:
        this.verifResultat() === "APPROUVE" ? this.verifNiveauApprouve() : null,
      motifRejet:
        this.verifResultat() === "REJETE" ? this.verifMotifRejet() : null,
    };
    this.api
      .put<ApiWrapped<KycDossier>>(
        `/api/v1/kyc/dossiers/${d.uid}/verifier`,
        body,
      )
      .subscribe({
        next: (r) => {
          this.selected.set(r.data ?? (r as unknown as KycDossier));
          this.showVerifPanel.set(false);
          this.verifSubmitting.set(false);
          this.load(this.currentPage());
        },
        error: () => this.verifSubmitting.set(false),
      });
  }

  // ── Upload document ─────────────────────────────────────────────────────────
  openUpload() {
    this.showUploadPanel.set(true);
    this.showVerifPanel.set(false);
    this.showRisquePanel.set(false);
    this.uploadError.set("");
    this.uploadBase64.set("");
    this.uploadNomFichier.set("");
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      this.uploadError.set("Fichier trop volumineux (max 5 Mo)");
      return;
    }
    this.uploadError.set("");
    this.uploadNomFichier.set(file.name);
    this.uploadMimeType.set(file.type || "application/octet-stream");
    this.uploadTaille.set(file.size);
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      this.uploadBase64.set(result.split(",")[1]);
    };
    reader.readAsDataURL(file);
  }

  submitUpload() {
    const d = this.selected();
    if (!d || !this.uploadBase64() || !this.uploadNomFichier()) return;
    this.uploadSubmitting.set(true);
    this.uploadError.set("");
    const body = {
      typeDocument: this.uploadTypeDoc(),
      nomFichier: this.uploadNomFichier(),
      contenuBase64: this.uploadBase64(),
      mimeType: this.uploadMimeType(),
      tailleOctets: this.uploadTaille(),
    };
    this.api
      .post<ApiWrapped<KycDocument>>(
        `/api/v1/kyc/dossiers/${d.uid}/documents`,
        body,
      )
      .subscribe({
        next: () => {
          this.uploadSubmitting.set(false);
          this.showUploadPanel.set(false);
          this.uploadBase64.set("");
          this.uploadNomFichier.set("");
          this.loadDocuments(d.uid);
        },
        error: (err: { error?: { message?: string } }) => {
          this.uploadError.set(err?.error?.message ?? "Erreur lors de l'envoi");
          this.uploadSubmitting.set(false);
        },
      });
  }

  // ── Validation document ─────────────────────────────────────────────────────
  validerDoc(docUid: string, valide: boolean) {
    const d = this.selected();
    if (!d) return;
    if (!valide && !this.docMotifRejet()) {
      this.showMotifFor.set(docUid);
      return;
    }
    this.docValidating.set(docUid);
    const body = { valide, motifRejet: valide ? null : this.docMotifRejet() };
    this.api
      .put<ApiWrapped<KycDocument>>(
        `/api/v1/kyc/documents/${docUid}/valider`,
        body,
      )
      .subscribe({
        next: () => {
          this.docValidating.set(null);
          this.showMotifFor.set(null);
          this.docMotifRejet.set("");
          this.loadDocuments(d.uid);
        },
        error: () => this.docValidating.set(null),
      });
  }

  // ── Évaluation risque ───────────────────────────────────────────────────────
  submitRisque() {
    const d = this.selected();
    if (!d) return;
    this.risqueSubmitting.set(true);
    const body = {
      estPep: this.risqueEstPep(),
      verifSanctions: this.risqueSanctions(),
      verifListesNoires: this.risqueListesNoires(),
      scoreManuel: this.risqueScoreManuel(),
      motifRisqueEleve: this.risqueMotif() || null,
      observations: null,
    };
    this.api
      .put<ApiWrapped<KycDossier>>(`/api/v1/kyc/dossiers/${d.uid}/risque`, body)
      .subscribe({
        next: (r) => {
          this.selected.set(r.data ?? (r as unknown as KycDossier));
          this.showRisquePanel.set(false);
          this.risqueSubmitting.set(false);
        },
        error: () => this.risqueSubmitting.set(false),
      });
  }

  // ── Helpers UI ──────────────────────────────────────────────────────────────
  typeDocLabel(t: string): string {
    return TYPE_LABEL[t] ?? t;
  }

  docsDisponibles(): string[] {
    const d = this.selected();
    if (!d) return DOCS_PAR_NIVEAU["NIVEAU_1"];
    return [
      ...DOCS_PAR_NIVEAU["NIVEAU_1"],
      ...(d.niveauDemande !== "NIVEAU_1" ? DOCS_PAR_NIVEAU["NIVEAU_2"] : []),
      ...(d.niveauDemande === "NIVEAU_3" ? DOCS_PAR_NIVEAU["NIVEAU_3"] : []),
    ];
  }

  statutBadge(s: StatutKyc): string {
    const m: Record<string, string> = {
      EN_ATTENTE: "badge-moyenne",
      DOCUMENTS_SOUMIS: "badge-basse",
      EN_COURS_VERIFICATION: "badge-basse",
      COMPLEMENT_REQUIS: "badge-haute",
      APPROUVE: "badge-green",
      REJETE: "badge-critique",
      EXPIRE: "badge-haute",
      SUSPENDU: "badge-critique",
    };
    return m[s] ?? "";
  }

  niveauLabel(n: NiveauKyc): string {
    return {
      NIVEAU_1: "Niveau 1 — Identité de base",
      NIVEAU_2: "Niveau 2 — Identité renforcée",
      NIVEAU_3: "Niveau 3 — Diligence renforcée",
    }[n];
  }

  niveauShort(n: NiveauKyc): string {
    return { NIVEAU_1: "N1", NIVEAU_2: "N2", NIVEAU_3: "N3" }[n];
  }

  statutLabel(s: StatutKyc): string {
    const m: Record<string, string> = {
      EN_ATTENTE: "En attente",
      DOCUMENTS_SOUMIS: "Docs soumis",
      EN_COURS_VERIFICATION: "En vérification",
      COMPLEMENT_REQUIS: "Complément requis",
      APPROUVE: "Approuvé",
      REJETE: "Rejeté",
      EXPIRE: "Expiré",
      SUSPENDU: "Suspendu",
    };
    return m[s] ?? s;
  }

  niveauStep(n: NiveauKyc): "done" | "active" | "pending" {
    const d = this.selected();
    if (!d) return "pending";
    const cur = NIVEAU_ORDER[d.niveauActuel];
    const step = NIVEAU_ORDER[n];
    if (step < cur) return "done";
    if (step === cur) return "active";
    return "pending";
  }

  risqueBadge(r: string): string {
    return (
      {
        FAIBLE: "badge-green",
        MOYEN: "badge-moyenne",
        ELEVE: "badge-haute",
        CRITIQUE: "badge-critique",
      }[r] ?? ""
    );
  }

  risqueColor(score: number): string {
    if (score < 30) return "#22c55e";
    if (score < 60) return "#f59e0b";
    if (score < 80) return "#f97316";
    return "#ef4444";
  }

  niveauOptions(demande: NiveauKyc): NiveauKyc[] {
    const max = NIVEAU_ORDER[demande];
    return this.niveaux.filter((n) => NIVEAU_ORDER[n] <= max);
  }

  downloadUrl(doc: KycDocument): string | null {
    return doc.documentUrl ? `${environment.apiUrl}${doc.documentUrl}` : null;
  }

  isPreviewable(doc: KycDocument): boolean {
    const mime = doc.mimeType ?? "";
    return mime.startsWith("image/") || mime === "application/pdf";
  }

  prevPage() {
    if (this.currentPage() > 0) this.load(this.currentPage() - 1);
  }
  nextPage() {
    const p = this.page();
    if (p && this.currentPage() < p.totalPages - 1)
      this.load(this.currentPage() + 1);
  }
}

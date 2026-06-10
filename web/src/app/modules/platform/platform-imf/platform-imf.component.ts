import { Component, OnInit, ViewChild } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute } from "@angular/router";
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { AuthService } from "@core/services/auth.service";
import {
  PlatformService,
  ImfRecord,
  CreateImfPayload,
  CreateImfAdminPayload,
} from "../platform.service";
import { fadeInUp, reveal } from "../../../shared/animations";

type ModalMode = "create-imf" | "create-admin" | "delete-imf" | null;

export const FORMES_JURIDIQUES = [
  "Société Anonyme (SA)",
  "SARL",
  "Coopérative d'épargne et de crédit",
  "Mutuelle d'épargne et de crédit",
  "Association",
];

export const SEGMENTS_CLIENTS = [
  "Particuliers",
  "Micro-entrepreneurs",
  "Groupements / Coopératives",
  "PME",
];

export const TYPES_GARANTIES = [
  "Aval",
  "Caution solidaire",
  "Gage de matériel",
  "Hypothèque",
  "Nantissement",
];

@Component({
  selector: "imf-platform-imf",
  templateUrl: "./platform-imf.component.html",
  styleUrls: ["./platform-imf.component.scss"],
  animations: [fadeInUp, reveal],
})
export class PlatformImfComponent implements OnInit {
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  dataSource = new MatTableDataSource<ImfRecord>([]);
  displayedColumns = [
    "avatar",
    "nom",
    "code",
    "pays",
    "statut",
    "createdAt",
    "actions",
  ];

  loading = true;
  errorCode = 0;

  // ── Modale ───────────────────────────────────────────────────────────────
  modalMode: ModalMode = null;
  selectedImf: ImfRecord | null = null;
  modalLoading = false;
  modalError = "";
  modalSuccess = "";

  // ── Wizard création IMF ──────────────────────────────────────────────────
  wizardStep = 1;
  readonly WIZARD_STEPS = 3;

  step1Form!: FormGroup; // Identité & constitution
  step2Form!: FormGroup; // Capital & segmentation
  step3Form!: FormGroup; // Paramètres métier

  readonly formesJuridiques = FORMES_JURIDIQUES;
  readonly segmentsDisponibles = SEGMENTS_CLIENTS;
  readonly garantiesDisponibles = TYPES_GARANTIES;

  selectedSegments: Record<string, boolean> = {};
  selectedGaranties: Record<string, boolean> = {};

  // ── Formulaire DSI ────────────────────────────────────────────────────────
  adminForm!: FormGroup;
  usernameFocused = false;
  emailFocused = false;

  constructor(
    public auth: AuthService,
    private platformService: PlatformService,
    private fb: FormBuilder,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.step1Form = this.fb.group({
      code: ["", [Validators.required, Validators.pattern(/^[A-Z0-9]{2,20}$/)]],
      nom: ["", [Validators.required, Validators.minLength(3)]],
      denominationSociale: ["", [Validators.required, Validators.minLength(3)]],
      formeJuridique: ["", Validators.required],
      pays: ["Cameroun"],
      adresseSiege: ["", [Validators.required, Validators.minLength(5)]],
      numAgrement: [""],
      telephone: [""],
      email: ["", Validators.email],
    });

    this.step2Form = this.fb.group({
      capitalSocial: [null, [Validators.required, Validators.min(1)]],
    });

    this.step3Form = this.fb.group({
      tauxInteretAnnuel: [
        null,
        [Validators.required, Validators.min(0), Validators.max(100)],
      ],
      dureeMaxCreditMois: [
        null,
        [Validators.required, Validators.min(1), Validators.max(360)],
      ],
      tauxPenaliteRetard: [null, [Validators.required, Validators.min(0)]],
      seuilRelanceJours: [null, [Validators.required, Validators.min(1)]],
      tauxEpargne: [null],
      soldeMinEpargne: [null],
      fraisTenueCompte: [null],
    });

    this.segmentsDisponibles.forEach((s) => (this.selectedSegments[s] = false));
    this.garantiesDisponibles.forEach(
      (g) => (this.selectedGaranties[g] = false),
    );

    this.adminForm = this.fb.group({
      username: ["", [Validators.required, Validators.minLength(3)]],
      email: ["", [Validators.required, Validators.email]],
    });

    this.loadImfs();

    this.route.queryParams.subscribe((p) => {
      if (p["action"] === "create") this.openCreateImf();
    });
  }

  // ── Chargement table ─────────────────────────────────────────────────────

  loadImfs(): void {
    this.loading = true;
    this.errorCode = 0;
    this.platformService.listImfs().subscribe({
      next: (data) => {
        this.dataSource.data = data;
        this.dataSource.paginator = this.paginator;
        this.dataSource.sort = this.sort;
        this.loading = false;
      },
      error: (err) => {
        this.errorCode = err?.status ?? -1;
        this.loading = false;
      },
    });
  }

  applyFilter(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.dataSource.filter = val.trim().toLowerCase();
    this.dataSource.paginator?.firstPage();
  }

  // ── Wizard ───────────────────────────────────────────────────────────────

  openCreateImf(): void {
    this.step1Form.reset({ pays: "Cameroun" });
    this.step2Form.reset();
    this.step3Form.reset();
    this.segmentsDisponibles.forEach((s) => (this.selectedSegments[s] = false));
    this.garantiesDisponibles.forEach(
      (g) => (this.selectedGaranties[g] = false),
    );
    this.wizardStep = 1;
    this.modalError = "";
    this.modalSuccess = "";
    this.modalMode = "create-imf";
  }

  isStepValid(step: number): boolean {
    if (step === 1) return this.step1Form.valid;
    if (step === 2) return this.step2Form.valid && this.anySegmentSelected;
    if (step === 3) return this.step3Form.valid;
    return false;
  }

  isStepDone(step: number): boolean {
    return this.wizardStep > step;
  }

  nextStep(): void {
    if (
      this.wizardStep < this.WIZARD_STEPS &&
      this.isStepValid(this.wizardStep)
    ) {
      this.wizardStep++;
    }
  }

  prevStep(): void {
    if (this.wizardStep > 1) this.wizardStep--;
  }

  get anySegmentSelected(): boolean {
    return Object.values(this.selectedSegments).some((v) => v);
  }

  get segmentsString(): string {
    return Object.entries(this.selectedSegments)
      .filter(([, v]) => v)
      .map(([k]) => k)
      .join(", ");
  }

  get garantiesString(): string {
    return Object.entries(this.selectedGaranties)
      .filter(([, v]) => v)
      .map(([k]) => k)
      .join(", ");
  }

  toggleSegment(segment: string): void {
    this.selectedSegments[segment] = !this.selectedSegments[segment];
  }

  toggleGarantie(garantie: string): void {
    this.selectedGaranties[garantie] = !this.selectedGaranties[garantie];
  }

  submitCreateImf(): void {
    if (
      !this.isStepValid(1) ||
      !this.isStepValid(2) ||
      !this.isStepValid(3) ||
      this.modalLoading
    )
      return;
    this.modalLoading = true;
    this.modalError = "";

    const payload: CreateImfPayload = {
      ...this.step1Form.value,
      ...this.step2Form.value,
      ...this.step3Form.value,
      segmentsClients: this.segmentsString || undefined,
      typesGaranties: this.garantiesString || undefined,
    };

    this.platformService.createImf(payload).subscribe({
      next: (imf) => {
        this.modalLoading = false;
        this.modalSuccess = `IMF « ${imf.nom} » créée avec succès.`;
        // Sauvegarder le logo choisi pendant le wizard
        if (this.wizardLogoPreview) {
          this.auth.setImfLogo(imf.code, this.wizardLogoPreview);
        }
        this.dataSource.data = [...this.dataSource.data, imf];
        setTimeout(() => this.closeModal(), 1800);
      },
      error: (err) => {
        this.modalLoading = false;
        this.modalError = err?.error?.message ?? "Une erreur est survenue.";
      },
    });
  }

  // ── DSI ───────────────────────────────────────────────────────────────────

  openCreateAdmin(imf: ImfRecord): void {
    this.selectedImf = imf;
    this.adminForm.reset();
    this.modalError = "";
    this.modalSuccess = "";
    this.modalMode = "create-admin";
  }

  submitCreateAdmin(): void {
    if (this.adminForm.invalid || !this.selectedImf || this.modalLoading)
      return;
    this.modalLoading = true;
    this.modalError = "";
    const payload: CreateImfAdminPayload = this.adminForm.value;
    const apiCall = this.selectedImf.hasDsi
      ? this.platformService.updateImfAdmin(this.selectedImf.uid, payload)
      : this.platformService.createImfAdmin(this.selectedImf.uid, payload);
    apiCall.subscribe({
        next: (updatedImf) => {
          this.modalLoading = false;
          this.modalSuccess = this.selectedImf!.hasDsi
            ? `Compte DSI « ${payload.username} » mis à jour.`
            : `Compte DSI « ${payload.username} » créé pour ${this.selectedImf!.nom}.`;
          this.dataSource.data = this.dataSource.data.map((i) =>
            i.uid === updatedImf.uid ? updatedImf : i,
          );
          setTimeout(() => this.closeModal(), 1500);
        },
        error: (err) => {
          this.modalLoading = false;
          this.modalError = err?.error?.message ?? "Une erreur est survenue.";
        },
      });
  }

  // ── Suppression ───────────────────────────────────────────────────────────

  openDeleteImf(imf: ImfRecord): void {
    this.selectedImf = imf;
    this.modalError = "";
    this.modalSuccess = "";
    this.modalMode = "delete-imf";
  }

  submitDeleteImf(): void {
    if (!this.selectedImf || this.modalLoading) return;
    this.modalLoading = true;
    this.modalError = "";
    this.platformService.deleteImf(this.selectedImf.uid).subscribe({
      next: () => {
        this.dataSource.data = this.dataSource.data.filter(
          (i) => i.uid !== this.selectedImf!.uid,
        );
        this.modalLoading = false;
        this.modalSuccess = `IMF « ${this.selectedImf!.nom} » supprimée définitivement.`;
        setTimeout(() => this.closeModal(), 1500);
      },
      error: (err) => {
        this.modalLoading = false;
        this.modalError = err?.error?.message ?? "Une erreur est survenue.";
      },
    });
  }

  // ── Logo IMF ──────────────────────────────────────────────────────────────

  wizardLogoPreview: string | null = null;

  /** Retourne le logo d'une IMF ou l'image par défaut */
  getImfLogo(code: string): string {
    if (!code) return "assets/photo_profil.jpg";
    return this.auth.getImfLogo(code) || "assets/photo_profil.jpg";
  }

  /** Ouvre un sélecteur de fichier pour changer le logo d'une IMF existante */
  pickImfLogo(imf: ImfRecord): void {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.onchange = (e: Event) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (ev) => {
        const result = (ev.target as FileReader).result as string;
        this.auth.setImfLogo(imf.code, result);
        this.dataSource.data = [...this.dataSource.data];
      };
      reader.readAsDataURL(file);
    };
    input.click();
  }

  /** Ouvre un sélecteur de fichier pour le logo pendant la création (wizard) */
  pickWizardLogo(): void {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.onchange = (e: Event) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (ev) => {
        this.wizardLogoPreview = (ev.target as FileReader).result as string;
      };
      reader.readAsDataURL(file);
    };
    input.click();
  }

  removeWizardLogo(): void {
    this.wizardLogoPreview = null;
  }

  toggleStatus(imf: ImfRecord): void {
    const obs = imf.actif
      ? this.platformService.deactivateImf(imf.uid)
      : this.platformService.activateImf(imf.uid);
    obs.subscribe({
      next: (updated) => {
        this.dataSource.data = this.dataSource.data.map((i) =>
          i.uid === updated.uid ? updated : i,
        );
      },
      error: () => {},
    });
  }

  closeModal(): void {
    this.modalMode = null;
    this.selectedImf = null;
    this.modalError = "";
    this.modalSuccess = "";
    this.modalLoading = false;
    this.wizardStep = 1;
    this.wizardLogoPreview = null;
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString("fr-FR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  }

  formatCurrency(val?: number): string {
    if (val == null) return "—";
    return new Intl.NumberFormat("fr-FR", {
      style: "currency",
      currency: "XAF",
      maximumFractionDigits: 0,
    }).format(val);
  }

  logout(): void {
    this.auth.logout();
  }

  get totalRows(): number {
    return this.dataSource.filteredData.length;
  }
}

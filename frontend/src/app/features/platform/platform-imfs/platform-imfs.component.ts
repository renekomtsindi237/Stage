import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { map } from "rxjs/operators";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { ImfDetail, ApiResp } from "../../../core/models/platform.model";

@Component({
  selector: "app-platform-imfs",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./platform-imfs.component.html",
  styleUrls: ["./platform-imfs.component.scss"],
})
export class PlatformImfsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  imfs = signal<ImfDetail[]>([]);
  selected = signal<ImfDetail | null>(null);
  showCreate = signal(false);
  step = signal(1);
  submitting = signal(false);
  showDsiForm = signal(false);
  showDeleteConfirm = signal(false);

  // ── Formulaire création IMF (4 étapes) ────────────────────────────────────

  createForm = this.fb.group({
    // Étape 1 — Identité
    code: ["", [Validators.required, Validators.pattern(/^[A-Z0-9]{2,20}$/)]],
    nom: ["", [Validators.required, Validators.minLength(3)]],
    denominationSociale: ["", [Validators.required, Validators.minLength(3)]],
    formeJuridique: ["", Validators.required],
    pays: ["Cameroun"],
    adresseSiege: ["", [Validators.required, Validators.minLength(5)]],
    numAgrement: [""],
    telephone: [""],
    email: ["", Validators.email],
    // Étape 2 — Capital & segmentation
    capitalSocial: [null as number | null, [Validators.required, Validators.min(0.01)]],
    segmentsClients: [""],
    typesGaranties: [""],
    // Étape 3 — Paramètres métier
    tauxInteretAnnuel: [null as number | null, [Validators.required, Validators.min(0), Validators.max(100)]],
    dureeMaxCreditMois: [null as number | null, [Validators.required, Validators.min(1), Validators.max(360)]],
    tauxPenaliteRetard: [null as number | null, [Validators.required, Validators.min(0)]],
    seuilRelanceJours: [null as number | null, [Validators.required, Validators.min(1)]],
    tauxEpargne: [null as number | null],
    soldeMinEpargne: [null as number | null],
    fraisTenueCompte: [null as number | null],
    // Étape 4 — Paramètres opérationnels
    maxDocumentKycOctets: [null as number | null],
    niveauKycMinimal: ["NIVEAU_1"],
    maxTentativesConnexion: [5, [Validators.min(1), Validators.max(20)]],
  });

  dsiForm = this.fb.group({
    username: ["", [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    email: ["", [Validators.required, Validators.email]],
  });

  readonly formeJuridiqueOptions = [
    "SA", "SARL", "SCI", "Coopérative", "Association", "GIC", "Mutuelle", "Autre",
  ];

  readonly niveauKycOptions = ["NIVEAU_1", "NIVEAU_2", "NIVEAU_3"];

  readonly stepLabels = [
    "Identité & constitution",
    "Capital & segmentation",
    "Paramètres métier",
    "Paramètres opérationnels",
  ];

  // ── Champs requis par étape (pour validation partielle) ──────────────────

  private readonly stepFields: Record<number, string[]> = {
    1: ["code", "nom", "denominationSociale", "formeJuridique", "adresseSiege"],
    2: ["capitalSocial"],
    3: ["tauxInteretAnnuel", "dureeMaxCreditMois", "tauxPenaliteRetard", "seuilRelanceJours"],
    4: [],
  };

  ngOnInit() {
    this.loadImfs();
  }

  loadImfs() {
    this.loading.set(true);
    this.api
      .get<ApiResp<ImfDetail[]>>("/api/v1/platform/imf")
      .pipe(map((r) => r.data ?? []))
      .subscribe({
        next: (list) => {
          this.imfs.set(list);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  // ── Sélection d'une IMF ───────────────────────────────────────────────────

  select(imf: ImfDetail) {
    this.selected.set(imf);
    this.showDsiForm.set(false);
    this.showDeleteConfirm.set(false);
  }

  closeDetail() {
    this.selected.set(null);
  }

  // ── Création IMF ─────────────────────────────────────────────────────────

  openCreate() {
    this.createForm.reset({ pays: "Cameroun", niveauKycMinimal: "NIVEAU_1", maxTentativesConnexion: 5 });
    this.step.set(1);
    this.showCreate.set(true);
  }

  closeCreate() {
    this.showCreate.set(false);
  }

  nextStep() {
    const fields = this.stepFields[this.step()];
    fields.forEach((f) => this.createForm.get(f)?.markAsTouched());
    const stepInvalid = fields.some((f) => this.createForm.get(f)?.invalid);
    if (stepInvalid) return;
    if (this.step() < 4) this.step.update((s) => s + 1);
  }

  prevStep() {
    if (this.step() > 1) this.step.update((s) => s - 1);
  }

  isStepFieldInvalid(name: string): boolean {
    const ctrl = this.createForm.get(name);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  submitCreate() {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const raw = this.createForm.value;
    const payload = {
      ...raw,
      code: raw.code?.toUpperCase(),
      maxDocumentKycOctets: raw.maxDocumentKycOctets
        ? Number(raw.maxDocumentKycOctets) * 1_048_576
        : null,
    };
    this.api
      .post<ApiResp<ImfDetail>>("/api/v1/platform/imf", payload)
      .pipe(map((r) => r.data))
      .subscribe({
        next: (imf) => {
          this.submitting.set(false);
          this.showCreate.set(false);
          this.imfs.update((list) => [imf, ...list]);
          this.select(imf);
          this.toast.showSuccess("IMF créée", "L'institution a été enregistrée avec succès.");
        },
        error: (err) => {
          this.submitting.set(false);
          const msg = err?.error?.message ?? "Erreur lors de la création";
          this.toast.showError("Erreur", msg);
        },
      });
  }

  // ── Activer / Désactiver ──────────────────────────────────────────────────

  toggleActif(imf: ImfDetail) {
    const path = imf.actif
      ? `/api/v1/platform/imf/${imf.uid}/deactivate`
      : `/api/v1/platform/imf/${imf.uid}/activate`;
    this.api
      .patch<ApiResp<ImfDetail>>(path, {})
      .pipe(map((r) => r.data))
      .subscribe({
        next: (updated) => {
          this.updateInList(updated);
          this.selected.set(updated);
          const label = updated.actif ? "activée" : "désactivée";
          this.toast.showSuccess("IMF mise à jour", `L'institution a été ${label}.`);
        },
        error: () => this.toast.showError("Erreur", "Impossible de mettre à jour l'IMF."),
      });
  }

  // ── Supprimer ─────────────────────────────────────────────────────────────

  confirmDelete() {
    this.showDeleteConfirm.set(true);
  }

  cancelDelete() {
    this.showDeleteConfirm.set(false);
  }

  deleteImf() {
    const imf = this.selected();
    if (!imf) return;
    this.api.delete<ApiResp<void>>(`/api/v1/platform/imf/${imf.uid}`).subscribe({
      next: () => {
        this.imfs.update((list) => list.filter((i) => i.uid !== imf.uid));
        this.selected.set(null);
        this.showDeleteConfirm.set(false);
        this.toast.showSuccess("IMF supprimée", "L'institution a été supprimée définitivement.");
      },
      error: () => this.toast.showError("Erreur", "Impossible de supprimer l'IMF."),
    });
  }

  // ── Compte DSI ────────────────────────────────────────────────────────────

  openDsiForm() {
    this.dsiForm.reset();
    this.showDsiForm.set(true);
  }

  closeDsiForm() {
    this.showDsiForm.set(false);
  }

  submitDsi() {
    if (this.dsiForm.invalid) {
      this.dsiForm.markAllAsTouched();
      return;
    }
    const imf = this.selected();
    if (!imf) return;
    this.submitting.set(true);
    this.api
      .post<ApiResp<ImfDetail>>(`/api/v1/platform/imf/${imf.uid}/admin`, this.dsiForm.value)
      .pipe(map((r) => r.data))
      .subscribe({
        next: (updated) => {
          this.submitting.set(false);
          this.updateInList(updated);
          this.selected.set(updated);
          this.showDsiForm.set(false);
          this.toast.showSuccess("Compte DSI créé", "Un email OTP a été envoyé au DSI.");
        },
        error: (err) => {
          this.submitting.set(false);
          const msg = err?.error?.message ?? "Erreur lors de la création du DSI";
          this.toast.showError("Erreur", msg);
        },
      });
  }

  suspendDsi() {
    const imf = this.selected();
    if (!imf) return;
    this.api
      .patch<ApiResp<ImfDetail>>(`/api/v1/platform/imf/${imf.uid}/admin/suspend`, {})
      .pipe(map((r) => r.data))
      .subscribe({
        next: (updated) => {
          this.updateInList(updated);
          this.selected.set(updated);
          this.toast.showWarning("DSI suspendu", "Le compte DSI a été désactivé.");
        },
        error: () => this.toast.showError("Erreur", "Impossible de suspendre le DSI."),
      });
  }

  deleteDsi() {
    const imf = this.selected();
    if (!imf) return;
    this.api
      .delete<ApiResp<ImfDetail>>(`/api/v1/platform/imf/${imf.uid}/admin`)
      .pipe(map((r) => r.data))
      .subscribe({
        next: (updated) => {
          this.updateInList(updated);
          this.selected.set(updated);
          this.toast.showSuccess("DSI supprimé", "Le compte DSI a été supprimé.");
        },
        error: () => this.toast.showError("Erreur", "Impossible de supprimer le DSI."),
      });
  }

  // ── Utilitaires ───────────────────────────────────────────────────────────

  private updateInList(updated: ImfDetail) {
    this.imfs.update((list) =>
      list.map((i) => (i.uid === updated.uid ? updated : i)),
    );
  }

  uppercaseCode(event: Event) {
    const val = (event.target as HTMLInputElement).value.toUpperCase();
    this.createForm.get("code")?.setValue(val, { emitEvent: false });
    (event.target as HTMLInputElement).value = val;
  }

  formatBytes(bytes?: number | null): string {
    if (!bytes) return "5 Mo (défaut)";
    return `${(bytes / 1_048_576).toFixed(0)} Mo`;
  }
}

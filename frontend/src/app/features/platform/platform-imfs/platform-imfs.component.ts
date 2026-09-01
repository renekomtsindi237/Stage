import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";

import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { ImfDetail } from "../../../core/models/platform.model";
import { EscCloseDirective } from "../../../shared/directives/esc-close.directive";

@Component({
  selector: "app-platform-imfs",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, EscCloseDirective],
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
  showDsiEditForm = signal(false);
  showDeleteConfirm = signal(false);
  showDsiDeleteConfirm = signal(false);

  createForm = this.fb.group({
    code: ["", [Validators.required, Validators.pattern(/^[A-Z0-9]{2,20}$/)]],
    nom: ["", [Validators.required, Validators.minLength(3)]],
    denominationSociale: ["", [Validators.required, Validators.minLength(3)]],
    formeJuridique: ["", Validators.required],
    pays: ["Cameroun"],
    adresseSiege: ["", [Validators.required, Validators.minLength(5)]],
    numAgrement: [""],
    telephone: [""],
    email: ["", Validators.email],
    capitalSocial: [
      null as number | null,
      [Validators.required, Validators.min(0.01)],
    ],
    segmentsClients: [""],
    typesGaranties: [""],
    tauxInteretAnnuel: [
      null as number | null,
      [Validators.required, Validators.min(0), Validators.max(100)],
    ],
    dureeMaxCreditMois: [
      null as number | null,
      [Validators.required, Validators.min(1), Validators.max(360)],
    ],
    tauxPenaliteRetard: [
      null as number | null,
      [Validators.required, Validators.min(0)],
    ],
    seuilRelanceJours: [
      null as number | null,
      [Validators.required, Validators.min(1)],
    ],
    tauxEpargne: [null as number | null],
    soldeMinEpargne: [null as number | null],
    fraisTenueCompte: [null as number | null],
    maxDocumentKycOctets: [null as number | null],
    niveauKycMinimal: ["NIVEAU_1"],
    maxTentativesConnexion: [5, [Validators.min(1), Validators.max(20)]],
  });

  dsiForm = this.fb.group({
    username: [
      "",
      [Validators.required, Validators.minLength(3), Validators.maxLength(50)],
    ],
    email: ["", [Validators.required, Validators.email]],
  });

  dsiEditForm = this.fb.group({
    username: [
      "",
      [Validators.required, Validators.minLength(3), Validators.maxLength(50)],
    ],
    email: ["", [Validators.required, Validators.email]],
  });

  readonly formeJuridiqueOptions = [
    "SA",
    "SARL",
    "SCI",
    "Coopérative",
    "Association",
    "GIC",
    "Mutuelle",
    "Autre",
  ];

  readonly niveauKycOptions = ["NIVEAU_1", "NIVEAU_2", "NIVEAU_3"];

  readonly stepLabels = [
    "Identité & constitution",
    "Capital & segmentation",
    "Paramètres métier",
    "Paramètres opérationnels",
  ];

  private readonly stepFields: Record<number, string[]> = {
    1: ["code", "nom", "denominationSociale", "formeJuridique", "adresseSiege"],
    2: ["capitalSocial"],
    3: [
      "tauxInteretAnnuel",
      "dureeMaxCreditMois",
      "tauxPenaliteRetard",
      "seuilRelanceJours",
    ],
    4: [],
  };

  ngOnInit() {
    this.loadImfs();
  }

  loadImfs() {
    this.loading.set(true);
    this.api.get<ImfDetail[]>("/api/v1/platform/imf").subscribe({
      next: (list) => {
        this.imfs.set(list ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  select(imf: ImfDetail) {
    this.selected.set(imf);
    this.showDsiForm.set(false);
    this.showDsiEditForm.set(false);
    this.showDeleteConfirm.set(false);
    this.showDsiDeleteConfirm.set(false);
  }

  closeDetail() {
    this.selected.set(null);
  }

  imfInitials(nom: string): string {
    return nom
      .split(/\s+/)
      .slice(0, 2)
      .map((w) => w[0])
      .join("")
      .toUpperCase();
  }

  openCreate() {
    this.createForm.reset({
      pays: "Cameroun",
      niveauKycMinimal: "NIVEAU_1",
      maxTentativesConnexion: 5,
    });
    this.step.set(1);
    this.showCreate.set(true);
  }

  closeCreate() {
    this.showCreate.set(false);
  }

  nextStep() {
    const fields = this.stepFields[this.step()];
    fields.forEach((f) => this.createForm.get(f)?.markAsTouched());
    if (fields.some((f) => this.createForm.get(f)?.invalid)) return;
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
    this.api.post<ImfDetail>("/api/v1/platform/imf", payload).subscribe({
      next: (imf) => {
        this.submitting.set(false);
        this.showCreate.set(false);
        this.imfs.update((list) => [imf, ...list]);
        this.select(imf);
        this.toast.showI18nSuccess(
          "platform_imfs.toast_create_title",
          "platform_imfs.toast_create_body",
        );
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.toast.showApiError(err, "platform_imfs.toast_create_error");
      },
    });
  }

  toggleActif(imf: ImfDetail) {
    const path = imf.actif
      ? `/api/v1/platform/imf/${imf.uid}/deactivate`
      : `/api/v1/platform/imf/${imf.uid}/activate`;
    this.api.patch<ImfDetail>(path, {}).subscribe({
      next: (updated) => {
        this.updateInList(updated);
        this.selected.set(updated);
        this.toast.showI18nSuccess(
          "platform_imfs.toast_update_title",
          updated.actif
            ? "platform_imfs.toast_update_on"
            : "platform_imfs.toast_update_off",
        );
      },
      error: (err: unknown) =>
        this.toast.showApiError(err, "platform_imfs.toast_update_error"),
    });
  }

  confirmDelete() {
    this.showDeleteConfirm.set(true);
  }
  cancelDelete() {
    this.showDeleteConfirm.set(false);
  }

  deleteImf() {
    const imf = this.selected();
    if (!imf) return;
    this.api.delete<void>(`/api/v1/platform/imf/${imf.uid}`).subscribe({
      next: () => {
        this.imfs.update((list) => list.filter((i) => i.uid !== imf.uid));
        this.selected.set(null);
        this.showDeleteConfirm.set(false);
        this.toast.showI18nSuccess(
          "platform_imfs.toast_delete_title",
          "platform_imfs.toast_delete_body",
        );
      },
      error: (err: unknown) =>
        this.toast.showApiError(err, "platform_imfs.toast_delete_error"),
    });
  }

  openDsiForm() {
    this.dsiForm.reset();
    this.showDsiForm.set(true);
    this.showDsiEditForm.set(false);
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
      .post<ImfDetail>(
        `/api/v1/platform/imf/${imf.uid}/admin`,
        this.dsiForm.value,
      )
      .subscribe({
        next: (updated) => {
          this.submitting.set(false);
          this.updateInList(updated);
          this.selected.set(updated);
          this.showDsiForm.set(false);
          this.toast.showI18nSuccess(
            "platform_imfs.toast_dsi_create_title",
            "platform_imfs.toast_dsi_create_body",
          );
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.toast.showApiError(err, "platform_imfs.toast_dsi_create_error");
        },
      });
  }

  openDsiEditForm() {
    this.dsiEditForm.reset();
    this.showDsiEditForm.set(true);
    this.showDsiForm.set(false);
  }

  closeDsiEditForm() {
    this.showDsiEditForm.set(false);
  }

  submitDsiEdit() {
    if (this.dsiEditForm.invalid) {
      this.dsiEditForm.markAllAsTouched();
      return;
    }
    const imf = this.selected();
    if (!imf) return;
    this.submitting.set(true);
    this.api
      .patch<ImfDetail>(
        `/api/v1/platform/imf/${imf.uid}/admin`,
        this.dsiEditForm.value,
      )
      .subscribe({
        next: (updated) => {
          this.submitting.set(false);
          this.updateInList(updated);
          this.selected.set(updated);
          this.showDsiEditForm.set(false);
          this.toast.showI18nSuccess(
            "platform_imfs.toast_dsi_update_title",
            "platform_imfs.toast_dsi_update_body",
          );
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.toast.showApiError(err, "platform_imfs.toast_dsi_update_error");
        },
      });
  }

  suspendDsi() {
    const imf = this.selected();
    if (!imf) return;
    this.api
      .patch<ImfDetail>(`/api/v1/platform/imf/${imf.uid}/admin/suspend`, {})
      .subscribe({
        next: (updated) => {
          this.updateInList(updated);
          this.selected.set(updated);
          this.toast.showI18nWarning(
            "platform_imfs.toast_dsi_suspend_title",
            "platform_imfs.toast_dsi_suspend_body",
          );
        },
        error: (err: unknown) =>
          this.toast.showApiError(err, "platform_imfs.toast_dsi_suspend_error"),
      });
  }

  confirmDsiDelete() {
    this.showDsiDeleteConfirm.set(true);
  }
  cancelDsiDelete() {
    this.showDsiDeleteConfirm.set(false);
  }

  deleteDsi() {
    const imf = this.selected();
    if (!imf) return;
    this.api
      .delete<ImfDetail>(`/api/v1/platform/imf/${imf.uid}/admin`)
      .subscribe({
        next: (updated) => {
          this.updateInList(updated);
          this.selected.set(updated);
          this.showDsiDeleteConfirm.set(false);
          this.toast.showI18nSuccess(
            "platform_imfs.toast_dsi_delete_title",
            "platform_imfs.toast_dsi_delete_body",
          );
        },
        error: (err: unknown) =>
          this.toast.showApiError(err, "platform_imfs.toast_dsi_delete_error"),
      });
  }

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

  formatCapital(v?: number | null): string {
    if (!v) return "—";
    return new Intl.NumberFormat("fr-FR").format(v) + " FCFA";
  }
}

import {
  Component,
  inject,
  signal,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  OnInit,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { ReactiveFormsModule, FormBuilder } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { AuthService } from "../../../core/auth/auth.service";
import { ToastService } from "../../../core/services/toast.service";
import { environment } from "../../../../environments/environment";

interface UploadState {
  progress: number;
  phase: "idle" | "reading" | "uploading" | "done" | "error";
}

interface PaymentConfigResponse {
  mtnActif: boolean | null;
  mtnBaseUrl: string | null;
  mtnEnvironment: string | null;
  mtnApiUser: string | null;
  mtnApiKeyMasked: string | null;
  mtnSubscriptionKeyCollectionMasked: string | null;
  mtnSubscriptionKeyDisbursementMasked: string | null;
  mtnCallbackUrl: string | null;
  orangeActif: boolean | null;
  orangeBaseUrl: string | null;
  orangeEnvironment: string | null;
  orangeMerchantKeyMasked: string | null;
  orangeClientId: string | null;
  orangeClientSecretMasked: string | null;
  orangeMerchantCode: string | null;
  orangeReturnUrl: string | null;
  orangeCancelUrl: string | null;
  orangeNotifUrl: string | null;
  updatedAt: string | null;
}

@Component({
  selector: "app-dsi-settings",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: "./dsi-settings.component.html",
  styleUrls: ["./dsi-settings.component.scss"],
})
export class DsiSettingsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly fb = inject(FormBuilder);

  @ViewChild("fileInput") fileInput!: ElementRef<HTMLInputElement>;

  upload = signal<UploadState>({ progress: 0, phase: "idle" });
  dragOver = signal(false);
  previewUrl = signal<string | null>(null);

  paymentConfig = signal<PaymentConfigResponse | null>(null);
  paymentSaving = signal(false);
  showMtnKey = signal(false);
  showOrangeMerchantKey = signal(false);
  showOrangeClientSecret = signal(false);

  readonly ACCEPTED_TYPES = [
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
  ];
  readonly MAX_SIZE_MB = 5;

  mtnForm = this.fb.group({
    mtnActif: [false],
    mtnBaseUrl: [""],
    mtnEnvironment: ["sandbox"],
    mtnApiUser: [""],
    mtnApiKey: [""],
    mtnSubscriptionKeyCollection: [""],
    mtnSubscriptionKeyDisbursement: [""],
    mtnCallbackUrl: [""],
  });

  orangeForm = this.fb.group({
    orangeActif: [false],
    orangeBaseUrl: [""],
    orangeEnvironment: ["sandbox"],
    orangeMerchantKey: [""],
    orangeClientId: [""],
    orangeClientSecret: [""],
    orangeMerchantCode: [""],
    orangeReturnUrl: [""],
    orangeCancelUrl: [""],
    orangeNotifUrl: [""],
  });

  get currentLogoUrl(): string | null {
    return this.auth.imfLogoUrl();
  }

  get imfName(): string {
    return this.auth.currentUser()?.imfNom ?? "—";
  }

  get imfCode(): string {
    return this.auth.currentUser()?.imfCode ?? "—";
  }

  ngOnInit() {
    this.loadPaymentConfig();
  }

  loadPaymentConfig() {
    this.http
      .get<{ data: PaymentConfigResponse }>(
        `${environment.apiUrl}/api/v1/admin/payment-config`,
      )
      .subscribe({
        next: (res) => {
          const cfg = res.data;
          this.paymentConfig.set(cfg);
          this.mtnForm.patchValue({
            mtnActif: cfg.mtnActif ?? false,
            mtnBaseUrl: cfg.mtnBaseUrl ?? "",
            mtnEnvironment: cfg.mtnEnvironment ?? "sandbox",
            mtnApiUser: cfg.mtnApiUser ?? "",
            mtnCallbackUrl: cfg.mtnCallbackUrl ?? "",
          });
          this.orangeForm.patchValue({
            orangeActif: cfg.orangeActif ?? false,
            orangeBaseUrl: cfg.orangeBaseUrl ?? "",
            orangeEnvironment: cfg.orangeEnvironment ?? "sandbox",
            orangeClientId: cfg.orangeClientId ?? "",
            orangeMerchantCode: cfg.orangeMerchantCode ?? "",
            orangeReturnUrl: cfg.orangeReturnUrl ?? "",
            orangeCancelUrl: cfg.orangeCancelUrl ?? "",
            orangeNotifUrl: cfg.orangeNotifUrl ?? "",
          });
          this.cdr.markForCheck();
        },
        error: () => {
          this.toast.showError(
            "Erreur",
            "Impossible de charger la configuration paiement.",
          );
          this.cdr.markForCheck();
        },
      });
  }

  saveMtn() {
    this.paymentSaving.set(true);
    const v = this.mtnForm.value;
    const body: Record<string, unknown> = {
      mtnActif: v.mtnActif,
      mtnBaseUrl: v.mtnBaseUrl || null,
      mtnEnvironment: v.mtnEnvironment || null,
      mtnApiUser: v.mtnApiUser || null,
      mtnCallbackUrl: v.mtnCallbackUrl || null,
    };
    if (v.mtnApiKey) body["mtnApiKey"] = v.mtnApiKey;
    if (v.mtnSubscriptionKeyCollection)
      body["mtnSubscriptionKeyCollection"] = v.mtnSubscriptionKeyCollection;
    if (v.mtnSubscriptionKeyDisbursement)
      body["mtnSubscriptionKeyDisbursement"] = v.mtnSubscriptionKeyDisbursement;

    this.http
      .put<{ data: PaymentConfigResponse }>(
        `${environment.apiUrl}/api/v1/admin/payment-config`,
        body,
      )
      .subscribe({
        next: (res) => {
          this.paymentConfig.set(res.data);
          this.paymentSaving.set(false);
          this.mtnForm.patchValue({
            mtnApiKey: "",
            mtnSubscriptionKeyCollection: "",
            mtnSubscriptionKeyDisbursement: "",
          });
          this.toast.showSuccess("MTN MoMo", "Configuration enregistrée.");
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.paymentSaving.set(false);
          this.toast.showError(
            "Erreur MTN MoMo",
            err?.error?.message ?? "Erreur lors de la sauvegarde.",
          );
          this.cdr.markForCheck();
        },
      });
  }

  saveOrange() {
    this.paymentSaving.set(true);
    const v = this.orangeForm.value;
    const body: Record<string, unknown> = {
      orangeActif: v.orangeActif,
      orangeBaseUrl: v.orangeBaseUrl || null,
      orangeEnvironment: v.orangeEnvironment || null,
      orangeClientId: v.orangeClientId || null,
      orangeMerchantCode: v.orangeMerchantCode || null,
      orangeReturnUrl: v.orangeReturnUrl || null,
      orangeCancelUrl: v.orangeCancelUrl || null,
      orangeNotifUrl: v.orangeNotifUrl || null,
    };
    if (v.orangeMerchantKey) body["orangeMerchantKey"] = v.orangeMerchantKey;
    if (v.orangeClientSecret) body["orangeClientSecret"] = v.orangeClientSecret;

    this.http
      .put<{ data: PaymentConfigResponse }>(
        `${environment.apiUrl}/api/v1/admin/payment-config`,
        body,
      )
      .subscribe({
        next: (res) => {
          this.paymentConfig.set(res.data);
          this.paymentSaving.set(false);
          this.orangeForm.patchValue({
            orangeMerchantKey: "",
            orangeClientSecret: "",
          });
          this.toast.showSuccess("Orange Money", "Configuration enregistrée.");
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.paymentSaving.set(false);
          this.toast.showError(
            "Erreur Orange Money",
            err?.error?.message ?? "Erreur lors de la sauvegarde.",
          );
          this.cdr.markForCheck();
        },
      });
  }

  openPicker() {
    this.fileInput.nativeElement.click();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.processFile(file);
    input.value = "";
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(true);
    this.cdr.markForCheck();
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(false);
    this.cdr.markForCheck();
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) this.processFile(file);
    this.cdr.markForCheck();
  }

  private processFile(file: File) {
    if (!this.ACCEPTED_TYPES.includes(file.type)) {
      this.toast.showError(
        "Format non supporté",
        "Utilisez JPG, PNG, WebP ou GIF.",
      );
      return;
    }
    if (file.size > this.MAX_SIZE_MB * 1024 * 1024) {
      this.toast.showError(
        "Fichier trop lourd",
        `Le logo doit faire moins de ${this.MAX_SIZE_MB} Mo.`,
      );
      return;
    }

    const reader = new FileReader();
    this.upload.set({ progress: 10, phase: "reading" });
    this.cdr.markForCheck();

    reader.onload = (e) => {
      this.previewUrl.set(e.target?.result as string);
      this.cdr.markForCheck();
      this.uploadFile(file);
    };
    reader.readAsDataURL(file);
  }

  private uploadFile(file: File) {
    this.upload.set({ progress: 30, phase: "uploading" });
    this.cdr.markForCheck();

    const formData = new FormData();
    formData.append("file", file);

    const token = this.auth.getToken();
    const headers: Record<string, string> = token
      ? { Authorization: `Bearer ${token}` }
      : {};

    const progressInterval = setInterval(() => {
      const cur = this.upload();
      if (cur.phase === "uploading" && cur.progress < 85) {
        this.upload.set({ ...cur, progress: cur.progress + 5 });
        this.cdr.markForCheck();
      }
    }, 200);

    this.http
      .post<{
        success: boolean;
        data: { logoUrl: string };
      }>(`${environment.apiUrl}/api/v1/admin/imf/logo`, formData, { headers })
      .subscribe({
        next: (res) => {
          clearInterval(progressInterval);
          const url = res?.data?.logoUrl ?? null;
          this.auth.updateImfLogoUrl(url);
          this.upload.set({ progress: 100, phase: "done" });
          this.cdr.markForCheck();
          this.toast.showSuccess(
            "Logo mis à jour",
            "Votre logo est maintenant stocké sur Cloudflare et visible sur toute la plateforme.",
          );
          setTimeout(() => {
            this.upload.set({ progress: 0, phase: "idle" });
            this.previewUrl.set(null);
            this.cdr.markForCheck();
          }, 2000);
        },
        error: (err) => {
          clearInterval(progressInterval);
          const msg =
            err?.error?.message ??
            "Vérifiez le format et la taille du fichier.";
          this.upload.set({ progress: 0, phase: "error" });
          this.cdr.markForCheck();
          this.toast.showError("Échec de l'upload", msg);
        },
      });
  }

  removeLogo() {
    this.previewUrl.set(null);
    this.upload.set({ progress: 0, phase: "idle" });
    this.cdr.markForCheck();
  }
}

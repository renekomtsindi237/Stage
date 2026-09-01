import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
  ElementRef,
  ViewChild,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { AuthService } from "../../core/auth/auth.service";
import { ToastService } from "../../core/services/toast.service";
import { apiErrorMessage } from "../../core/http/api-error";
import { environment } from "../../../environments/environment";
import { TranslatePipe, TranslateService } from "@ngx-translate/core";

@Component({
  selector: "app-profile",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./profile.component.html",
  styleUrls: ["./profile.component.scss"],
})
export class ProfileComponent {
  readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);
  private readonly http = inject(HttpClient);

  @ViewChild("fileInput") fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild("imfLogoInput") imfLogoInput!: ElementRef<HTMLInputElement>;

  uploading = signal(false);
  uploadingImfLogo = signal(false);
  logoutConfirm = signal(false);
  photoError = signal<string | null>(null);
  logoError = signal<string | null>(null);

  readonly apiBase = environment.apiUrl;
  private static readonly ALLOWED_EXT = [
    ".jpg",
    ".jpeg",
    ".png",
    ".webp",
    ".gif",
  ];
  private static readonly ALLOWED_MIME = new Set([
    "image/jpeg",
    "image/jpg",
    "image/pjpeg",
    "image/png",
    "image/webp",
    "image/gif",
  ]);

  get user() {
    return this.auth.currentUser();
  }

  get isDsi(): boolean {
    return this.auth.role() === "DSI";
  }

  get roleLabel(): string {
    const map: Record<string, string> = {
      SUPER_ADMIN: "Super Administrateur",
      DSI: "Administrateur IMF",
      DIRECTEUR: "Directeur",
      AGENT: "Agent",
      ANALYSTE: "Analyste",
      CAISSIER: "Caissier",
      CHEF_AGENCE: "Chef d'Agence",
      AGENT_CREDIT: "Agent de Crédit",
      ANALYSTE_ENGAGEMENTS: "Analyste Engagements",
      AGENT_SAISIE: "Agent de Saisie",
      RESPONSABLE_RECOUVREMENT: "Responsable Recouvrement",
      SUPPORT: "Support",
    };
    return map[this.user?.role ?? ""] ?? this.user?.role ?? "";
  }

  /** URL complète de l'avatar : priorité à l'URL backend, sinon asset local. */
  get avatarSrc(): string {
    const url = this.user?.avatarUrl;
    if (!url || url.includes("/users/me/avatar")) {
      return `${this.apiBase}/api/v1/public/default-avatar`;
    }
    if (url.startsWith("http")) return url;
    return `${this.apiBase}${url}`;
  }

  triggerUpload() {
    this.fileInput.nativeElement.click();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.photoError.set(null);
    const localError = this.validateImageFile(file);
    if (localError) {
      this.photoError.set(localError);
      this.toast.showError(
        this.i18n.instant("profile.toast_upload_fail"),
        localError,
      );
      input.value = "";
      return;
    }

    this.uploading.set(true);
    this.auth.uploadAvatar(file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.photoError.set(null);
        this.toast.showI18nSuccess(
          "profile.toast_photo_ok_title",
          "profile.toast_photo_ok_body",
        );
        input.value = "";
      },
      error: (err: unknown) => {
        this.uploading.set(false);
        const msg = apiErrorMessage(
          err,
          this.i18n.instant("profile.toast_photo_error"),
        );
        this.photoError.set(msg);
        this.toast.showError(
          this.i18n.instant("profile.toast_upload_fail"),
          msg,
          7000,
        );
        input.value = "";
      },
    });
  }

  removeAvatar() {
    this.auth.removeAvatar().subscribe({
      next: () =>
        this.toast.showI18nSuccess(
          "profile.toast_photo_del_title",
          "profile.toast_photo_del_body",
        ),
      error: (err: unknown) =>
        this.toast.showApiError(err, "profile.toast_photo_del_error"),
    });
  }

  triggerImfLogoUpload() {
    this.imfLogoInput.nativeElement.click();
  }

  onImfLogoSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.logoError.set(null);
    const localError = this.validateImageFile(file);
    if (localError) {
      this.logoError.set(localError);
      this.toast.showError(
        this.i18n.instant("profile.toast_upload_fail"),
        localError,
      );
      input.value = "";
      return;
    }

    const formData = new FormData();
    formData.append("file", file);

    this.uploadingImfLogo.set(true);
    const token = this.auth.getToken();
    this.http
      .post<{
        data: { logoUrl: string };
      }>(
        `${environment.apiUrl}/api/v1/admin/imf/logo`,
        formData,
        token ? { headers: { Authorization: `Bearer ${token}` } } : {},
      )
      .subscribe({
        next: (res) => {
          const url = res?.data?.logoUrl ?? null;
          this.auth.updateImfLogoUrl(url);
          this.uploadingImfLogo.set(false);
          this.logoError.set(null);
          this.toast.showI18nSuccess(
            "profile.toast_logo_ok_title",
            "profile.toast_logo_ok_body",
          );
          input.value = "";
        },
        error: (err: unknown) => {
          this.uploadingImfLogo.set(false);
          const msg = apiErrorMessage(
            err,
            this.i18n.instant("profile.toast_logo_error"),
          );
          this.logoError.set(msg);
          this.toast.showError(
            this.i18n.instant("profile.toast_upload_fail"),
            msg,
            7000,
          );
          input.value = "";
        },
      });
  }

  logout() {
    this.auth.logout();
  }

  private validateImageFile(file: File): string | null {
    const mime = (file.type || "").toLowerCase().split(";")[0].trim();
    const name = file.name.toLowerCase();
    const mimeOk = ProfileComponent.ALLOWED_MIME.has(mime);
    const extOk = ProfileComponent.ALLOWED_EXT.some((ext) =>
      name.endsWith(ext),
    );
    if (!mimeOk && !extOk) {
      return this.i18n.instant("profile.file_unsupported");
    }
    if (file.size > 5 * 1024 * 1024) {
      return this.i18n.instant("profile.file_too_large");
    }
    return null;
  }
}

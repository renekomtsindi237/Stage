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
import { environment } from "../../../environments/environment";
import { TranslatePipe } from "@ngx-translate/core";

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
  private readonly http = inject(HttpClient);

  @ViewChild("fileInput") fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild("imfLogoInput") imfLogoInput!: ElementRef<HTMLInputElement>;

  uploading = signal(false);
  uploadingImfLogo = signal(false);
  logoutConfirm = signal(false);

  readonly apiBase = environment.apiUrl;

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

    if (!file.type.startsWith("image/")) {
      this.toast.showError(
        "Format invalide",
        "Sélectionnez une image (JPG, PNG, WebP).",
      );
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      this.toast.showError(
        "Fichier trop lourd",
        "La photo doit faire moins de 2 Mo.",
      );
      return;
    }

    this.uploading.set(true);
    this.auth.uploadAvatar(file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.toast.showSuccess(
          "Photo mise à jour",
          "Votre photo de profil est visible sur toute la plateforme.",
        );
        input.value = "";
      },
      error: () => {
        this.uploading.set(false);
        this.toast.showError(
          "Erreur",
          "Impossible de téléverser la photo. Réessayez.",
        );
        input.value = "";
      },
    });
  }

  removeAvatar() {
    this.auth.removeAvatar().subscribe({
      next: () =>
        this.toast.showSuccess(
          "Photo supprimée",
          "Votre avatar a été réinitialisé.",
        ),
      error: () =>
        this.toast.showError("Erreur", "Impossible de supprimer la photo."),
    });
  }

  triggerImfLogoUpload() {
    this.imfLogoInput.nativeElement.click();
  }

  onImfLogoSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!file.type.startsWith("image/")) {
      this.toast.showError(
        "Format invalide",
        "Sélectionnez une image (JPG, PNG, WebP).",
      );
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      this.toast.showError(
        "Fichier trop lourd",
        "Le logo doit faire moins de 2 Mo.",
      );
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
          this.toast.showSuccess(
            "Logo mis à jour",
            "Le logo de votre IMF est visible sur toute la plateforme.",
          );
          input.value = "";
        },
        error: () => {
          this.uploadingImfLogo.set(false);
          this.toast.showError(
            "Erreur",
            "Impossible de téléverser le logo. Réessayez.",
          );
          input.value = "";
        },
      });
  }

  logout() {
    this.auth.logout();
  }
}

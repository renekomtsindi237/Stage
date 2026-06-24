import {
  Component,
  inject,
  signal,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { AuthService } from "../../../core/auth/auth.service";
import { ToastService } from "../../../core/services/toast.service";
import { environment } from "../../../../environments/environment";

interface UploadState {
  progress: number;
  phase: "idle" | "reading" | "uploading" | "done" | "error";
}

@Component({
  selector: "app-dsi-settings",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./dsi-settings.component.html",
  styleUrls: ["./dsi-settings.component.scss"],
})
export class DsiSettingsComponent {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild("fileInput") fileInput!: ElementRef<HTMLInputElement>;

  upload = signal<UploadState>({ progress: 0, phase: "idle" });
  dragOver = signal(false);
  previewUrl = signal<string | null>(null);

  readonly ACCEPTED_TYPES = [
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
  ];
  readonly MAX_SIZE_MB = 5;

  get currentLogoUrl(): string | null {
    return this.auth.imfLogoUrl();
  }

  get imfName(): string {
    return this.auth.currentUser()?.imfNom ?? "—";
  }

  get imfCode(): string {
    return this.auth.currentUser()?.imfCode ?? "—";
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
    // Validation type
    if (!this.ACCEPTED_TYPES.includes(file.type)) {
      this.toast.showError(
        "Format non supporté",
        "Utilisez JPG, PNG, WebP ou GIF.",
      );
      return;
    }
    // Validation taille
    if (file.size > this.MAX_SIZE_MB * 1024 * 1024) {
      this.toast.showError(
        "Fichier trop lourd",
        `Le logo doit faire moins de ${this.MAX_SIZE_MB} Mo.`,
      );
      return;
    }

    // Prévisualisation
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

    // Simulation de progression pendant l'upload
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
          // Reset après 2s
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

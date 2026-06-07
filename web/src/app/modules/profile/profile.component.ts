import { Component, OnInit } from "@angular/core";
import {
  FormBuilder,
  FormGroup,
  Validators,
  AbstractControl,
  ValidationErrors,
} from "@angular/forms";
import { AuthService } from "@core/services/auth.service";
import { ProfileService } from "@core/services/profile.service";
import { UserPreferencesService } from "@core/services/user-preferences.service";
import { UserResponse } from "@core/models/user.model";
import { fadeInUp, reveal } from "../../shared/animations";

function passwordMatchValidator(
  control: AbstractControl,
): ValidationErrors | null {
  const newPwd = control.parent?.get("newPassword")?.value;
  return control.value === newPwd ? null : { mismatch: true };
}

@Component({
  selector: "imf-profile",
  templateUrl: "./profile.component.html",
  styleUrls: ["./profile.component.scss"],
  animations: [fadeInUp, reveal],
})
export class ProfileComponent implements OnInit {
  profile: UserResponse | null = null;
  loading = true;
  errorLoad = false;

  // Mot de passe
  pwdForm!: FormGroup;
  pwdLoading = false;
  pwdError = "";
  pwdSuccess = "";
  showCurrentPwd = false;
  showNewPwd = false;
  showConfirmPwd = false;

  // Avatar
  avatarPreview: string | null = null;
  avatarUploading = false;
  avatarError = "";

  // Préférences
  prefsLoading = false;
  prefsSuccess = false;

  readonly PAGE_SIZES = [10, 20, 50];

  constructor(
    public auth: AuthService,
    private profileService: ProfileService,
    private userPrefs: UserPreferencesService,
    private fb: FormBuilder,
  ) {}

  ngOnInit(): void {
    this.pwdForm = this.fb.group({
      currentPassword: ["", [Validators.required]],
      newPassword: ["", [Validators.required, Validators.minLength(8)]],
      confirmPassword: ["", [Validators.required, passwordMatchValidator]],
    });
    this.pwdForm
      .get("newPassword")
      ?.valueChanges.subscribe(() =>
        this.pwdForm.get("confirmPassword")?.updateValueAndValidity(),
      );
    this.loadProfile();
  }

  loadProfile(): void {
    this.loading = true;
    this.errorLoad = false;
    this.profileService.getProfile().subscribe({
      next: (p) => {
        this.profile = p;
        this.avatarPreview = p.avatarUrl || this.auth.getUserAvatar();
        this.loading = false;
      },
      error: () => {
        this.errorLoad = true;
        this.loading = false;
      },
    });
  }

  // ── Avatar ────────────────────────────────────────────────────────────────

  pickAvatar(): void {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/jpeg,image/png,image/webp,image/gif";
    input.onchange = (e: Event) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      this.uploadAvatarFile(file);
    };
    input.click();
  }

  private uploadAvatarFile(file: File): void {
    this.avatarUploading = true;
    this.avatarError = "";

    // Preview immédiat pendant l'upload
    const reader = new FileReader();
    reader.onload = (ev) =>
      (this.avatarPreview = (ev.target as FileReader).result as string);
    reader.readAsDataURL(file);

    this.profileService.uploadAvatar(file).subscribe({
      next: (updated) => {
        this.profile = updated;
        this.avatarPreview = updated.avatarUrl ?? null;
        this.auth.setUserAvatar(updated.avatarUrl ?? "");
        this.avatarUploading = false;
      },
      error: (err) => {
        this.avatarError = err?.error?.message ?? "Erreur lors de l'upload.";
        this.avatarUploading = false;
        // Revenir à l'ancienne valeur
        this.avatarPreview = this.profile?.avatarUrl ?? null;
      },
    });
  }

  removeAvatar(): void {
    this.avatarUploading = true;
    this.profileService.removeAvatar().subscribe({
      next: (updated) => {
        this.profile = updated;
        this.avatarPreview = null;
        this.auth.setUserAvatar("");
        this.avatarUploading = false;
      },
      error: () => {
        this.avatarUploading = false;
      },
    });
  }

  // ── Mot de passe ──────────────────────────────────────────────────────────

  submitChangePassword(): void {
    if (this.pwdForm.invalid || this.pwdLoading) return;
    this.pwdLoading = true;
    this.pwdError = "";
    this.pwdSuccess = "";
    const { currentPassword, newPassword } = this.pwdForm.value;
    this.profileService
      .changePassword({ currentPassword, newPassword })
      .subscribe({
        next: () => {
          this.pwdLoading = false;
          this.pwdSuccess = "Mot de passe mis à jour avec succès.";
          this.pwdForm.reset();
        },
        error: (err) => {
          this.pwdLoading = false;
          this.pwdError = err?.error?.message ?? "Une erreur est survenue.";
        },
      });
  }

  // ── Préférences ───────────────────────────────────────────────────────────

  savePrefs(partial: Record<string, unknown>): void {
    this.prefsLoading = true;
    this.prefsSuccess = false;
    this.userPrefs.patch(partial as any).subscribe({
      next: (updated) => {
        this.profile = updated;
        this.prefsLoading = false;
        this.prefsSuccess = true;
        setTimeout(() => (this.prefsSuccess = false), 3000);
      },
      error: () => {
        this.prefsLoading = false;
      },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  get isSuperAdmin(): boolean {
    return this.auth.isSuperAdmin();
  }

  formatDate(iso?: string | null): string {
    if (!iso) return "—";
    return new Date(iso).toLocaleDateString("fr-FR", {
      day: "2-digit",
      month: "long",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  roleLabel(role: string): string {
    const labels: Record<string, string> = {
      SUPER_ADMIN: "Super Administrateur",
      DIRECTEUR: "Directeur",
      RESPONSABLE_RECOUVREMENT: "Responsable Recouvrement",
      ANALYSTE: "Analyste",
      DSI: "Directeur Systèmes d'Information",
      AGENT: "Agent de terrain",
    };
    return labels[role] ?? role;
  }

  getStrength(): number {
    const pwd: string = this.pwdForm.get("newPassword")?.value ?? "";
    if (pwd.length < 8) return 1;
    const score = [/[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((r) =>
      r.test(pwd),
    ).length;
    return score >= 2 ? 3 : score === 1 ? 2 : 1;
  }
}

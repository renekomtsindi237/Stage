import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
} from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { AuthService } from "../../../core/auth/auth.service";
import { ToastService } from "../../../core/services/toast.service";

type Tab = "superadmin" | "support";
type SupportStep = "email" | "otp";

@Component({
  selector: "app-login-admin",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: "./login-admin.component.html",
  styleUrls: ["./login-admin.component.scss"],
})
export class LoginAdminComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  activeTab = signal<Tab>("superadmin");
  supportStep = signal<SupportStep>("email");

  loading = signal(false);
  error = signal("");
  showPwd = signal(false);
  supportEmail = signal("");

  superAdminForm = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
    motDePasse: ["", [Validators.required, Validators.minLength(6)]],
  });

  supportEmailForm = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
  });

  supportOtpForm = this.fb.group({
    code: ["", [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  setTab(tab: Tab) {
    this.activeTab.set(tab);
    this.error.set("");
    this.supportStep.set("email");
    this.superAdminForm.reset();
    this.supportEmailForm.reset();
    this.supportOtpForm.reset();
  }

  togglePwd() {
    this.showPwd.update((v) => !v);
  }

  submitSuperAdmin() {
    if (this.superAdminForm.invalid) {
      this.superAdminForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set("");
    const { email, motDePasse } = this.superAdminForm.value;
    this.auth.login(email!, motDePasse!).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.showSuccess(
          "Connexion réussie",
          `Bienvenue, ${this.auth.fullName()} !`,
        );
        this.router.navigate([this.auth.defaultRouteForRole()]);
      },
      error: () => {
        this.loading.set(false);
        this.error.set("Email ou mot de passe incorrect.");
      },
    });
  }

  requestOtp() {
    if (this.supportEmailForm.invalid) {
      this.supportEmailForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set("");
    const email = this.supportEmailForm.value.email!;
    this.auth.requestOtp(email).subscribe({
      next: () => {
        this.loading.set(false);
        this.supportEmail.set(email);
        this.supportStep.set("otp");
      },
      error: () => {
        this.loading.set(false);
        this.error.set("Impossible d'envoyer le code. Vérifiez l'email.");
      },
    });
  }

  verifyOtp() {
    if (this.supportOtpForm.invalid) {
      this.supportOtpForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set("");
    const code = this.supportOtpForm.value.code!;
    this.auth.verifyOtp(this.supportEmail(), code).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.showSuccess(
          "Connexion réussie",
          `Bienvenue, ${this.auth.fullName()} !`,
        );
        this.router.navigate([this.auth.defaultRouteForRole()]);
      },
      error: () => {
        this.loading.set(false);
        this.error.set("Code incorrect ou expiré.");
      },
    });
  }

  backToEmail() {
    this.supportStep.set("email");
    this.supportOtpForm.reset();
    this.error.set("");
  }
}

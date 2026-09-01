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
import { TranslatePipe, TranslateService } from "@ngx-translate/core";

@Component({
  selector: "app-login-admin",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: "./login-admin.component.html",
  styleUrls: ["./login-admin.component.scss"],
})
export class LoginAdminComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  loading = signal(false);
  error = signal("");
  showPwd = signal(false);

  form = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
    motDePasse: ["", [Validators.required, Validators.minLength(6)]],
  });

  togglePwd() {
    this.showPwd.update((v) => !v);
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set("");
    const { email, motDePasse } = this.form.value;
    this.auth.login(email!, motDePasse!).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.showI18nSuccess("toast.welcome_title", "toast.welcome_body", {
          name: this.auth.fullName(),
        });
        this.router.navigate([this.auth.defaultRouteForRole()]);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(this.i18n.instant("login_admin.error_invalid"));
      },
    });
  }
}

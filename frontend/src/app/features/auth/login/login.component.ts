import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
} from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { CommonModule } from "@angular/common";
import { AuthService } from "../../../core/auth/auth.service";

@Component({
  selector: "app-login",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./login.component.html",
  styleUrls: ["./login.component.scss"],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  loading = signal(false);
  error = signal("");

  form = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
  });

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set("");
    const email = this.form.value.email!;
    this.auth.requestOtp(email).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(["/login/otp"], { state: { email } });
      },
      error: () => {
        this.loading.set(false);
        this.error.set("Adresse introuvable ou compte désactivé.");
      },
    });
  }

  get emailCtrl() {
    return this.form.controls.email;
  }
}

import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
} from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import type { HttpErrorResponse } from "@angular/common/http";
import { AuthService } from "../../../core/auth/auth.service";
import { environment } from "../../../../environments/environment";
import { TranslatePipe } from "@ngx-translate/core";

@Component({
  selector: "app-login",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: "./login.component.html",
  styleUrls: ["./login.component.scss"],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  loading = signal(false);
  error = signal("");

  showSupport = signal(false);
  supportLoading = signal(false);
  supportSent = signal(false);
  supportError = signal("");

  form = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
  });

  supportForm = this.fb.group({
    nom: ["", [Validators.required, Validators.maxLength(100)]],
    email: ["", [Validators.required, Validators.email]],
    sujet: ["", [Validators.required, Validators.maxLength(200)]],
    message: ["", [Validators.required, Validators.maxLength(2000)]],
    categorie: ["ACCES_COMPTE"],
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
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 0 || err.status === 504 || err.status === 502) {
          this.error.set(
            "Serveur temporairement indisponible. Veuillez réessayer dans quelques instants.",
          );
        } else {
          this.error.set("Adresse introuvable ou compte désactivé.");
        }
      },
    });
  }

  openSupport() {
    this.supportForm.reset({ categorie: "ACCES_COMPTE" });
    this.supportSent.set(false);
    this.supportError.set("");
    this.showSupport.set(true);
  }

  closeSupport() {
    this.showSupport.set(false);
  }

  submitSupport() {
    if (this.supportForm.invalid) {
      this.supportForm.markAllAsTouched();
      return;
    }
    this.supportLoading.set(true);
    this.supportError.set("");

    const body = {
      nom: this.supportForm.value.nom,
      email: this.supportForm.value.email,
      sujet: this.supportForm.value.sujet,
      message: this.supportForm.value.message,
      categorie: this.supportForm.value.categorie,
    };

    this.http
      .post(`${environment.apiUrl}/api/v1/public/contact-support`, body)
      .subscribe({
        next: () => {
          this.supportLoading.set(false);
          this.supportSent.set(true);
        },
        error: (err: HttpErrorResponse) => {
          this.supportLoading.set(false);
          this.supportError.set(
            err?.error?.message ??
              "Une erreur est survenue. Réessayez plus tard.",
          );
        },
      });
  }

  get emailCtrl() {
    return this.form.controls.email;
  }
  get sf() {
    return this.supportForm.controls;
  }
}

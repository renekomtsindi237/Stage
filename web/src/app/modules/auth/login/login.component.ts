import { Component, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { AuthService } from "@core/services/auth.service";
import { FullscreenToastService } from "@core/services/fullscreen-toast.service";

@Component({
  selector: "imf-login",
  templateUrl: "./login.component.html",
  styleUrls: ["./login.component.scss"],
})
export class LoginComponent implements OnInit {
  form!: FormGroup;
  loading = false;
  hidePassword = true;

  usernameFocused = false;
  passwordFocused = false;

  readonly brandFeatures = [
    {
      icon: "dashboard",
      title: "Tableau de bord KPI",
      desc: "PAR, collectes, alertes — tout en temps réel",
      color: "#2563EB",
    },
    {
      icon: "warning_amber",
      title: "Alertes intelligentes",
      desc: "Détection automatique des impayés",
      color: "#F59E0B",
    },
    {
      icon: "bar_chart",
      title: "Exports &amp; Reporting",
      desc: "PDF et CSV en un clic",
      color: "#10B981",
    },
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private toastService: FullscreenToastService,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      username: ["", [Validators.required, Validators.minLength(3)]],
      password: ["", [Validators.required, Validators.minLength(6)]],
    });
    if (this.authService.isLoggedIn()) {
      this.authService.navigateAfterLogin(this.authService.getRole()!);
    }
  }

  get username() {
    return this.form.get("username")!;
  }
  get password() {
    return this.form.get("password")!;
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading) return;
    this.loading = true;

    const { username, password } = this.form.value;

    this.authService.login(username, password).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.mustChangePassword) {
          this.toastService.showSuccess(
            "Connexion réussie",
            "Vous devez définir un nouveau mot de passe avant de continuer.",
            2000,
          );
          setTimeout(
            () => this.router.navigate(["/login/change-password"]),
            2000,
          );
          return;
        }
        const message =
          res.role === "SUPER_ADMIN"
            ? `Bienvenue sur la plateforme MicroRecouv, ${res.username} !`
            : `Bienvenue, ${res.username}. Vous allez être redirigé...`;
        this.toastService.showSuccess("Connexion réussie !", message, 2000);
        setTimeout(() => this.authService.navigateAfterLogin(res.role), 2000);
      },
      error: (err) => {
        this.loading = false;
        const status = err?.status;
        const message =
          status === 401
            ? "Identifiants invalides. Vérifiez votre nom d'utilisateur et votre mot de passe."
            : status === 0
              ? "Serveur injoignable. Vérifiez votre connexion internet."
              : "Une erreur est survenue. Veuillez réessayer.";
        this.toastService.showError("Échec de connexion", message, 0);
      },
    });
  }
}

import {
  Component,
  OnInit,
  OnDestroy,
  ViewChildren,
  ElementRef,
  QueryList,
} from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { trigger, transition, style, animate } from "@angular/animations";
import { AuthService } from "@core/services/auth.service";
import { FullscreenToastService } from "@core/services/fullscreen-toast.service";

export type LoginMode = "imf" | "admin";
export type OtpStep = "email" | "code";

@Component({
  selector: "imf-login",
  templateUrl: "./login.component.html",
  styleUrls: ["./login.component.scss"],
  animations: [
    trigger("fade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(-12px)" }),
        animate(
          "240ms 30ms cubic-bezier(0.16,1,0.3,1)",
          style({ opacity: 1, transform: "translateY(0)" }),
        ),
      ]),
      transition(":leave", [
        animate(
          "160ms ease",
          style({ opacity: 0, transform: "translateY(10px)" }),
        ),
      ]),
    ]),
    trigger("slideUp", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(24px)" }),
        animate(
          "320ms cubic-bezier(0.16,1,0.3,1)",
          style({ opacity: 1, transform: "translateY(0)" }),
        ),
      ]),
    ]),
    trigger("boxPop", [
      transition(":enter", [
        style({ opacity: 0, transform: "scale(0.7)" }),
        animate(
          "200ms cubic-bezier(0.34,1.56,0.64,1)",
          style({ opacity: 1, transform: "scale(1)" }),
        ),
      ]),
    ]),
  ],
})
export class LoginComponent implements OnInit, OnDestroy {
  @ViewChildren("otpBox") otpBoxes!: QueryList<ElementRef<HTMLInputElement>>;

  mode: LoginMode = "imf";
  otpStep: OtpStep = "email";
  loading = false;
  otpEmail = "";

  // OTP boxes state
  otpDigits: string[] = Array(6).fill("");
  resendCountdown = 0;
  private resendTimer?: ReturnType<typeof setInterval>;

  emailForm!: FormGroup;
  otpForm!: FormGroup;
  adminForm!: FormGroup;

  hidePassword = true;
  emailFocused = false;
  passwordFocused = false;

  readonly IMF_ROLES = [
    { label: "DSI", icon: "settings_suggest", color: "#2563EB" },
    { label: "Directeur", icon: "business_center", color: "#0D9488" },
    { label: "Analyste", icon: "analytics", color: "#8B5CF6" },
    { label: "Resp. Recouvrement", icon: "account_balance", color: "#EC4899" },
    { label: "Agent terrain", icon: "person_pin", color: "#F59E0B" },
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: FullscreenToastService,
  ) {}

  ngOnInit(): void {
    this.emailForm = this.fb.group({
      email: ["", [Validators.required, Validators.email]],
    });
    this.otpForm = this.fb.group({
      code: ["", [Validators.required, Validators.pattern(/^\d{6}$/)]],
    });
    this.adminForm = this.fb.group({
      email: ["", [Validators.required, Validators.email]],
      password: ["", [Validators.required, Validators.minLength(6)]],
    });

    const modeParam = this.route.snapshot.queryParamMap.get("mode");
    if (modeParam === "admin") this.mode = "admin";

    if (this.authService.isLoggedIn()) {
      this.authService.navigateAfterLogin(this.authService.getRole()!);
    }
  }

  ngOnDestroy(): void {
    if (this.resendTimer) clearInterval(this.resendTimer);
  }

  // ── Mode switch ──────────────────────────────────────────────────────────

  switchMode(newMode: LoginMode): void {
    if (this.mode === newMode) return;
    this.mode = newMode;
    this.resetOtpState();
    this.emailForm.reset();
    this.otpForm.reset();
    this.adminForm.reset();
    this.router.navigate([], {
      queryParams: newMode === "admin" ? { mode: "admin" } : {},
      replaceUrl: true,
    });
  }

  // ── IMF Step 1 : demande OTP ─────────────────────────────────────────────

  submitRequestOtp(): void {
    if (this.emailForm.invalid || this.loading) return;
    this.loading = true;
    const email: string = this.emailForm.value.email;
    this.authService.requestOtp(email).subscribe({
      next: () => this.onOtpSent(email),
      error: () => this.onOtpSent(email), // réponse générique — ne pas révéler si l'email existe
    });
  }

  private onOtpSent(email: string): void {
    this.loading = false;
    this.otpEmail = email;
    this.otpDigits = Array(6).fill("");
    this.otpForm.get("code")?.setValue("");
    this.otpStep = "code";
    this.startResendTimer();
    // Focus sur la première case après le rendu
    setTimeout(() => this.otpBoxes.first?.nativeElement.focus(), 80);
  }

  backToEmail(): void {
    this.resetOtpState();
    this.otpForm.reset();
    this.otpEmail = "";
  }

  resendOtp(): void {
    if (this.resendCountdown > 0 || this.loading) return;
    this.loading = true;
    this.authService.requestOtp(this.otpEmail).subscribe({
      next: () => {
        this.loading = false;
        this.resetOtpBoxes();
        this.startResendTimer();
      },
      error: () => {
        this.loading = false;
        this.resetOtpBoxes();
        this.startResendTimer();
      },
    });
  }

  private startResendTimer(): void {
    if (this.resendTimer) clearInterval(this.resendTimer);
    this.resendCountdown = 60;
    this.resendTimer = setInterval(() => {
      this.resendCountdown--;
      if (this.resendCountdown <= 0) clearInterval(this.resendTimer);
    }, 1000);
  }

  private resetOtpState(): void {
    this.otpStep = "email";
    this.resendCountdown = 0;
    if (this.resendTimer) clearInterval(this.resendTimer);
  }

  private resetOtpBoxes(): void {
    this.otpDigits = Array(6).fill("");
    this.otpForm.get("code")?.setValue("");
    setTimeout(() => this.otpBoxes.first?.nativeElement.focus(), 80);
  }

  // ── OTP box keyboard handling ────────────────────────────────────────────

  onOtpInput(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const val = input.value.replace(/\D/g, "").slice(-1);
    this.otpDigits[index] = val;
    input.value = val;

    const code = this.otpDigits.join("");
    this.otpForm.get("code")?.setValue(code);

    if (val && index < 5) {
      this.otpBoxes.toArray()[index + 1].nativeElement.focus();
    }

    // Auto-submit quand les 6 chiffres sont saisis
    if (this.otpDigits.every((d) => d !== "")) {
      setTimeout(() => this.submitVerifyOtp(), 200);
    }
  }

  onOtpKeydown(event: KeyboardEvent, index: number): void {
    if (event.key === "Backspace") {
      if (!this.otpDigits[index] && index > 0) {
        this.otpDigits[index - 1] = "";
        this.otpForm.get("code")?.setValue(this.otpDigits.join(""));
        this.otpBoxes.toArray()[index - 1].nativeElement.focus();
      } else {
        this.otpDigits[index] = "";
        this.otpForm.get("code")?.setValue(this.otpDigits.join(""));
      }
    } else if (event.key === "ArrowLeft" && index > 0) {
      this.otpBoxes.toArray()[index - 1].nativeElement.focus();
    } else if (event.key === "ArrowRight" && index < 5) {
      this.otpBoxes.toArray()[index + 1].nativeElement.focus();
    }
  }

  onOtpPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const paste = (event.clipboardData?.getData("text") ?? "")
      .replace(/\D/g, "")
      .slice(0, 6);
    paste.split("").forEach((d, i) => (this.otpDigits[i] = d));
    this.otpForm.get("code")?.setValue(this.otpDigits.join(""));
    const focusIdx = Math.min(paste.length, 5);
    this.otpBoxes.toArray()[focusIdx]?.nativeElement.focus();
    if (paste.length === 6) setTimeout(() => this.submitVerifyOtp(), 200);
  }

  // ── IMF Step 2 : vérification OTP ───────────────────────────────────────

  submitVerifyOtp(): void {
    const code = this.otpForm.get("code")?.value ?? "";
    if (code.length < 6 || this.loading) return;
    this.loading = true;
    this.authService.verifyOtp(this.otpEmail, code).subscribe({
      next: (res) => {
        this.loading = false;
        this.toastService.showLogin(res.username, false);
        setTimeout(() => this.authService.navigateAfterLogin(res.role), 2500);
      },
      error: (err) => {
        this.loading = false;
        this.resetOtpBoxes();
        const msg =
          err?.status === 400
            ? "Code invalide ou expiré. Vérifiez le code reçu par email."
            : "Une erreur est survenue. Veuillez réessayer.";
        this.toastService.showError("Code incorrect", msg, 0);
      },
    });
  }

  // ── Admin login ──────────────────────────────────────────────────────────

  submitAdminLogin(): void {
    if (this.adminForm.invalid || this.loading) return;
    this.loading = true;
    const { email, password } = this.adminForm.value;
    this.authService.login(email, password).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.mustChangePassword) {
          this.toastService.showSuccess(
            "Connexion réussie",
            "Vous devez définir un nouveau mot de passe.",
            2000,
          );
          setTimeout(
            () => this.router.navigate(["/login/change-password"]),
            2000,
          );
          return;
        }
        this.toastService.showLogin(res.username, true);
        setTimeout(() => this.authService.navigateAfterLogin(res.role), 2500);
      },
      error: (err) => {
        this.loading = false;
        const status = err?.status;
        const message =
          status === 401
            ? "Identifiants invalides. Vérifiez votre email et mot de passe."
            : status === 0
              ? "Serveur injoignable. Vérifiez votre connexion."
              : "Une erreur est survenue. Veuillez réessayer.";
        this.toastService.showError("Échec de connexion", message, 0);
      },
    });
  }

  // ── Getters ──────────────────────────────────────────────────────────────

  get imfEmail() {
    return this.emailForm.get("email")!;
  }
  get otpCode() {
    return this.otpForm.get("code")!;
  }
  get adminEmail() {
    return this.adminForm.get("email")!;
  }
  get adminPassword() {
    return this.adminForm.get("password")!;
  }

  get otpComplete(): boolean {
    return this.otpDigits.every((d) => d !== "");
  }

  get otpFilledCount(): number {
    return this.otpDigits.filter((d) => d !== "").length;
  }

  maskEmail(email: string): string {
    const [local, domain] = email.split("@");
    if (!domain) return email;
    const shown = local.length > 2 ? local.slice(0, 2) : (local[0] ?? "");
    return `${shown}${"•".repeat(Math.max(local.length - 2, 3))}@${domain}`;
  }
}

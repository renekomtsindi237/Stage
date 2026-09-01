import {
  Component,
  inject,
  signal,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  ElementRef,
  ViewChildren,
  QueryList,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router } from "@angular/router";
import { AuthService } from "../../../core/auth/auth.service";
import { ToastService } from "../../../core/services/toast.service";
import { NotificationService } from "../../../core/services/notification.service";
import { TranslatePipe, TranslateService } from "@ngx-translate/core";
import { apiErrorMessage } from "../../../core/http/api-error";

@Component({
  selector: "app-otp",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./otp.component.html",
  styleUrls: ["./otp.component.scss"],
})
export class OtpComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly notifService = inject(NotificationService);
  private readonly i18n = inject(TranslateService);

  @ViewChildren("digitInput") digitInputs!: QueryList<
    ElementRef<HTMLInputElement>
  >;

  digits = signal<string[]>(["", "", "", "", "", ""]);
  loading = signal(false);
  error = signal("");
  email = "";
  countdown = signal(60);
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit() {
    const nav = this.router.getCurrentNavigation();
    this.email =
      (nav?.extras?.state as { email?: string })?.email ??
      (history.state as { email?: string })?.email ??
      "";
    if (!this.email) this.router.navigate(["/login"]);
    this.startCountdown();
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  onInput(index: number, event: Event) {
    const input = event.target as HTMLInputElement;
    const val = input.value.replace(/\D/g, "").slice(-1);
    const arr = [...this.digits()];
    arr[index] = val;
    this.digits.set(arr);
    if (val && index < 5) {
      const inputs = this.digitInputs.toArray();
      inputs[index + 1]?.nativeElement.focus();
    }
    if (arr.every((d) => d) && arr.join("").length === 6) this.submit();
  }

  onKeydown(index: number, event: KeyboardEvent) {
    if (event.key === "Backspace" && !this.digits()[index] && index > 0) {
      const inputs = this.digitInputs.toArray();
      inputs[index - 1]?.nativeElement.focus();
    }
  }

  submit() {
    const code = this.digits().join("");
    if (code.length < 6) return;
    this.loading.set(true);
    this.error.set("");
    this.auth.verifyOtp(this.email, code).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.showI18nSuccess("toast.welcome_title", "toast.welcome_body", {
          name: this.auth.fullName(),
        });
        this.notifService.init();
        this.router.navigate([this.auth.defaultRouteForRole()]);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(this.i18n.instant("otp.error_invalid"));
        this.digits.set(["", "", "", "", "", ""]);
        setTimeout(() => this.digitInputs.first?.nativeElement.focus(), 50);
      },
    });
  }

  resend() {
    if (this.countdown() > 0) return;
    this.auth.requestOtp(this.email).subscribe({
      next: () => {
        this.countdown.set(60);
        this.startCountdown();
      },
      error: (err: unknown) =>
        this.error.set(
          apiErrorMessage(err, this.i18n.instant("otp.error_resend")),
        ),
    });
  }

  private startCountdown() {
    clearInterval(this.timer);
    this.timer = setInterval(() => {
      const c = this.countdown();
      if (c <= 0) {
        clearInterval(this.timer);
        return;
      }
      this.countdown.set(c - 1);
    }, 1000);
  }
}

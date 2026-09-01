import { inject, Injectable } from "@angular/core";
import { BehaviorSubject } from "rxjs";
import { TranslateService } from "@ngx-translate/core";
import { apiErrorMessage } from "../http/api-error";

export interface ToastState {
  type: "success" | "error" | "login" | "logout" | "warning" | "info";
  title: string;
  message: string;
  show: boolean;
  username?: string;
  duration: number;
}

@Injectable({ providedIn: "root" })
export class ToastService {
  private readonly i18n = inject(TranslateService);

  private state$ = new BehaviorSubject<ToastState>({
    type: "success",
    title: "",
    message: "",
    show: false,
    duration: 2500,
  });

  readonly toast$ = this.state$.asObservable();

  showSuccess(title: string, message: string, duration = 2500) {
    this.emit("success", title, message, duration);
  }

  showError(title: string, message: string, duration = 4000) {
    this.emit("error", title, message, duration);
  }

  showWarning(title: string, message: string, duration = 3000) {
    this.emit("warning", title, message, duration);
  }

  showInfo(title: string, message: string, duration = 3000) {
    this.emit("info", title, message, duration);
  }

  showI18nSuccess(
    titleKey: string,
    messageKey: string,
    params?: Record<string, unknown>,
    duration = 2500,
  ) {
    this.showSuccess(
      this.i18n.instant(titleKey, params),
      this.i18n.instant(messageKey, params),
      duration,
    );
  }

  showI18nError(
    titleKey: string,
    messageKey: string,
    params?: Record<string, unknown>,
    duration = 4000,
  ) {
    this.showError(
      this.i18n.instant(titleKey, params),
      this.i18n.instant(messageKey, params),
      duration,
    );
  }

  showI18nWarning(
    titleKey: string,
    messageKey: string,
    params?: Record<string, unknown>,
    duration = 3000,
  ) {
    this.showWarning(
      this.i18n.instant(titleKey, params),
      this.i18n.instant(messageKey, params),
      duration,
    );
  }

  showApiError(
    err: unknown,
    fallbackKey = "common.unexpected_error",
    duration = 4000,
  ) {
    this.showError(
      this.i18n.instant("common.error"),
      apiErrorMessage(err, this.i18n.instant(fallbackKey)),
      duration,
    );
  }

  showLogin(username: string, isSuperAdmin = false, duration = 2500) {
    const message = this.i18n.instant(
      isSuperAdmin ? "toast.login_body_admin" : "toast.login_body",
    );
    this.state$.next({
      type: "login",
      title: this.i18n.instant("toast.login_title"),
      message,
      show: true,
      username,
      duration,
    });
    if (duration > 0) setTimeout(() => this.hide(), duration);
  }

  showLogout(username?: string, duration = 2500) {
    this.state$.next({
      type: "logout",
      title: this.i18n.instant("toast.logout_title"),
      message: username
        ? this.i18n.instant("toast.logout_body_named", { name: username })
        : this.i18n.instant("toast.logout_body"),
      show: true,
      username,
      duration,
    });
    if (duration > 0) setTimeout(() => this.hide(), duration);
  }

  hide() {
    const c = this.state$.value;
    this.state$.next({ ...c, show: false });
  }

  private emit(
    type: ToastState["type"],
    title: string,
    message: string,
    duration: number,
  ) {
    this.state$.next({ type, title, message, show: true, duration });
    if (duration > 0) setTimeout(() => this.hide(), duration);
  }
}

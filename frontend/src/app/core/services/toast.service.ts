import { Injectable } from "@angular/core";
import { BehaviorSubject } from "rxjs";

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

  showLogin(username: string, isSuperAdmin = false, duration = 2500) {
    const message = isSuperAdmin
      ? "Bienvenue sur la plateforme MicroRecouv !"
      : "Redirection vers votre espace…";
    this.state$.next({
      type: "login",
      title: "Connexion réussie !",
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
      title: "Déconnexion réussie",
      message: username ? `À bientôt, ${username} !` : "À bientôt !",
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

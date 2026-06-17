import { Injectable, signal, computed, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { tap, catchError, throwError } from "rxjs";
import { environment } from "../../../environments/environment";
import { User, AuthResponse, Role } from "../models/user.model";

const TOKEN_KEY = "mr_token";
const USER_KEY = "mr_user";

@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly currentUser = signal<User | null>(this.loadUser());
  readonly isLoggedIn = computed(() => this.currentUser() !== null);
  readonly role = computed(() => this.currentUser()?.role ?? null);
  readonly fullName = computed(() => {
    const u = this.currentUser();
    return u ? `${u.prenom} ${u.nom}` : "";
  });
  readonly initials = computed(() => {
    const u = this.currentUser();
    return u ? `${u.prenom[0]}${u.nom[0]}`.toUpperCase() : "?";
  });

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  requestOtp(email: string) {
    return this.http.post(
      `${environment.apiUrl}/api/v1/auth/request-otp`,
      null,
      { params: { email } },
    );
  }

  verifyOtp(email: string, code: string) {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/api/v1/auth/verify-otp`, {
        email,
        code,
      })
      .pipe(tap((res: AuthResponse) => this.saveSession(res)));
  }

  login(email: string, motDePasse: string) {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/api/v1/auth/login`, {
        email,
        motDePasse,
      })
      .pipe(
        tap((res: AuthResponse) => this.saveSession(res)),
        catchError((err: unknown) => throwError(() => err)),
      );
  }

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
    this.router.navigate(["/login"]);
  }

  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return r !== null && roles.includes(r);
  }

  defaultRouteForRole(): string {
    switch (this.role()) {
      case "AGENT":
        return "/agent";
      case "ANALYSTE":
        return "/analyste/dashboard";
      case "DIRECTEUR":
        return "/directeur/dashboard";
      case "DSI":
        return "/dsi/dashboard";
      case "SUPER_ADMIN":
        return "/platform/dashboard";
      case "RESPONSABLE_RECOUVREMENT":
        return "/recouvrement/dashboard";
      case "CHEF_AGENCE":
        return "/chef-agence/dashboard";
      case "AGENT_CREDIT":
        return "/credit/dashboard";
      case "ANALYSTE_ENGAGEMENTS":
        return "/engagements/conformite";
      case "AGENT_SAISIE":
        return "/saisie/contrats";
      case "CAISSIER":
        return "/caisse/dashboard";
      case "SUPPORT":
        return "/support/tickets";
      default:
        return "/dashboard";
    }
  }

  private saveSession(res: AuthResponse) {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this.currentUser.set(res.user);
  }

  private loadUser(): User | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }
}

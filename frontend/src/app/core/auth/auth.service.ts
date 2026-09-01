import { Injectable, signal, computed, inject } from "@angular/core";
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Router } from "@angular/router";
import { Observable, tap, catchError, throwError, map } from "rxjs";
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
  readonly fullName = computed(() => this.currentUser()?.username ?? "");
  readonly avatarUrl = computed(() => this.currentUser()?.avatarUrl ?? null);
  readonly imfLogoUrl = computed(() => this.currentUser()?.imfLogoUrl ?? null);
  readonly initials = computed(() => {
    const name = this.currentUser()?.username;
    if (!name) return "?";
    const parts = name.split(/[\s._-]+/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.slice(0, 2).toUpperCase();
  });

  constructor() {
    queueMicrotask(() => {
      if (this.getToken()) {
        this.refreshProfile();
      }
    });
  }

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
        password: motDePasse,
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
    this.router.navigate(["/"]);
  }

  /**
   * Upload la photo de profil vers le disque du backend (/uploads/avatars).
   * Met à jour le signal currentUser avec l'URL retournée.
   */
  uploadAvatar(file: File): Observable<string> {
    const formData = new FormData();
    formData.append("file", file);
    const token = this.getToken();
    const headers = token
      ? new HttpHeaders({ Authorization: `Bearer ${token}` })
      : undefined;
    return this.http
      .post<unknown>(`${environment.apiUrl}/api/v1/users/me/avatar`, formData, {
        headers,
      })
      .pipe(
        map((res) => {
          const data = this.unwrapData<{ avatarUrl?: string }>(res);
          const url = this.normalizeAvatarUrl(data?.avatarUrl);
          if (!url) {
            throw new Error("URL avatar absente");
          }
          return url;
        }),
        tap((url) => this._patchAvatarUrl(url)),
      );
  }

  /**
   * Supprime la photo de profil (retour à l'image par défaut).
   */
  removeAvatar(): Observable<void> {
    const token = this.getToken();
    const headers = token
      ? new HttpHeaders({ Authorization: `Bearer ${token}` })
      : undefined;
    return this.http
      .delete<void>(`${environment.apiUrl}/api/v1/users/me/avatar`, { headers })
      .pipe(tap(() => this._patchAvatarUrl(null)));
  }

  private _patchAvatarUrl(url: string | null) {
    const user = this.currentUser();
    if (!user) return;
    const updated: User = { ...user, avatarUrl: this.normalizeAvatarUrl(url) };
    localStorage.setItem(USER_KEY, JSON.stringify(updated));
    this.currentUser.set(updated);
  }

  /** Évite que <img> appelle GET /users/me/avatar (400 sans Bearer). */
  private normalizeAvatarUrl(url: string | null | undefined): string | null {
    if (!url || url.includes("/users/me/avatar")) return null;
    return url;
  }

  /** @deprecated utiliser uploadAvatar() */
  updateAvatar(url: string | null) {
    this._patchAvatarUrl(url);
  }

  updateImfLogoUrl(url: string | null) {
    const user = this.currentUser();
    if (!user) return;
    const updated: User = { ...user, imfLogoUrl: url };
    localStorage.setItem(USER_KEY, JSON.stringify(updated));
    this.currentUser.set(updated);
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
    localStorage.setItem(TOKEN_KEY, res.accessToken);
    const user: User = {
      username: res.username,
      role: res.role as Role,
      imfUid: res.imfUid ?? null,
      imfCode: res.imfCode ?? null,
      imfNom: res.imfNom ?? null,
      imfLogoUrl: res.imfLogoUrl ?? null,
      mustChangePassword: res.mustChangePassword ?? false,
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
    this.refreshProfile();
  }

  private refreshProfile() {
    this.http.get<unknown>(`${environment.apiUrl}/api/v1/users/me`).subscribe({
      next: (raw) => {
        const me = this.unwrapData<{ avatarUrl?: string | null }>(raw);
        const user = this.currentUser();
        if (!user || !me) return;
        const updated: User = {
          ...user,
          avatarUrl: this.normalizeAvatarUrl(me.avatarUrl),
        };
        localStorage.setItem(USER_KEY, JSON.stringify(updated));
        this.currentUser.set(updated);
      },
      error: () => {
        /* Conservé en local si le profil distant est injoignable (latence). */
      },
    });
  }

  private unwrapData<T>(res: unknown): T | null {
    if (res && typeof res === "object" && "data" in res) {
      return (res as { data: T }).data;
    }
    return (res as T) ?? null;
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

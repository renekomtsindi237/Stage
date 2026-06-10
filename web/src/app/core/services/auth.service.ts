import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { BehaviorSubject, Observable, tap, throwError } from "rxjs";
import { catchError, map } from "rxjs/operators";
import { Router } from "@angular/router";
import { AuthResponse, LoginRequest, OtpInitResponse, OtpVerifyResponse, Role } from "../models/auth.model";

// Tokens (accessToken, refreshToken) stockÃ©s dans des cookies httpOnly cÃ´tÃ© serveur.
// Seules les donnÃ©es non-sensibles restent en localStorage.
const SESSION_KEY = "imf_session"; // Flag de session (pas un token)
const ROLE_KEY = "imf_role";
const USERNAME_KEY = "imf_username";
const IMF_ID_KEY = "imf_id";
const IMF_CODE_KEY = "imf_code";
const IMF_NOM_KEY = "imf_nom";
const AVATAR_KEY = "imf_user_avatar";
const MUST_CHANGE_KEY = "imf_must_change"; // Indique si le mot de passe doit Ãªtre changÃ©

@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly API = "/api/v1/auth";
  private loggedIn$ = new BehaviorSubject<boolean>(this.hasValidToken());

  constructor(
    private http: HttpClient,
    private router: Router,
  ) {}

  login(email: string, password: string): Observable<AuthResponse> {
    const req: LoginRequest = { email, password };
    return this.http
      .post<AuthResponse>(`${this.API}/login`, req, { withCredentials: true })
      .pipe(
        tap((res) => this.storeTokens(res)),
        catchError((err) => throwError(() => err)),
      );
  }

  requestOtp(email: string): Observable<OtpInitResponse> {
    return this.http.post<OtpInitResponse>(`${this.API}/request-otp`, { email });
  }

  verifyOtp(email: string, code: string): Observable<AuthResponse> {
    return this.http
      .post<OtpVerifyResponse>(`${this.API}/verify-otp`, { email, code }, { withCredentials: true })
      .pipe(
        map((res) => ({
          accessToken: res.accessToken,
          refreshToken: res.refreshToken,
          role: res.role as Role,
          username: res.username,
          imfUid: res.imfUid,
          imfCode: res.imfCode,
          imfNom: res.imfNom,
          mustChangePassword: res.mustChangePassword ?? false,
          expiresIn: res.expiresIn,
        })),
        tap((res) => this.storeTokens(res)),
      );
  }

  refresh(): Observable<AuthResponse> {
    // Le refresh token est dans le cookie httpOnly imf_refresh â€” envoyÃ© automatiquement
    return this.http
      .post<AuthResponse>(`${this.API}/refresh`, null, {
        withCredentials: true,
      })
      .pipe(tap((res) => this.storeTokens(res)));
  }

  logout(): void {
    this.http
      .post(`${this.API}/logout`, null, { withCredentials: true })
      .subscribe({ error: () => {} });
    this.clearTokens();
    this.loggedIn$.next(false);
    this.router.navigate(["/login"]);
  }

  navigateAfterLogin(role: Role): void {
    const routes: Partial<Record<Role, string>> = {
      SUPER_ADMIN: "/platform",
      SUPPORT: "/support",
      ANALYSTE: "/analyste/scoring",
      DSI: "/dsi/rgpd",
    };
    this.router.navigate([routes[role] ?? "/dashboard"]);
  }

  isLoggedIn(): boolean {
    return this.hasValidToken();
  }

  getRole(): Role | null {
    return localStorage.getItem(ROLE_KEY) as Role | null;
  }

  getUsername(): string | null {
    return localStorage.getItem(USERNAME_KEY);
  }

  getImfId(): number | null {
    const v = localStorage.getItem(IMF_ID_KEY);
    return v ? Number(v) : null;
  }

  getImfCode(): string | null {
    return localStorage.getItem(IMF_CODE_KEY);
  }

  getImfNom(): string | null {
    return localStorage.getItem(IMF_NOM_KEY);
  }

  isDsi(): boolean {
    return this.getRole() === "DSI";
  }

  getUserAvatar(): string | null {
    return localStorage.getItem(AVATAR_KEY);
  }

  setUserAvatar(avatarUrl: string): void {
    localStorage.setItem(AVATAR_KEY, avatarUrl);
  }

  /** Retourne le logo d'une IMF par son code (stockÃ© localement) */
  getImfLogo(imfCode: string): string | null {
    return localStorage.getItem(`imf_logo_${imfCode}`);
  }

  /** Enregistre le logo d'une IMF par son code */
  setImfLogo(imfCode: string, logoUrl: string): void {
    localStorage.setItem(`imf_logo_${imfCode}`, logoUrl);
  }

  /** Supprime le logo d'une IMF */
  removeImfLogo(imfCode: string): void {
    localStorage.removeItem(`imf_logo_${imfCode}`);
  }

  mustChangePassword(): boolean {
    return localStorage.getItem(MUST_CHANGE_KEY) === "1";
  }

  clearMustChangePassword(): void {
    localStorage.removeItem(MUST_CHANGE_KEY);
  }

  isSuperAdmin(): boolean {
    return this.getRole() === "SUPER_ADMIN";
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.getRole();
    return role !== null && roles.includes(role);
  }

  get isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  private hasValidToken(): boolean {
    return !!localStorage.getItem(SESSION_KEY);
  }

  private storeTokens(res: AuthResponse): void {
    // Les tokens JWT sont dans les cookies httpOnly posÃ©s par le serveur.
    // On stocke uniquement le flag de session et les mÃ©tadonnÃ©es non-sensibles.
    localStorage.setItem(SESSION_KEY, "1");
    localStorage.setItem(ROLE_KEY, res.role);
    localStorage.setItem(USERNAME_KEY, res.username);
    if (res.imfUid != null) {
      localStorage.setItem(IMF_ID_KEY, res.imfUid);
    } else {
      localStorage.removeItem(IMF_ID_KEY);
    }
    if (res.imfCode) {
      localStorage.setItem(IMF_CODE_KEY, res.imfCode);
    } else {
      localStorage.removeItem(IMF_CODE_KEY);
    }
    if (res.imfNom) {
      localStorage.setItem(IMF_NOM_KEY, res.imfNom);
    } else {
      localStorage.removeItem(IMF_NOM_KEY);
    }
    if (res.mustChangePassword) {
      localStorage.setItem(MUST_CHANGE_KEY, "1");
    } else {
      localStorage.removeItem(MUST_CHANGE_KEY);
    }
    this.loggedIn$.next(true);
  }

  private clearTokens(): void {
    localStorage.removeItem(SESSION_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(IMF_ID_KEY);
    localStorage.removeItem(IMF_CODE_KEY);
    localStorage.removeItem(IMF_NOM_KEY);
    localStorage.removeItem(AVATAR_KEY);
    localStorage.removeItem(MUST_CHANGE_KEY);
  }
}


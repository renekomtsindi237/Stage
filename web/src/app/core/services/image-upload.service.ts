import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api-response.model";
import { UserResponse } from "@core/models/user.model";

export interface ImfLogoResponse {
  id: number;
  code: string;
  nom: string;
  logoUrl?: string;
}

/**
 * Service centralisé pour tous les uploads d'images.
 *
 * Périmètre :
 *  - Avatar de l'utilisateur connecté (self-service)
 *  - Avatar d'un utilisateur quelconque (action DSI / admin)
 *  - Logo d'une IMF (action DSI → son IMF)
 *
 * Tous les endpoints acceptent multipart/form-data avec le champ « file ».
 * Types acceptés : JPEG, PNG, WEBP, GIF — taille max 2 Mo (configurée côté backend).
 */
@Injectable({ providedIn: "root" })
export class ImageUploadService {
  private readonly USERS_API = "/api/users";
  private readonly ADMIN_API = "/api/admin";

  constructor(private http: HttpClient) {}

  // ── Avatar self-service ───────────────────────────────────────────────────

  /** Upload ou remplacement de son propre avatar. */
  uploadMyAvatar(file: File): Observable<UserResponse> {
    return this.http
      .post<
        ApiResponse<UserResponse>
      >(`${this.USERS_API}/me/avatar`, this.toFormData(file))
      .pipe(map((r) => r.data));
  }

  /** Supprime son propre avatar. */
  removeMyAvatar(): Observable<UserResponse> {
    return this.http
      .delete<ApiResponse<UserResponse>>(`${this.USERS_API}/me/avatar`)
      .pipe(map((r) => r.data));
  }

  // ── Avatar admin (DSI gère ses utilisateurs) ──────────────────────────────

  /** Upload ou remplacement de l'avatar d'un utilisateur (action DSI). */
  uploadUserAvatar(userId: number, file: File): Observable<UserResponse> {
    return this.http
      .post<
        ApiResponse<UserResponse>
      >(`${this.ADMIN_API}/users/${userId}/avatar`, this.toFormData(file))
      .pipe(map((r) => r.data));
  }

  /** Supprime l'avatar d'un utilisateur (action DSI). */
  removeUserAvatar(userId: number): Observable<UserResponse> {
    return this.http
      .delete<
        ApiResponse<UserResponse>
      >(`${this.ADMIN_API}/users/${userId}/avatar`)
      .pipe(map((r) => r.data));
  }

  // ── Logo IMF ──────────────────────────────────────────────────────────────

  /** Upload ou remplacement du logo de l'IMF (action DSI). */
  uploadImfLogo(file: File): Observable<any> {
    return this.http
      .post<
        ApiResponse<any>
      >(`${this.ADMIN_API}/imf/logo`, this.toFormData(file))
      .pipe(map((r) => r.data));
  }

  // ── Utilitaires ───────────────────────────────────────────────────────────

  /** Ouvre un sélecteur de fichier et appelle le callback avec le fichier choisi. */
  pickImage(
    callback: (file: File) => void,
    accept = "image/jpeg,image/png,image/webp,image/gif",
  ): void {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = accept;
    input.onchange = (e: Event) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) callback(file);
    };
    input.click();
  }

  private toFormData(file: File): FormData {
    const fd = new FormData();
    fd.append("file", file);
    return fd;
  }
}

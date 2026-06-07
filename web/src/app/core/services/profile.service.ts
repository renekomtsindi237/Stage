import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable, map } from "rxjs";
import { ApiResponse } from "../models/api-response.model";
import {
  UserPreferences,
  UserResponse,
  ChangePasswordRequest,
} from "../models/user.model";

@Injectable({ providedIn: "root" })
export class ProfileService {
  private readonly API = "/api/users";

  constructor(private http: HttpClient) {}

  /** Récupère le profil complet de l'utilisateur connecté, incluant ses préférences. */
  getProfile(): Observable<UserResponse> {
    return this.http
      .get<ApiResponse<UserResponse>>(`${this.API}/me`)
      .pipe(map((r) => r.data));
  }

  /**
   * Met à jour les préférences de l'utilisateur connecté (patch partiel).
   * Retourne le profil mis à jour pour un feedback immédiat.
   */
  updatePreferences(prefs: Partial<UserPreferences>): Observable<UserResponse> {
    return this.http
      .patch<ApiResponse<UserResponse>>(`${this.API}/me/preferences`, prefs)
      .pipe(map((r) => r.data));
  }

  /** Change le mot de passe de l'utilisateur connecté. */
  changePassword(payload: ChangePasswordRequest): Observable<void> {
    return this.http
      .patch<ApiResponse<void>>(`${this.API}/me/password`, payload)
      .pipe(map(() => void 0));
  }

  /** Upload un fichier image comme avatar (JPEG/PNG/WEBP/GIF, max 2 Mo). */
  uploadAvatar(file: File): Observable<UserResponse> {
    const form = new FormData();
    form.append("file", file);
    return this.http
      .post<ApiResponse<UserResponse>>(`${this.API}/me/avatar`, form)
      .pipe(map((r) => r.data));
  }

  /** Supprime l'avatar — revient à l'image par défaut. */
  removeAvatar(): Observable<UserResponse> {
    return this.http
      .delete<ApiResponse<UserResponse>>(`${this.API}/me/avatar`)
      .pipe(map((r) => r.data));
  }
}

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
  private readonly API = "/api/v1/users";

  constructor(private http: HttpClient) {}

  /** RÃ©cupÃ¨re le profil complet de l'utilisateur connectÃ©, incluant ses prÃ©fÃ©rences. */
  getProfile(): Observable<UserResponse> {
    return this.http
      .get<ApiResponse<UserResponse>>(`${this.API}/me`)
      .pipe(map((r) => r.data));
  }

  /**
   * Met Ã  jour les prÃ©fÃ©rences de l'utilisateur connectÃ© (patch partiel).
   * Retourne le profil mis Ã  jour pour un feedback immÃ©diat.
   */
  updatePreferences(prefs: Partial<UserPreferences>): Observable<UserResponse> {
    return this.http
      .patch<ApiResponse<UserResponse>>(`${this.API}/me/preferences`, prefs)
      .pipe(map((r) => r.data));
  }

  /** Change le mot de passe de l'utilisateur connectÃ©. */
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

  /** Supprime l'avatar â€” revient Ã  l'image par dÃ©faut. */
  removeAvatar(): Observable<UserResponse> {
    return this.http
      .delete<ApiResponse<UserResponse>>(`${this.API}/me/avatar`)
      .pipe(map((r) => r.data));
  }
}

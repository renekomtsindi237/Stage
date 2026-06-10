import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api-response.model";
import { UserResponse } from "@core/models/user.model";
import { Role } from "@core/models/auth.model";

export interface ImfInfo {
  id: number;
  code: string;
  nom: string;
  pays: string;
  actif: boolean;
  formeJuridique?: string;
  capitalSocial?: number;
  segmentsClients?: string;
  hasDsi: boolean;
}

/** Labels lisibles pour les rÃ´les IMF (hors SUPER_ADMIN). */
export const ROLE_LABELS: Record<string, string> = {
  DSI: "Administrateur IMF",
  DIRECTEUR: "Directeur",
  RESPONSABLE_RECOUVREMENT: "Resp. Recouvrement",
  ANALYSTE: "Analyste CrÃ©dit",
  AGENT: "Agent de terrain",
};

export interface CreateUserPayload {
  username: string;
  password: string;
  email?: string | null;
  role: Role;
  zoneId?: string;
  latitude?: number | null;
  longitude?: number | null;
}

export interface AgenceResponse {
  id: number;
  nom: string;
  ville?: string;
  responsable?: string;
  telephone?: string;
  actif: boolean;
  createdAt: string;
}

export interface CreateAgencePayload {
  nom: string;
  ville?: string;
  responsable?: string;
  telephone?: string;
}

@Injectable({ providedIn: "root" })
export class AdminService {
  private readonly API = "/api/v1/admin";

  constructor(private http: HttpClient) {}

  getImfInfo(): Observable<ImfInfo> {
    return this.http
      .get<ApiResponse<ImfInfo>>(`${this.API}/imf`)
      .pipe(map((r) => r.data));
  }

  listUsers(
    page = 0,
    size = 20,
  ): Observable<{ content: UserResponse[]; total: number }> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http
      .get<ApiResponse<any>>(`${this.API}/users`, { params })
      .pipe(
        map((r) => ({
          content: r.data.content ?? [],
          total: r.data.totalElements ?? 0,
        })),
      );
  }

  createUser(payload: CreateUserPayload): Observable<UserResponse> {
    return this.http
      .post<ApiResponse<UserResponse>>(`${this.API}/users`, payload)
      .pipe(map((r) => r.data));
  }

  deactivate(id: number): Observable<UserResponse> {
    return this.http
      .delete<ApiResponse<UserResponse>>(`${this.API}/users/${id}`)
      .pipe(map((r) => r.data));
  }

  activate(id: number): Observable<UserResponse> {
    return this.http
      .patch<ApiResponse<UserResponse>>(`${this.API}/users/${id}/activate`, {})
      .pipe(map((r) => r.data));
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http
      .patch<
        ApiResponse<void>
      >(`${this.API}/users/${id}/reset-password`, { newPassword })
      .pipe(map(() => void 0));
  }

  listAgences(): Observable<AgenceResponse[]> {
    return this.http
      .get<ApiResponse<AgenceResponse[]>>(`${this.API}/agences`)
      .pipe(map((r) => r.data));
  }

  listAgenceNoms(): Observable<string[]> {
    return this.http
      .get<ApiResponse<string[]>>(`${this.API}/agences/noms`)
      .pipe(map((r) => r.data));
  }

  createAgence(payload: CreateAgencePayload): Observable<AgenceResponse> {
    return this.http
      .post<ApiResponse<AgenceResponse>>(`${this.API}/agences`, payload)
      .pipe(map((r) => r.data));
  }

  toggleAgence(id: number): Observable<AgenceResponse> {
    return this.http
      .patch<
        ApiResponse<AgenceResponse>
      >(`${this.API}/agences/${id}/toggle`, {})
      .pipe(map((r) => r.data));
  }

  deleteAgence(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/agences/${id}`);
  }

  // â”€â”€ Avatar utilisateur (admin) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  uploadUserAvatar(userId: number, file: File): Observable<UserResponse> {
    const fd = new FormData();
    fd.append("file", file);
    return this.http
      .post<ApiResponse<UserResponse>>(`${this.API}/users/${userId}/avatar`, fd)
      .pipe(map((r) => r.data));
  }

  removeUserAvatar(userId: number): Observable<UserResponse> {
    return this.http
      .delete<ApiResponse<UserResponse>>(`${this.API}/users/${userId}/avatar`)
      .pipe(map((r) => r.data));
  }

  // â”€â”€ Logo IMF â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  uploadImfLogo(file: File): Observable<any> {
    const fd = new FormData();
    fd.append("file", file);
    return this.http
      .post<ApiResponse<any>>(`${this.API}/imf/logo`, fd)
      .pipe(map((r) => r.data));
  }
}


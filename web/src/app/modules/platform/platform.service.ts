import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api-response.model";
import type { UserResponse } from "@core/models/user.model";

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface AgenceSupervision {
  id: number;
  nom: string;
  ville: string;
  responsable: string;
  telephone: string;
  actif: boolean;
  createdAt: string;
}

export interface AuditEntry {
  id: number;
  username: string;
  action: string;
  entite: string;
  entiteId: string;
  details: string;
  ipClient: string;
  statut: string;
  createdAt: string;
}

export interface ImfSummary {
  imfId: number;
  userCount: number;
  agenceCount: number;
}

export interface ImfRecord {
  uid: string;
  code: string;
  nom: string;
  pays: string;
  actif: boolean;
  createdAt: string;
  // Constitution
  denominationSociale?: string;
  adresseSiege?: string;
  formeJuridique?: string;
  capitalSocial?: number;
  numAgrement?: string;
  telephone?: string;
  email?: string;
  // ParamÃ¨tres crÃ©dit
  tauxInteretAnnuel?: number;
  dureeMaxCreditMois?: number;
  tauxPenaliteRetard?: number;
  seuilRelanceJours?: number;
  // ParamÃ¨tres Ã©pargne
  tauxEpargne?: number;
  soldeMinEpargne?: number;
  fraisTenueCompte?: number;
  // Segmentation
  segmentsClients?: string;
  typesGaranties?: string;
  // DSI
  hasDsi: boolean;
}

export interface PlatformStats {
  totalImfs: number;
  activeImfs: number;
  inactiveImfs: number;
  totalUsers: number;
  newImfsThisMonth: number;
}

export interface CreateImfPayload {
  // Ã‰tape 1 â€” IdentitÃ©
  code: string;
  nom: string;
  denominationSociale: string;
  formeJuridique: string;
  pays?: string;
  adresseSiege: string;
  numAgrement?: string;
  telephone?: string;
  email?: string;
  // Ã‰tape 2 â€” Capital
  capitalSocial: number;
  segmentsClients?: string;
  typesGaranties?: string;
  // Ã‰tape 3 â€” ParamÃ¨tres mÃ©tier
  tauxInteretAnnuel: number;
  dureeMaxCreditMois: number;
  tauxPenaliteRetard: number;
  seuilRelanceJours: number;
  tauxEpargne?: number;
  soldeMinEpargne?: number;
  fraisTenueCompte?: number;
}

export interface CreateImfAdminPayload {
  username: string;
  email: string;
}

export interface UpdateUserPayload {
  username: string;
  email: string;
  role: string;
  zoneId?: string;
}

export interface DelegateUserPayload {
  toUserUid: string;
}

export interface PlatformConfig {
  accessTokenExpiryMinutes: number;
  refreshTokenExpiryDays: number;
  cookieSecure: boolean;
  smtpHost: string;
  smtpPort: number;
  smtpUser: string;
  firebaseEnabled: boolean;
  dbPoolSize: number;
  environment: string;
}

@Injectable({ providedIn: "root" })
export class PlatformService {
  private readonly API = "/api/v1/platform";

  constructor(private http: HttpClient) {}

  getStats(): Observable<PlatformStats> {
    return this.http
      .get<ApiResponse<PlatformStats>>(`${this.API}/stats`)
      .pipe(map((r) => r.data));
  }

  listImfs(): Observable<ImfRecord[]> {
    return this.http
      .get<ApiResponse<ImfRecord[]>>(`${this.API}/imf`)
      .pipe(map((r) => r.data));
  }

  createImf(payload: CreateImfPayload): Observable<ImfRecord> {
    return this.http
      .post<ApiResponse<ImfRecord>>(`${this.API}/imf`, payload)
      .pipe(map((r) => r.data));
  }

  deactivateImf(uid: string): Observable<ImfRecord> {
    return this.http
      .patch<ApiResponse<ImfRecord>>(`${this.API}/imf/${uid}/deactivate`, {})
      .pipe(map((r) => r.data));
  }

  deleteImf(uid: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.API}/imf/${uid}`)
      .pipe(map(() => void 0));
  }

  activateImf(uid: string): Observable<ImfRecord> {
    return this.http
      .patch<ApiResponse<ImfRecord>>(`${this.API}/imf/${uid}/activate`, {})
      .pipe(map((r) => r.data));
  }

  createImfAdmin(
    imfUid: string,
    payload: CreateImfAdminPayload,
  ): Observable<ImfRecord> {
    return this.http
      .post<ApiResponse<ImfRecord>>(`${this.API}/imf/${imfUid}/admin`, payload)
      .pipe(map((r) => r.data));
  }

  suspendImfAdmin(imfUid: string): Observable<ImfRecord> {
    return this.http
      .patch<ApiResponse<ImfRecord>>(
        `${this.API}/imf/${imfUid}/admin/suspend`,
        {},
      )
      .pipe(map((r) => r.data));
  }

  deleteImfAdmin(imfUid: string): Observable<ImfRecord> {
    return this.http
      .delete<ApiResponse<ImfRecord>>(`${this.API}/imf/${imfUid}/admin`)
      .pipe(map((r) => r.data));
  }

  // â”€â”€ Supervision (SUPER_ADMIN) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  private readonly SUPERVISION = "/api/v1/platform/supervision";

  getImfSummary(imfUid: string): Observable<ImfSummary> {
    return this.http
      .get<ApiResponse<ImfSummary>>(`${this.SUPERVISION}/imf/${imfUid}/summary`)
      .pipe(map((r) => r.data));
  }

  getImfUsers(
    imfUid: string,
    page = 0,
    size = 20,
  ): Observable<PageResponse<UserResponse>> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http
      .get<
        ApiResponse<PageResponse<UserResponse>>
      >(`${this.SUPERVISION}/imf/${imfUid}/users`, { params })
      .pipe(map((r) => r.data));
  }

  getImfAgences(imfUid: string): Observable<AgenceSupervision[]> {
    return this.http
      .get<
        ApiResponse<AgenceSupervision[]>
      >(`${this.SUPERVISION}/imf/${imfUid}/agences`)
      .pipe(map((r) => r.data));
  }

  getImfAudit(
    imfUid: string,
    page = 0,
    size = 50,
  ): Observable<PageResponse<AuditEntry>> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http
      .get<
        ApiResponse<PageResponse<AuditEntry>>
      >(`${this.SUPERVISION}/imf/${imfUid}/audit`, { params })
      .pipe(map((r) => r.data));
  }

  // ── Gestion utilisateurs (supervision write) ──────────────────────────────

  updateImfUser(
    imfUid: string,
    userUid: string,
    payload: UpdateUserPayload,
  ): Observable<UserResponse> {
    return this.http
      .patch<
        ApiResponse<UserResponse>
      >(`${this.SUPERVISION}/imf/${imfUid}/users/${userUid}`, payload)
      .pipe(map((r) => r.data));
  }

  deleteImfUser(imfUid: string, userUid: string): Observable<void> {
    return this.http
      .delete<
        ApiResponse<void>
      >(`${this.SUPERVISION}/imf/${imfUid}/users/${userUid}`)
      .pipe(map(() => void 0));
  }

  suspendImfUser(imfUid: string, userUid: string): Observable<UserResponse> {
    return this.http
      .patch<
        ApiResponse<UserResponse>
      >(`${this.SUPERVISION}/imf/${imfUid}/users/${userUid}/suspend`, {})
      .pipe(map((r) => r.data));
  }

  reactivateImfUser(imfUid: string, userUid: string): Observable<UserResponse> {
    return this.http
      .patch<
        ApiResponse<UserResponse>
      >(`${this.SUPERVISION}/imf/${imfUid}/users/${userUid}/reactivate`, {})
      .pipe(map((r) => r.data));
  }

  delegateImfUser(
    imfUid: string,
    fromUserUid: string,
    payload: DelegateUserPayload,
  ): Observable<UserResponse> {
    return this.http
      .post<
        ApiResponse<UserResponse>
      >(`${this.SUPERVISION}/imf/${imfUid}/users/${fromUserUid}/delegate`, payload)
      .pipe(map((r) => r.data));
  }

  getGlobalAudit(page = 0, size = 50): Observable<PageResponse<AuditEntry>> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http
      .get<
        ApiResponse<PageResponse<AuditEntry>>
      >(`${this.SUPERVISION}/audit`, { params })
      .pipe(map((r) => r.data));
  }

  getConfig(): Observable<PlatformConfig> {
    return this.http
      .get<ApiResponse<PlatformConfig>>(`${this.API}/config`)
      .pipe(map((r) => r.data));
  }
}

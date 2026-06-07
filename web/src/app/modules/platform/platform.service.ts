import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api-response.model";
import { UserResponse } from "@core/models/user.model";

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
  id: number;
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
  // Paramètres crédit
  tauxInteretAnnuel?: number;
  dureeMaxCreditMois?: number;
  tauxPenaliteRetard?: number;
  seuilRelanceJours?: number;
  // Paramètres épargne
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
  // Étape 1 — Identité
  code: string;
  nom: string;
  denominationSociale: string;
  formeJuridique: string;
  pays?: string;
  adresseSiege: string;
  numAgrement?: string;
  telephone?: string;
  email?: string;
  // Étape 2 — Capital
  capitalSocial: number;
  segmentsClients?: string;
  typesGaranties?: string;
  // Étape 3 — Paramètres métier
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
  password: string;
}

@Injectable({ providedIn: "root" })
export class PlatformService {
  private readonly API = "/api/platform";

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

  deactivateImf(id: number): Observable<ImfRecord> {
    return this.http
      .patch<ApiResponse<ImfRecord>>(`${this.API}/imf/${id}/deactivate`, {})
      .pipe(map((r) => r.data));
  }

  deleteImf(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.API}/imf/${id}`)
      .pipe(map(() => void 0));
  }

  activateImf(id: number): Observable<ImfRecord> {
    return this.http
      .patch<ApiResponse<ImfRecord>>(`${this.API}/imf/${id}/activate`, {})
      .pipe(map((r) => r.data));
  }

  createImfAdmin(
    imfId: number,
    payload: CreateImfAdminPayload,
  ): Observable<ImfRecord> {
    return this.http
      .post<ApiResponse<ImfRecord>>(`${this.API}/imf/${imfId}/admin`, payload)
      .pipe(map((r) => r.data));
  }

  // ── Supervision (SUPER_ADMIN) ──────────────────────────────────────────────

  private readonly SUPERVISION = "/api/platform/supervision";

  getImfSummary(imfId: number): Observable<ImfSummary> {
    return this.http
      .get<ApiResponse<ImfSummary>>(`${this.SUPERVISION}/imf/${imfId}/summary`)
      .pipe(map((r) => r.data));
  }

  getImfUsers(
    imfId: number,
    page = 0,
    size = 20,
  ): Observable<PageResponse<UserResponse>> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http
      .get<
        ApiResponse<PageResponse<UserResponse>>
      >(`${this.SUPERVISION}/imf/${imfId}/users`, { params })
      .pipe(map((r) => r.data));
  }

  getImfAgences(imfId: number): Observable<AgenceSupervision[]> {
    return this.http
      .get<
        ApiResponse<AgenceSupervision[]>
      >(`${this.SUPERVISION}/imf/${imfId}/agences`)
      .pipe(map((r) => r.data));
  }

  getImfAudit(
    imfId: number,
    page = 0,
    size = 50,
  ): Observable<PageResponse<AuditEntry>> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http
      .get<
        ApiResponse<PageResponse<AuditEntry>>
      >(`${this.SUPERVISION}/imf/${imfId}/audit`, { params })
      .pipe(map((r) => r.data));
  }
}

import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse, PageResponse } from "@core/models/api-response.model";
import {
  DossierRecouvrementResponse,
  ActionRecouvrementResponse,
  AccordReechelonnementResponse,
  OuvrirDossierRequest,
  AjouterActionRequest,
  EscaladerDossierRequest,
  AccordReechelonnementRequest,
  RecouvrementPhase,
} from "@core/models/recouvrement.model";

@Injectable({ providedIn: "root" })
export class RecouvrementService {
  private readonly API = "/api/recouvrement";

  constructor(private http: HttpClient) {}

  // ── Dossiers ────────────────────────────────────────────────────────────────

  ouvrirDossier(
    req: OuvrirDossierRequest,
  ): Observable<DossierRecouvrementResponse> {
    return this.http
      .post<
        ApiResponse<DossierRecouvrementResponse>
      >(`${this.API}/dossiers`, req)
      .pipe(map((r) => r.data));
  }

  listDossiers(
    phase?: RecouvrementPhase,
    clos?: boolean,
    page = 0,
    size = 20,
  ): Observable<PageResponse<DossierRecouvrementResponse>> {
    let params = new HttpParams().set("page", page).set("size", size);
    if (phase) params = params.set("phase", phase);
    if (clos != null) params = params.set("clos", String(clos));
    return this.http
      .get<
        ApiResponse<PageResponse<DossierRecouvrementResponse>>
      >(`${this.API}/dossiers`, { params })
      .pipe(map((r) => r.data));
  }

  getDossier(id: number): Observable<DossierRecouvrementResponse> {
    return this.http
      .get<
        ApiResponse<DossierRecouvrementResponse>
      >(`${this.API}/dossiers/${id}`)
      .pipe(map((r) => r.data));
  }

  escalader(
    id: number,
    req: EscaladerDossierRequest,
  ): Observable<DossierRecouvrementResponse> {
    return this.http
      .put<
        ApiResponse<DossierRecouvrementResponse>
      >(`${this.API}/dossiers/${id}/escalader`, req)
      .pipe(map((r) => r.data));
  }

  clore(id: number, motif = ""): Observable<DossierRecouvrementResponse> {
    const params = new HttpParams().set("motif", motif);
    return this.http
      .put<
        ApiResponse<DossierRecouvrementResponse>
      >(`${this.API}/dossiers/${id}/clore`, null, { params })
      .pipe(map((r) => r.data));
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  ajouterAction(
    dossierId: number,
    req: AjouterActionRequest,
  ): Observable<ActionRecouvrementResponse> {
    return this.http
      .post<
        ApiResponse<ActionRecouvrementResponse>
      >(`${this.API}/dossiers/${dossierId}/actions`, req)
      .pipe(map((r) => r.data));
  }

  getActions(dossierId: number): Observable<ActionRecouvrementResponse[]> {
    return this.http
      .get<
        ApiResponse<ActionRecouvrementResponse[]>
      >(`${this.API}/dossiers/${dossierId}/actions`)
      .pipe(map((r) => r.data));
  }

  // ── Accords ─────────────────────────────────────────────────────────────────

  creerAccord(
    dossierId: number,
    req: AccordReechelonnementRequest,
  ): Observable<AccordReechelonnementResponse> {
    return this.http
      .post<
        ApiResponse<AccordReechelonnementResponse>
      >(`${this.API}/dossiers/${dossierId}/accords`, req)
      .pipe(map((r) => r.data));
  }

  getAccords(dossierId: number): Observable<AccordReechelonnementResponse[]> {
    return this.http
      .get<
        ApiResponse<AccordReechelonnementResponse[]>
      >(`${this.API}/dossiers/${dossierId}/accords`)
      .pipe(map((r) => r.data));
  }
}

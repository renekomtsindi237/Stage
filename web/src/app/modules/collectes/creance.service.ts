import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api-response.model";
import { Creance, KpiRecouvrement, ScoreMcrs } from "./models/creance.model";
import { PageResponse } from "./models/collecte.model";

@Injectable({ providedIn: "root" })
export class CreanceService {
  private readonly API = "/api/v1/creances";

  constructor(private http: HttpClient) {}

  lister(
    agenceId?: number,
    categoriePar?: string,
    statut?: string,
    dateDebut?: string,
    dateFin?: string,
    page = 0,
    size = 20,
  ): Observable<PageResponse<Creance>> {
    let params = new HttpParams().set("page", page).set("size", size);
    if (agenceId) params = params.set("agenceId", agenceId);
    if (categoriePar) params = params.set("categoriePar", categoriePar);
    if (statut) params = params.set("statut", statut);
    if (dateDebut) params = params.set("dateDebut", dateDebut);
    if (dateFin) params = params.set("dateFin", dateFin);
    return this.http
      .get<ApiResponse<PageResponse<Creance>>>(this.API, { params })
      .pipe(map((r) => r.data));
  }

  detail(id: number): Observable<Creance> {
    return this.http
      .get<ApiResponse<Creance>>(`${this.API}/${id}`)
      .pipe(map((r) => r.data));
  }

  kpi(agenceId?: number, datePeriode?: string): Observable<KpiRecouvrement> {
    let params = new HttpParams();
    if (agenceId) params = params.set("agenceId", agenceId);
    if (datePeriode) params = params.set("datePeriode", datePeriode);
    return this.http
      .get<ApiResponse<KpiRecouvrement>>(`${this.API}/kpi`, { params })
      .pipe(map((r) => r.data));
  }

  scoreClient(clientId: string): Observable<ScoreMcrs> {
    return this.http
      .get<ApiResponse<ScoreMcrs>>(`${this.API}/client/${clientId}/score-mcrs`)
      .pipe(map((r) => r.data));
  }

  majStatut(
    id: number,
    statut: string,
    observation?: string,
  ): Observable<Creance> {
    let params = new HttpParams().set("statut", statut);
    if (observation) params = params.set("observation", observation);
    return this.http
      .patch<ApiResponse<Creance>>(`${this.API}/${id}/statut`, null, { params })
      .pipe(map((r) => r.data));
  }
}


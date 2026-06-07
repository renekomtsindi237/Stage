import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api-response.model";
import {
  DashboardDirecteur,
  DashboardRecouvrement,
  DashboardAgent,
  ParStat,
  CollecteStat,
  TendancePrixProduit,
  BenchmarkAgence,
  DashboardSummary,
} from "./models/kpi.model";

@Injectable({ providedIn: "root" })
export class KpiService {
  private readonly API_KPI = "/api/kpi";
  private readonly API_CREAN = "/api/creances";
  private readonly API_COLLECT = "/api/collectes-epargne";

  constructor(private http: HttpClient) {}

  // ── Dashboards par rôle ─────────────────────────────────────────────────

  getDashboardDirecteur(agenceId?: number): Observable<DashboardDirecteur> {
    let params = new HttpParams();
    if (agenceId) params = params.set("agenceId", agenceId);
    return this.http
      .get<
        ApiResponse<DashboardDirecteur>
      >(`${this.API_KPI}/dashboard-directeur`, { params })
      .pipe(map((r) => r.data));
  }

  getDashboardRecouvrement(
    agenceId?: number,
  ): Observable<DashboardRecouvrement> {
    let params = new HttpParams();
    if (agenceId) params = params.set("agenceId", agenceId);
    return this.http
      .get<
        ApiResponse<DashboardRecouvrement>
      >(`${this.API_KPI}/dashboard-recouvrement`, { params })
      .pipe(map((r) => r.data));
  }

  getDashboardAgent(): Observable<DashboardAgent> {
    return this.http
      .get<ApiResponse<DashboardAgent>>(`${this.API_KPI}/dashboard-agent`)
      .pipe(map((r) => r.data));
  }

  // ── Séries temporelles ───────────────────────────────────────────────────

  getParStats(
    dateDebut: string,
    dateFin: string,
    agenceId?: number,
  ): Observable<ParStat[]> {
    let params = new HttpParams()
      .set("dateDebut", dateDebut)
      .set("dateFin", dateFin);
    if (agenceId) params = params.set("agenceId", agenceId);
    return this.http
      .get<ApiResponse<ParStat[]>>(`${this.API_KPI}/par-stats`, { params })
      .pipe(map((r) => r.data));
  }

  getCollecteStats(
    dateDebut: string,
    dateFin: string,
    agenceId?: number,
  ): Observable<CollecteStat[]> {
    let params = new HttpParams()
      .set("dateDebut", dateDebut)
      .set("dateFin", dateFin);
    if (agenceId) params = params.set("agenceId", agenceId);
    return this.http
      .get<
        ApiResponse<CollecteStat[]>
      >(`${this.API_KPI}/collecte-stats`, { params })
      .pipe(map((r) => r.data));
  }

  getTendancesPrix(
    codeProduit?: string,
    zoneId?: string,
    jours = 90,
  ): Observable<TendancePrixProduit[]> {
    let params = new HttpParams().set("jours", jours);
    if (codeProduit) params = params.set("codeProduit", codeProduit);
    if (zoneId) params = params.set("zoneId", zoneId);
    return this.http
      .get<
        ApiResponse<TendancePrixProduit[]>
      >(`${this.API_KPI}/tendances-prix`, { params })
      .pipe(map((r) => r.data));
  }

  getBenchmarks(): Observable<BenchmarkAgence[]> {
    return this.http
      .get<ApiResponse<BenchmarkAgence[]>>(`${this.API_KPI}/benchmarks`)
      .pipe(map((r) => r.data));
  }

  // ── Rétrocompatibilité ───────────────────────────────────────────────────

  getDashboardSummary(): Observable<DashboardSummary> {
    return this.http
      .get<ApiResponse<DashboardSummary>>(`${this.API_KPI}/dashboard-summary`)
      .pipe(map((r) => r.data));
  }
}

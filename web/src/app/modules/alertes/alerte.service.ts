import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse, PageResponse } from "@core/models/api-response.model";
import {
  AlerteResponse,
  AlerteUpdateRequest,
  StatutAlerte,
} from "@core/models/alerte.model";

@Injectable({ providedIn: "root" })
export class AlerteService {
  private readonly API = "/api/alertes";

  constructor(private http: HttpClient) {}

  getAlertes(
    statut?: StatutAlerte,
    page = 0,
    size = 20,
  ): Observable<PageResponse<AlerteResponse>> {
    let params = new HttpParams().set("page", page).set("size", size);
    if (statut) params = params.set("statut", statut);
    return this.http
      .get<ApiResponse<PageResponse<AlerteResponse>>>(this.API, { params })
      .pipe(map((r) => r.data));
  }

  getById(id: number): Observable<AlerteResponse> {
    return this.http
      .get<ApiResponse<AlerteResponse>>(`${this.API}/${id}`)
      .pipe(map((r) => r.data));
  }

  updateStatut(
    id: number,
    request: AlerteUpdateRequest,
  ): Observable<AlerteResponse> {
    return this.http
      .put<ApiResponse<AlerteResponse>>(`${this.API}/${id}`, request)
      .pipe(map((r) => r.data));
  }
}

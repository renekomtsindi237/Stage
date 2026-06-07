import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiResponse } from '@core/models/api-response.model';
import {
  CollecteEpargne,
  CollecteEpargneRequest,
  SyncCollectesRequest,
  SyncCollectesResponse,
  KpiJourAgent,
  PageResponse,
} from './models/collecte.model';

@Injectable({ providedIn: 'root' })
export class CollecteEpargneService {

  private readonly API = '/api/collectes-epargne';

  constructor(private http: HttpClient) {}

  soumettre(request: CollecteEpargneRequest): Observable<CollecteEpargne> {
    return this.http.post<ApiResponse<CollecteEpargne>>(this.API, request)
      .pipe(map(r => r.data));
  }

  syncBatch(request: SyncCollectesRequest): Observable<SyncCollectesResponse> {
    return this.http.post<ApiResponse<SyncCollectesResponse>>(`${this.API}/sync`, request)
      .pipe(map(r => r.data));
  }

  lister(
    agenceId?: number,
    agentId?: number,
    dateDebut?: string,
    dateFin?: string,
    statut?: string,
    page = 0,
    size = 20,
  ): Observable<PageResponse<CollecteEpargne>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (agenceId) params = params.set('agenceId', agenceId);
    if (agentId)  params = params.set('agentId', agentId);
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin)   params = params.set('dateFin', dateFin);
    if (statut)    params = params.set('statut', statut);
    return this.http.get<ApiResponse<PageResponse<CollecteEpargne>>>(this.API, { params })
      .pipe(map(r => r.data));
  }

  valider(id: number, motifRejet?: string): Observable<CollecteEpargne> {
    let params = new HttpParams();
    if (motifRejet) params = params.set('motifRejet', motifRejet);
    return this.http.patch<ApiResponse<CollecteEpargne>>(
      `${this.API}/${id}/valider`, null, { params }
    ).pipe(map(r => r.data));
  }

  kpiJour(date?: string): Observable<KpiJourAgent> {
    let params = new HttpParams();
    if (date) params = params.set('date', date);
    return this.http.get<ApiResponse<KpiJourAgent>>(`${this.API}/mon-kpi-jour`, { params })
      .pipe(map(r => r.data));
  }

  collectesNonSynchros(): Observable<CollecteEpargne[]> {
    return this.http.get<ApiResponse<CollecteEpargne[]>>(`${this.API}/non-synchros`)
      .pipe(map(r => r.data));
  }
}

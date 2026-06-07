import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiResponse, PageResponse } from '@core/models/api-response.model';
import { PretResponse, StatutPret } from '@core/models/pret.model';

@Injectable({ providedIn: 'root' })
export class PretService {

  private readonly API = '/api/prets';

  constructor(private http: HttpClient) {}

  listPrets(statut?: StatutPret, page = 0, size = 20): Observable<PageResponse<PretResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (statut) params = params.set('statut', statut);
    return this.http.get<ApiResponse<PageResponse<PretResponse>>>(this.API, { params }).pipe(
      map(r => r.data)
    );
  }

  getById(idPret: string): Observable<PretResponse> {
    return this.http.get<ApiResponse<PretResponse>>(`${this.API}/${idPret}`)
      .pipe(map(r => r.data));
  }

  getPretsClient(idClient: string): Observable<PretResponse[]> {
    return this.http.get<ApiResponse<PretResponse[]>>(`${this.API}/client/${idClient}`)
      .pipe(map(r => r.data));
  }
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  AgentCredit,
  Delegation,
  DeleguerAutoriteRequest,
  ReassignerDossierRequest,
} from '../models/delegation.model';

@Injectable({ providedIn: 'root' })
export class DelegationService {
  private readonly BASE = '/api/v1/delegations';
  private readonly CREDIT = '/api/v1/dossiers-credit';

  constructor(private http: HttpClient) {}

  listDelegations(page = 0, size = 20): Observable<{ content: Delegation[]; totalElements: number }> {
    return this.http
      .get<any>(this.BASE, { params: { page, size } })
      .pipe(map((r) => r.data));
  }

  mesDelegations(): Observable<Delegation[]> {
    return this.http
      .get<any>(`${this.BASE}/mes-delegations`)
      .pipe(map((r) => r.data));
  }

  getAgentsCredit(): Observable<AgentCredit[]> {
    return this.http
      .get<any>(`${this.BASE}/agents-credit`)
      .pipe(map((r) => r.data));
  }

  reassignerDossier(dossierUid: string, req: ReassignerDossierRequest): Observable<Delegation> {
    return this.http
      .patch<any>(`${this.CREDIT}/${dossierUid}/reassigner`, req)
      .pipe(map((r) => r.data));
  }

  deleguerAutorite(req: DeleguerAutoriteRequest): Observable<Delegation> {
    return this.http
      .post<any>(`${this.BASE}/deleguer-autorite`, req)
      .pipe(map((r) => r.data));
  }

  revoquer(uid: string): Observable<void> {
    return this.http
      .delete<any>(`${this.BASE}/${uid}/revoquer`)
      .pipe(map(() => void 0));
  }
}

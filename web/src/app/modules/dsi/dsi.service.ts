import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";

export interface ViolationDonnees {
  id: number;
  typeViolation: string;
  dateDeclaration: string;
  nbPersonnesConcernees: number;
  severite: string;
  statut: string;
  delaiRestantHeures?: number;
}

export interface DemandreDroit {
  id: number;
  typeDroit: string;
  sujet: string;
  dateSubmission: string;
  delaiRestantJours: number;
  statut: string;
}

export interface Consentement {
  id: number;
  utilisateur: string;
  role: string;
  finalite: string;
  dateConsentement: string;
  canal: string;
  statut: "ACCORDE" | "REVOQUE";
}

export interface AuditEntry {
  id: number;
  horodatage: string;
  utilisateur: string;
  role: string;
  action: string;
  entiteType: string;
  entiteId: string;
  resumeChangement: string;
  ipSource: string;
}

export interface ServiceSante {
  nom: string;
  description: string;
  statut: "UP" | "DOWN" | "DEGRADE";
  version?: string;
  uptime?: string;
  metrics: Array<{
    label: string;
    value: string;
    progress?: number;
    alerte?: boolean;
  }>;
}

@Injectable({ providedIn: "root" })
export class DsiService {
  private readonly API = "/api/v1/dsi";

  constructor(private http: HttpClient) {}

  getViolations(): Observable<ViolationDonnees[]> {
    return this.http.get<ViolationDonnees[]>(`${this.API}/violations`);
  }

  declarerViolation(data: any): Observable<any> {
    return this.http.post(`${this.API}/violations`, data);
  }

  getDemandesDroits(): Observable<DemandreDroit[]> {
    return this.http.get<DemandreDroit[]>(`${this.API}/droits`);
  }

  traiterDemande(id: number, statut: string): Observable<any> {
    return this.http.patch(`${this.API}/droits/${id}`, { statut });
  }

  getConsentements(page = 0, size = 20): Observable<any> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http.get<any>(`${this.API}/consentements`, { params });
  }

  revoquerConsentement(id: number): Observable<any> {
    return this.http.delete(`${this.API}/consentements/${id}`);
  }

  getAuditTrail(
    page = 0,
    size = 50,
    search = "",
    action = "",
    entiteType = "",
  ): Observable<any> {
    let params = new HttpParams().set("page", page).set("size", size);
    if (search) params = params.set("search", search);
    if (action) params = params.set("action", action);
    if (entiteType) params = params.set("entiteType", entiteType);
    return this.http.get<any>(`${this.API}/audit`, { params });
  }

  exportAudit(): Observable<Blob> {
    return this.http.get(`${this.API}/audit/export`, { responseType: "blob" });
  }

  getSantesServices(): Observable<ServiceSante[]> {
    return this.http.get<ServiceSante[]>(`${this.API}/monitoring`);
  }

  getConfiguration(): Observable<any> {
    return this.http.get<any>(`${this.API}/configuration`);
  }

  saveConfiguration(data: any): Observable<any> {
    return this.http.put(`${this.API}/configuration`, data);
  }

  uploadLogoImf(file: File): Observable<any> {
    const form = new FormData();
    form.append("logo", file);
    return this.http.post(`${this.API}/logo`, form);
  }
}

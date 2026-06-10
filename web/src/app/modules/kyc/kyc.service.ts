import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse, PageResponse } from "@core/models/api-response.model";
import {
  KycDossierResponse,
  KycDocumentResponse,
  KycVerificationResponse,
  InitierKycRequest,
  SoumettreDocumentKycRequest,
  VerifierKycRequest,
  EvaluerRisqueKycRequest,
  ValiderDocumentKycRequest,
  StatutKyc,
  NiveauKyc,
  NiveauRisque,
} from "@core/models/kyc.model";

@Injectable({ providedIn: "root" })
export class KycService {
  private readonly API = "/api/v1/kyc";

  constructor(private http: HttpClient) {}

  // â”€â”€ Dossiers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  initierDossier(req: InitierKycRequest): Observable<KycDossierResponse> {
    return this.http
      .post<ApiResponse<KycDossierResponse>>(`${this.API}/dossiers`, req)
      .pipe(map((r) => r.data));
  }

  listDossiers(
    statut?: StatutKyc,
    niveau?: NiveauKyc,
    risque?: NiveauRisque,
    page = 0,
    size = 20,
  ): Observable<PageResponse<KycDossierResponse>> {
    let params = new HttpParams().set("page", page).set("size", size);
    if (statut) params = params.set("statut", statut);
    if (niveau) params = params.set("niveau", niveau);
    if (risque) params = params.set("risque", risque);
    return this.http
      .get<
        ApiResponse<PageResponse<KycDossierResponse>>
      >(`${this.API}/dossiers`, { params })
      .pipe(map((r) => r.data));
  }

  getDossier(id: number): Observable<KycDossierResponse> {
    return this.http
      .get<ApiResponse<KycDossierResponse>>(`${this.API}/dossiers/${id}`)
      .pipe(map((r) => r.data));
  }

  evaluerRisque(
    id: number,
    req: EvaluerRisqueKycRequest,
  ): Observable<KycDossierResponse> {
    return this.http
      .put<
        ApiResponse<KycDossierResponse>
      >(`${this.API}/dossiers/${id}/risque`, req)
      .pipe(map((r) => r.data));
  }

  verifier(
    id: number,
    req: VerifierKycRequest,
  ): Observable<KycDossierResponse> {
    return this.http
      .put<
        ApiResponse<KycDossierResponse>
      >(`${this.API}/dossiers/${id}/verifier`, req)
      .pipe(map((r) => r.data));
  }

  getVerifications(id: number): Observable<KycVerificationResponse[]> {
    return this.http
      .get<
        ApiResponse<KycVerificationResponse[]>
      >(`${this.API}/dossiers/${id}/verifications`)
      .pipe(map((r) => r.data));
  }

  // â”€â”€ Documents â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  soumettreDocument(
    dossierId: number,
    req: SoumettreDocumentKycRequest,
  ): Observable<KycDocumentResponse> {
    return this.http
      .post<
        ApiResponse<KycDocumentResponse>
      >(`${this.API}/dossiers/${dossierId}/documents`, req)
      .pipe(map((r) => r.data));
  }

  getDocuments(dossierId: number): Observable<KycDocumentResponse[]> {
    return this.http
      .get<
        ApiResponse<KycDocumentResponse[]>
      >(`${this.API}/dossiers/${dossierId}/documents`)
      .pipe(map((r) => r.data));
  }

  validerDocument(
    documentId: number,
    req: ValiderDocumentKycRequest,
  ): Observable<KycDocumentResponse> {
    return this.http
      .put<
        ApiResponse<KycDocumentResponse>
      >(`${this.API}/documents/${documentId}/valider`, req)
      .pipe(map((r) => r.data));
  }
}


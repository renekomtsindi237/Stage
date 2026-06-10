import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { ApiResponse } from "@core/models/api-response.model";

export interface CreerTicketRequest {
  titre: string;
  description: string;
  categorie: string;
  priorite: string;
  emailContact?: string;
  telephone?: string;
}

export interface TicketSupportDto {
  id: number;
  uid: string;
  titre: string;
  description: string;
  categorie: string;
  priorite: string;
  statut: string;
  auteurUsername: string;
  resolution?: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: "root" })
export class TicketService {
  private readonly API = "/api/v1/tickets";

  constructor(private http: HttpClient) {}

  creer(req: CreerTicketRequest): Observable<ApiResponse<TicketSupportDto>> {
    return this.http.post<ApiResponse<TicketSupportDto>>(this.API, req);
  }

  mesTickets(page = 0, size = 20): Observable<ApiResponse<any>> {
    return this.http.get<ApiResponse<any>>(`${this.API}/mes-tickets`, {
      params: { page, size },
    });
  }
}

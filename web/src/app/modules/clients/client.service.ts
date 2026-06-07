import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse } from "@core/models/api-response.model";
import { ClientResponse } from "@core/models/client.model";

@Injectable({ providedIn: "root" })
export class ClientService {
  private readonly API = "/api/clients";

  constructor(private http: HttpClient) {}

  search(query: string): Observable<ClientResponse[]> {
    const params = new HttpParams().set("q", query);
    return this.http
      .get<ApiResponse<ClientResponse[]>>(`${this.API}/search`, { params })
      .pipe(map((r) => r.data));
  }

  getById(id: string): Observable<ClientResponse> {
    return this.http
      .get<ApiResponse<ClientResponse>>(`${this.API}/${id}`)
      .pipe(map((r) => r.data));
  }

  list(
    page = 0,
    size = 20,
  ): Observable<{ content: ClientResponse[]; total: number }> {
    const params = new HttpParams().set("page", page).set("size", size);
    return this.http.get<ApiResponse<any>>(this.API, { params }).pipe(
      map((r) => ({
        content: r.data.content ?? r.data,
        total: r.data.totalElements ?? r.data.length ?? 0,
      })),
    );
  }
}

import { Injectable, inject } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { environment } from "../../../environments/environment";

@Injectable({ providedIn: "root" })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  private unwrap<T>(res: unknown): T {
    if (res && typeof res === "object" && "success" in res && "data" in res) {
      return (res as { data: T }).data;
    }
    return res as T;
  }

  get<T>(
    path: string,
    params?: Record<string, string | number | boolean | null | undefined>,
  ): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v != null) {
          httpParams = httpParams.set(k, String(v));
        }
      });
    }
    return this.http
      .get<unknown>(`${this.baseUrl}${path}`, { params: httpParams })
      .pipe(map((res) => this.unwrap<T>(res)));
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http
      .post<unknown>(`${this.baseUrl}${path}`, body)
      .pipe(map((res) => this.unwrap<T>(res)));
  }

  put<T>(path: string, body?: unknown): Observable<T> {
    return this.http
      .put<unknown>(`${this.baseUrl}${path}`, body ?? {})
      .pipe(map((res) => this.unwrap<T>(res)));
  }

  delete<T>(path: string): Observable<T> {
    return this.http
      .delete<unknown>(`${this.baseUrl}${path}`)
      .pipe(map((res) => this.unwrap<T>(res)));
  }

  patch<T>(path: string, body: unknown): Observable<T> {
    return this.http
      .patch<unknown>(`${this.baseUrl}${path}`, body)
      .pipe(map((res) => this.unwrap<T>(res)));
  }
}

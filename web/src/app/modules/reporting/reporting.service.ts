import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ReportingService {

  private readonly API = '/api/reporting';

  constructor(private http: HttpClient) {}

  exportCollectesCSV(dateDebut: string, dateFin: string): Observable<Blob> {
    const params = new HttpParams().set('dateDebut', dateDebut).set('dateFin', dateFin);
    return this.http.get(`${this.API}/collectes/csv`, {
      params,
      responseType: 'blob',
    });
  }

  exportCollectesPDF(dateDebut: string, dateFin: string): Observable<Blob> {
    const params = new HttpParams().set('dateDebut', dateDebut).set('dateFin', dateFin);
    return this.http.get(`${this.API}/collectes/pdf`, {
      params,
      responseType: 'blob',
    });
  }

  exportPretsEnRetardCSV(): Observable<Blob> {
    return this.http.get(`${this.API}/prets-en-retard/csv`, { responseType: 'blob' });
  }

  exportPretsEnRetardPDF(): Observable<Blob> {
    return this.http.get(`${this.API}/prets-en-retard/pdf`, { responseType: 'blob' });
  }

  exportKpiRapportPDF(dateDebut: string, dateFin: string): Observable<Blob> {
    const params = new HttpParams().set('dateDebut', dateDebut).set('dateFin', dateFin);
    return this.http.get(`${this.API}/kpi/pdf`, {
      params,
      responseType: 'blob',
    });
  }

  downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}

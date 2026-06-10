import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";

export interface ContainerDocker {
  id: string;
  nom: string;
  image: string;
  statut: "running" | "exited" | "restarting" | "paused";
  uptime: string;
  cpu: number;
  memoire: number;
  memoireMax: number;
  ports: string[];
  restarts: number;
}

export interface VpsMetrics {
  hostname: string;
  os: string;
  cpu: number;
  ram: number;
  ramTotal: number;
  disk: number;
  diskTotal: number;
  loadAvg: number[];
  uptime: string;
  ipPublique: string;
  nbContainersActifs: number;
}

export interface DagRun {
  dagId: string;
  nom: string;
  statut: "success" | "running" | "failed" | "queued" | "skipped";
  debutExecution: string;
  dureeSecondes?: number;
  derniereExecution: string;
  prochaineLancement?: string;
  schedule: string;
  tentative: number;
}

export interface LogEntry {
  id: number;
  timestamp: string;
  niveau: "INFO" | "WARN" | "ERROR" | "CRITICAL" | "DEBUG";
  source: string;
  message: string;
  contexte?: string;
}

export interface AlerteSysteme {
  id: number;
  timestamp: string;
  type: string;
  titre: string;
  detail: string;
  severite: "INFO" | "WARN" | "CRITIQUE";
  statut: "ACTIVE" | "RESOLUE" | "EN_COURS";
  source: string;
}

@Injectable({ providedIn: "root" })
export class SupportService {
  private readonly API = "/api/v1/support";

  constructor(private http: HttpClient) {}

  getContainersDocker(): Observable<ContainerDocker[]> {
    return this.http.get<ContainerDocker[]>(`${this.API}/docker/containers`);
  }

  getVpsMetrics(): Observable<VpsMetrics> {
    return this.http.get<VpsMetrics>(`${this.API}/vps/metrics`);
  }

  getDagRuns(): Observable<DagRun[]> {
    return this.http.get<DagRun[]>(`${this.API}/airflow/dags`);
  }

  triggerDag(dagId: string): Observable<any> {
    return this.http.post(`${this.API}/airflow/dags/${dagId}/trigger`, {});
  }

  getJournaux(
    page = 0,
    size = 100,
    niveau = "",
    source = "",
    search = "",
  ): Observable<any> {
    let params = new HttpParams().set("page", page).set("size", size);
    if (niveau) params = params.set("niveau", niveau);
    if (source) params = params.set("source", source);
    if (search) params = params.set("search", search);
    return this.http.get<any>(`${this.API}/logs`, { params });
  }

  getAlertes(): Observable<AlerteSysteme[]> {
    return this.http.get<AlerteSysteme[]>(`${this.API}/alertes`);
  }

  acquitterAlerte(id: number): Observable<any> {
    return this.http.patch(`${this.API}/alertes/${id}/acquitter`, {});
  }

  getSystemOverview(): Observable<any> {
    return this.http.get<any>(`${this.API}/overview`);
  }
}

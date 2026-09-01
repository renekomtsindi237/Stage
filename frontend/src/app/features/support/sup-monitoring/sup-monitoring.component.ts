import {
  Component,
  inject,
  signal,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ApiService } from "../../../core/http/api.service";
import { TranslatePipe } from "@ngx-translate/core";
import { ToastService } from "../../../core/services/toast.service";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";
import { SseService } from "../../../core/services/sse.service";
import { Subscription } from "rxjs";

interface OverviewData {
  nbImfs: number;
  containersActifs: number;
  containersTotal: number;
  cpuVps: number;
  disqueUtilise: number;
  dagsEchoues: number;
  utilisateursConnectes: number;
  alertesCritiques: number;
  alertesActives: number;
  ticketsOuverts?: number;
  couches: { nom: string; statut: string; latenceMs: string }[];
}

interface VpsMetrics {
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

interface Container {
  id: string;
  nom: string;
  image: string;
  statut: string;
  uptime: string;
  cpuPct: number;
  ramMo: number;
  ports: string;
}

interface DagInfo {
  dagId: string;
  nom: string;
  statut: string;
  dernierExecution: string;
  dureeSecondes: number;
  schedule: string;
  tentative: number;
}

interface LogEntry {
  id: string;
  timestamp: string;
  niveau: string;
  source: string;
  message: string;
  utilisateur: string;
  ipClient: string;
}

@Component({
  selector: "app-sup-monitoring",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe, StatutLabelPipe],
  templateUrl: "./sup-monitoring.component.html",
  styleUrls: ["./sup-monitoring.component.scss"],
})
export class SupMonitoringComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly sse = inject(SseService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = signal(true);
  overview = signal<OverviewData | null>(null);
  vps = signal<VpsMetrics | null>(null);
  containers = signal<Container[]>([]);
  dags = signal<DagInfo[]>([]);
  logs = signal<LogEntry[]>([]);
  triggeringDag = signal<string | null>(null);
  lastUpdate = signal<Date>(new Date());
  sseConnected = signal(false);

  private sseSub?: Subscription;
  private logRefreshTimer?: ReturnType<typeof setInterval>;

  readonly grafanaUrl = `http://${window.location.hostname}:3000`;
  readonly prometheusUrl = `http://${window.location.hostname}:9100`;

  ngOnInit() {
    this.loadAll();
    this.subscribeToSse();
    // Logs rafraîchis toutes les 60s (moins critique)
    this.logRefreshTimer = setInterval(() => this.loadLogs(), 60_000);
  }

  ngOnDestroy() {
    this.sseSub?.unsubscribe();
    clearInterval(this.logRefreshTimer);
  }

  private subscribeToSse() {
    this.sseSub = this.sse.events$.subscribe((event) => {
      if (event.type === "MONITORING_UPDATE" && event.payload) {
        const p = event.payload as Record<string, unknown>;
        this.sseConnected.set(true);
        this.lastUpdate.set(new Date());
        // Patch partiel de l'overview avec les données fraîches du serveur
        const cur = this.overview();
        if (cur) {
          this.overview.set({
            ...cur,
            nbImfs: (p["nbImfs"] as number) ?? cur.nbImfs,
            alertesCritiques:
              (p["alertesCritiques"] as number) ?? cur.alertesCritiques,
            utilisateursConnectes:
              (p["utilisateursConnectes"] as number) ??
              cur.utilisateursConnectes,
            ticketsOuverts:
              (p["ticketsOuverts"] as number) ?? cur.ticketsOuverts,
          });
          this.cdr.markForCheck();
        }
      }
    });
  }

  loadAll() {
    this.loading.set(true);
    let pending = 4;
    const done = () => {
      pending--;
      if (pending <= 0) {
        this.loading.set(false);
        this.lastUpdate.set(new Date());
        this.cdr.markForCheck();
      }
    };

    this.api.get<OverviewData>("/api/v1/support/overview").subscribe({
      next: (d) => {
        this.overview.set(d);
        done();
      },
      error: () => done(),
    });

    this.api.get<VpsMetrics>("/api/v1/support/vps/metrics").subscribe({
      next: (d) => {
        this.vps.set(d);
        done();
      },
      error: () => done(),
    });

    this.api.get<Container[]>("/api/v1/support/docker/containers").subscribe({
      next: (d) => {
        this.containers.set(d ?? []);
        done();
      },
      error: () => done(),
    });

    this.api.get<DagInfo[]>("/api/v1/support/airflow/dags").subscribe({
      next: (d) => {
        this.dags.set(d ?? []);
        done();
      },
      error: () => done(),
    });

    this.loadLogs();
  }

  private loadLogs() {
    this.api
      .get<{ content: LogEntry[] }>("/api/v1/support/logs", { size: 20 })
      .subscribe({
        next: (d) => {
          this.logs.set(d?.content ?? (d as unknown as LogEntry[]) ?? []);
          this.cdr.markForCheck();
        },
        error: () => {},
      });
  }

  triggerDag(dagId: string) {
    this.triggeringDag.set(dagId);
    this.api
      .post(`/api/v1/support/airflow/dags/${dagId}/trigger`, {})
      .subscribe({
        next: () => {
          this.triggeringDag.set(null);
          this.toast.showI18nSuccess(
            "sup_monitoring.toast_dag_title",
            "sup_monitoring.toast_dag_body",
            { id: dagId },
          );
          this.loadAll();
        },
        error: (err: unknown) => {
          this.triggeringDag.set(null);
          this.toast.showApiError(err, "sup_monitoring.toast_dag_error");
        },
      });
  }

  cpuColor(pct: number) {
    if (pct >= 90) return "#ef4444";
    if (pct >= 70) return "#f59e0b";
    return "#22c55e";
  }

  statutDagClass(s: string) {
    return (
      {
        success: "badge-success",
        running: "badge-info",
        failed: "badge-danger",
        queued: "badge-warning",
      }[s] ?? "badge-secondary"
    );
  }

  coucheClass(s: string) {
    return s === "OK" ? "dot-up" : s === "WARN" ? "dot-warn" : "dot-down";
  }

  logNiveauClass(n: string) {
    return (
      {
        ERROR: "log-error",
        WARN: "log-warn",
        INFO: "log-info",
        DEBUG: "log-debug",
      }[n] ?? "log-info"
    );
  }

  containerStatutClass(s: string) {
    return s === "running"
      ? "badge-success"
      : s === "paused"
        ? "badge-warning"
        : "badge-danger";
  }
}

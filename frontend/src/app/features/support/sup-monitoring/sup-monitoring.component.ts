import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ApiService } from "../../../core/http/api.service";

interface ServiceHealth {
  name: string;
  status: "UP" | "DOWN" | "DEGRADED";
  responseTimeMs?: number;
  details?: string;
}

interface ActuatorHealth {
  status: string;
  components?: Record<string, { status: string }>;
}

@Component({
  selector: "app-sup-monitoring",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./sup-monitoring.component.html",
  styleUrls: ["./sup-monitoring.component.scss"],
})
export class SupMonitoringComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = signal(true);
  services = signal<ServiceHealth[]>([]);

  readonly grafanaUrl = "http://localhost:3000";
  readonly prometheusUrl = "http://localhost:9100";

  ngOnInit() {
    this.loadHealth();
  }

  loadHealth() {
    this.loading.set(true);
    this.api.get<ActuatorHealth>("/actuator/health").subscribe({
      next: (h: ActuatorHealth) => {
        const rows: ServiceHealth[] = [
          { name: "Backend API", status: h.status === "UP" ? "UP" : "DOWN" },
          ...(h.components
            ? Object.entries(h.components).map(([k, v]) => ({
                name: k,
                status: (v.status === "UP"
                  ? "UP"
                  : "DEGRADED") as ServiceHealth["status"],
              }))
            : []),
        ];
        this.services.set(rows);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.services.set([
          { name: "Backend API", status: "DOWN", details: "Inaccessible" },
        ]);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
    });
  }

  statusClass(s: string) {
    return {
      "badge-success": s === "UP",
      "badge-warning": s === "DEGRADED",
      "badge-danger": s === "DOWN",
    };
  }
}

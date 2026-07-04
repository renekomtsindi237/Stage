import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { PipelineStatus, DagStatus } from "../../../core/models/analyste.model";

@Component({
  selector: "app-anl-pipeline",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./anl-pipeline.component.html",
  styleUrls: ["./anl-pipeline.component.scss"],
})
export class AnlPipelineComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data = signal<PipelineStatus | null>(null);
  forcing = signal(false);

  ngOnInit() {
    this.load();
  }

  load() {
    this.api.get<PipelineStatus>("/api/v1/analyste/pipeline/status").subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  forceExecution() {
    this.forcing.set(true);
    this.api.post("/api/v1/analyste/pipeline/trigger", {}).subscribe({
      next: () => {
        this.forcing.set(false);
        this.load();
      },
      error: () => this.forcing.set(false),
    });
  }

  statutClass(s: string): string {
    const map: Record<string, string> = {
      SUCCESS: "success",
      RUNNING: "running",
      FAILED: "danger",
      PENDING: "basse",
    };
    return map[s] ?? "basse";
  }

  statutLabel(s: string): string {
    const map: Record<string, string> = {
      SUCCESS: "anl_pipeline.statut_success",
      RUNNING: "anl_pipeline.statut_running",
      FAILED: "anl_pipeline.statut_failed",
      PENDING: "anl_pipeline.statut_pending",
    };
    return map[s] ?? s;
  }

  dagIcon(s: string): string {
    const map: Record<string, string> = {
      SUCCESS: "check_circle",
      RUNNING: "autorenew",
      FAILED: "cancel",
      PENDING: "schedule",
    };
    return map[s] ?? "circle";
  }

  formatLines(n?: number): string {
    if (!n) return "—";
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 1000) return `${Math.round(n / 1000)}K`;
    return String(n);
  }
}

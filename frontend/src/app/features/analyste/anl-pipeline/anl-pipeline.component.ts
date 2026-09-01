import {
  Component,
  inject,
  signal,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { AppDatePipe } from "../../../shared/pipes/app-date.pipe";
import { EscCloseDirective } from "../../../shared/directives/esc-close.directive";
import {
  PipelineStatus,
  PipelineRun,
  PipelineTriggerResult,
} from "../../../core/models/analyste.model";

@Component({
  selector: "app-anl-pipeline",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe, AppDatePipe, EscCloseDirective],
  templateUrl: "./anl-pipeline.component.html",
  styleUrls: ["./anl-pipeline.component.scss"],
})
export class AnlPipelineComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data = signal<PipelineStatus | null>(null);
  forcing = signal(false);
  modalOpen = signal(false);
  run = signal<PipelineRun | null>(null);

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit() {
    this.load();
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  load() {
    this.api.get<PipelineStatus>("/api/v1/analyste/pipeline/status").subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.run) {
          this.run.set(d.run);
          if (d.run.statut === "RUNNING" && !this.pollTimer) {
            this.modalOpen.set(true);
            this.startPolling();
          }
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  forceExecution() {
    this.forcing.set(true);
    this.modalOpen.set(true);
    this.api
      .post<PipelineTriggerResult>("/api/v1/analyste/pipeline/trigger", {})
      .subscribe({
        next: (res) => {
          this.forcing.set(false);
          if (res?.run) {
            this.run.set(res.run);
          }
          this.startPolling();
          this.load();
        },
        error: () => {
          this.forcing.set(false);
          this.startPolling();
        },
      });
  }

  closeModal() {
    if (this.run()?.statut === "RUNNING") return;
    this.modalOpen.set(false);
  }

  progressPct(): number {
    const r = this.run();
    if (!r || !r.etapesTotal) return 0;
    const done = r.etapes.filter((s) => s.statut === "SUCCESS").length;
    const running = r.etapes.some((s) => s.statut === "RUNNING") ? 0.45 : 0;
    return Math.min(100, Math.round(((done + running) / r.etapesTotal) * 100));
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

  formatLines(n?: number | null): string {
    if (n == null || Number.isNaN(n)) return "—";
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)} M`;
    if (n >= 1000) return `${Math.round(n / 1000)} k`;
    return String(n);
  }

  private startPolling() {
    this.stopPolling();
    this.pollTimer = setInterval(() => this.pollStatus(), 1200);
  }

  private stopPolling() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private pollStatus() {
    this.api.get<PipelineStatus>("/api/v1/analyste/pipeline/status").subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.run) {
          this.run.set(d.run);
          if (d.run.statut !== "RUNNING") {
            this.stopPolling();
            this.forcing.set(false);
          }
        }
      },
    });
  }
}

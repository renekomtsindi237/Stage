import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { BaseChartDirective } from "ng2-charts";
import { ChartConfiguration } from "chart.js";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { PipelineStatus, MlDrift } from "../../../core/models/analyste.model";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";

@Component({
  selector: "app-dir-analytics",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, BaseChartDirective, TranslatePipe, StatutLabelPipe],
  templateUrl: "./dir-analytics.component.html",
  styleUrls: ["./dir-analytics.component.scss"],
})
export class DirAnalyticsComponent implements OnInit {
  private readonly api = inject(ApiService);

  loadingPipeline = signal(true);
  loadingDrift = signal(true);
  pipeline = signal<PipelineStatus | null>(null);
  drift = signal<MlDrift | null>(null);
  triggering = signal(false);

  psiChartData: ChartConfiguration<"line">["data"] = {
    labels: [],
    datasets: [],
  };
  psiChartOptions: ChartConfiguration<"line">["options"] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { min: 0, grid: { color: "#f1f5f9" } },
      x: { grid: { display: false }, ticks: { maxTicksLimit: 10 } },
    },
  };

  ngOnInit() {
    this.loadPipeline();
    this.loadDrift();
  }

  loadPipeline() {
    this.api.get<PipelineStatus>("/api/v1/analyste/pipeline/status").subscribe({
      next: (d) => {
        this.pipeline.set(d);
        this.loadingPipeline.set(false);
      },
      error: () => this.loadingPipeline.set(false),
    });
  }

  loadDrift() {
    this.api.get<MlDrift>("/api/v1/analyste/ml/drift").subscribe({
      next: (d: MlDrift) => {
        this.drift.set(d);
        this.buildChart(d);
        this.loadingDrift.set(false);
      },
      error: () => this.loadingDrift.set(false),
    });
  }

  triggerPipeline() {
    this.triggering.set(true);
    this.api.post("/api/v1/analyste/pipeline/trigger", {}).subscribe({
      next: () => {
        this.triggering.set(false);
        this.loadPipeline();
      },
      error: () => this.triggering.set(false),
    });
  }

  private buildChart(d: MlDrift) {
    const evo = d.evolutionPsi ?? [];
    this.psiChartData = {
      labels: evo.map((e) => e.date.slice(5)),
      datasets: [
        {
          label: "PSI",
          data: evo.map((e) => e.psi),
          borderColor: "#1b2f4b",
          backgroundColor: "rgba(27,47,75,.08)",
          fill: true,
          tension: 0.5,
          pointRadius: 2,
        },
      ],
    };
  }

  get psiClass(): string {
    const psi = this.drift()?.psiActuel ?? 0;
    if (psi > 0.25) return "critical";
    if (psi > 0.2) return "high";
    if (psi > 0.1) return "medium";
    return "ok";
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

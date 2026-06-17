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
import { ApiService } from "../../../core/http/api.service";
import { MlDrift } from "../../../core/models/analyste.model";

@Component({
  selector: "app-anl-drift",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: "./anl-drift.component.html",
  styleUrls: ["./anl-drift.component.scss"],
})
export class AnlDriftComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data = signal<MlDrift | null>(null);

  psiChartData: ChartConfiguration<"line">["data"] = {
    labels: [],
    datasets: [],
  };
  psiChartOptions: ChartConfiguration<"line">["options"] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: {
        min: 0,
        ticks: { callback: (v) => String(v) },
        grid: { color: "#f1f5f9" },
      },
      x: { grid: { display: false }, ticks: { maxTicksLimit: 12 } },
    },
  };

  ngOnInit() {
    this.api.get<MlDrift>("/api/v1/analyste/ml/drift").subscribe({
      next: (d: MlDrift) => {
        this.data.set(d);
        this.buildChart(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
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
    const psi = this.data()?.psiActuel ?? 0;
    if (psi > 0.25) return "critical";
    if (psi > 0.2) return "high";
    if (psi > 0.1) return "medium";
    return "ok";
  }

  maxContrib(): number {
    return Math.max(
      ...(this.data()?.contributionFeatures ?? []).map(
        (f: { nom: string; contribution: number }) => f.contribution,
      ),
      1,
    );
  }
}

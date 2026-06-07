import {
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
  ElementRef,
  OnDestroy,
} from "@angular/core";
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
  Filler,
} from "chart.js";
import { ParStat } from "../../models/kpi.model";

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
  Filler,
);

@Component({
  selector: "imf-par-chart",
  template: `
    <div class="chart-wrapper">
      <canvas #chartCanvas></canvas>
      <p class="no-data" *ngIf="!parStats || parStats.length === 0">
        Aucune donnée disponible
      </p>
    </div>
  `,
  styles: [
    `
      .chart-wrapper {
        position: relative;
        height: 280px;
      }
      .no-data {
        text-align: center;
        color: #999;
        padding: 60px 0;
      }
    `,
  ],
})
export class ParChartComponent implements OnChanges, OnDestroy {
  @Input() parStats: ParStat[] = [];
  @ViewChild("chartCanvas", { static: true })
  canvasRef!: ElementRef<HTMLCanvasElement>;

  private chart?: Chart;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes["parStats"]) {
      this.buildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private buildChart(): void {
    if (!this.parStats || this.parStats.length === 0) return;

    // Agréger par date : somme des encours_par30 / encours_par90
    const byDate = new Map<string, { par30: number; par90: number }>();
    for (const s of this.parStats) {
      const date = s.dateValeur?.substring(0, 10) ?? "";
      const existing = byDate.get(date) ?? { par30: 0, par90: 0 };
      existing.par30 += s.encoursPar30 ?? 0;
      existing.par90 += s.encoursPar90 ?? 0;
      byDate.set(date, existing);
    }

    const sortedDates = [...byDate.keys()].sort();
    const par30Data = sortedDates.map((d) => byDate.get(d)!.par30 / 1_000_000);
    const par90Data = sortedDates.map((d) => byDate.get(d)!.par90 / 1_000_000);

    this.chart?.destroy();
    this.chart = new Chart(this.canvasRef.nativeElement, {
      type: "line",
      data: {
        labels: sortedDates,
        datasets: [
          {
            label: "PAR30 (MFCFA)",
            data: par30Data,
            borderColor: "#ff9800",
            backgroundColor: "rgba(255, 152, 0, 0.1)",
            fill: true,
            tension: 0.3,
            pointRadius: 3,
          },
          {
            label: "PAR90 (MFCFA)",
            data: par90Data,
            borderColor: "#f44336",
            backgroundColor: "rgba(244, 67, 54, 0.1)",
            fill: true,
            tension: 0.3,
            pointRadius: 3,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: "top" },
          tooltip: {
            callbacks: {
              label: (ctx) =>
                `${ctx.dataset.label}: ${ctx.parsed.y?.toFixed(2) ?? "0"} MFCFA`,
            },
          },
        },
        scales: {
          y: { beginAtZero: true, title: { display: true, text: "MFCFA" } },
          x: { title: { display: true, text: "Date" } },
        },
      },
    });
  }
}

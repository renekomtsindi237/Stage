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
  BarController,
  BarElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
} from "chart.js";

Chart.register(
  BarController,
  BarElement,
  LinearScale,
  CategoryScale,
  Tooltip,
  Legend,
);

export interface StackedBarDataset {
  label: string;
  data: number[];
  color?: string;
}

@Component({
  selector: "imf-stacked-bar-chart",
  template: `
    <div class="chart-wrapper">
      <canvas #chartCanvas></canvas>
      <p class="no-data" *ngIf="!datasets || datasets.length === 0">
        Aucune donnée disponible
      </p>
    </div>
  `,
  styles: [
    `
      .chart-wrapper {
        position: relative;
        height: 100%;
        min-height: 280px;
      }
      .no-data {
        text-align: center;
        color: #999;
        padding: 60px 0;
      }
    `,
  ],
})
export class StackedBarChartComponent implements OnChanges, OnDestroy {
  @Input() labels: string[] = [];
  @Input() datasets: StackedBarDataset[] = [];

  @ViewChild("chartCanvas", { static: true })
  canvasRef!: ElementRef<HTMLCanvasElement>;

  private chart?: Chart;

  private readonly defaultColors = [
    "#1B4F8A",
    "#2563EB",
    "#10B981",
    "#F59E0B",
    "#EF4444",
    "#8B5CF6",
    "#EC4899",
    "#14B8A6",
    "#F97316",
    "#6366F1",
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes["datasets"] || changes["labels"]) {
      this.buildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private buildChart(): void {
    if (!this.datasets || this.datasets.length === 0) return;

    const chartDatasets = this.datasets.map((ds, i) => ({
      label: ds.label,
      data: ds.data,
      backgroundColor:
        ds.color || this.defaultColors[i % this.defaultColors.length],
      borderRadius: 4,
      borderSkipped: false,
    }));

    this.chart?.destroy();
    this.chart = new Chart(this.canvasRef.nativeElement, {
      type: "bar",
      data: {
        labels: this.labels,
        datasets: chartDatasets,
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: "top",
            labels: {
              padding: 15,
              font: { size: 12, weight: 600 },
              usePointStyle: true,
              pointStyle: "circle",
            },
          },
          tooltip: {
            mode: "index",
            intersect: false,
            callbacks: {
              label: (ctx) =>
                `${ctx.dataset.label}: ${(ctx.parsed.y ?? 0).toLocaleString("fr-FR")}`,
            },
          },
        },
        scales: {
          x: {
            stacked: true,
            grid: { display: false },
          },
          y: {
            stacked: true,
            beginAtZero: true,
            grid: { color: "rgba(0,0,0,0.05)" },
          },
        },
      },
    });
  }
}

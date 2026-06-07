import { Component, Input, OnChanges, SimpleChanges, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import { Chart, DoughnutController, ArcElement, Tooltip, Legend } from 'chart.js';

Chart.register(DoughnutController, ArcElement, Tooltip, Legend);

export interface DoughnutChartData {
  label: string;
  value: number;
  color?: string;
}

@Component({
  selector: 'imf-doughnut-chart',
  template: `
    <div class="chart-wrapper">
      <canvas #chartCanvas></canvas>
      <div class="center-text" *ngIf="centerValue">
        <div class="center-value">{{ centerValue }}</div>
        <div class="center-label">{{ centerLabel }}</div>
      </div>
      <p class="no-data" *ngIf="!data || data.length === 0">Aucune donnée disponible</p>
    </div>
  `,
  styles: [`
    .chart-wrapper { 
      position: relative; 
      height: 100%; 
      min-height: 250px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .no-data { text-align: center; color: #999; padding: 60px 0; }
    canvas { max-height: 280px; }
    .center-text {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;
      pointer-events: none;
    }
    .center-value {
      font-size: 2rem;
      font-weight: 900;
      color: var(--color-text-primary);
      line-height: 1;
    }
    .center-label {
      font-size: 0.85rem;
      color: var(--color-text-muted);
      margin-top: 4px;
      font-weight: 600;
    }
  `]
})
export class DoughnutChartComponent implements OnChanges, OnDestroy {

  @Input() data: DoughnutChartData[] = [];
  @Input() centerValue: string = '';
  @Input() centerLabel: string = '';

  @ViewChild('chartCanvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  private chart?: Chart;

  private readonly defaultColors = [
    '#1B4F8A', '#2563EB', '#10B981', '#F59E0B', '#EF4444',
    '#8B5CF6', '#EC4899', '#14B8A6', '#F97316', '#6366F1'
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['data']) {
      this.buildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private buildChart(): void {
    if (!this.data || this.data.length === 0) return;

    const labels = this.data.map(d => d.label);
    const values = this.data.map(d => d.value);
    const colors = this.data.map((d, i) => d.color || this.defaultColors[i % this.defaultColors.length]);

    this.chart?.destroy();
    this.chart = new Chart(this.canvasRef.nativeElement, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data: values,
          backgroundColor: colors,
          borderColor: '#fff',
          borderWidth: 3,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '70%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              padding: 15,
              font: { size: 12, weight: 600 },
              usePointStyle: true,
              pointStyle: 'circle'
            }
          },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const total = values.reduce((a, b) => a + b, 0);
                const percentage = ((ctx.parsed / total) * 100).toFixed(1);
                return `${ctx.label}: ${ctx.parsed.toLocaleString('fr-FR')} (${percentage}%)`;
              }
            }
          }
        }
      }
    });
  }
}

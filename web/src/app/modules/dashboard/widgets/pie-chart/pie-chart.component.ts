import { Component, Input, OnChanges, SimpleChanges, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import { Chart, PieController, ArcElement, Tooltip, Legend } from 'chart.js';

Chart.register(PieController, ArcElement, Tooltip, Legend);

export interface PieChartData {
  label: string;
  value: number;
  color?: string;
}

@Component({
  selector: 'imf-pie-chart',
  template: `
    <div class="chart-wrapper">
      <canvas #chartCanvas></canvas>
      <p class="no-data" *ngIf="!data || data.length === 0">Aucune donnée disponible</p>
    </div>
  `,
  styles: [`
    .chart-wrapper { position: relative; height: 100%; min-height: 250px; }
    .no-data { text-align: center; color: #999; padding: 60px 0; }
    canvas { max-height: 280px; }
  `]
})
export class PieChartComponent implements OnChanges, OnDestroy {

  @Input() data: PieChartData[] = [];
  @Input() title: string = '';

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
      type: 'pie',
      data: {
        labels,
        datasets: [{
          data: values,
          backgroundColor: colors,
          borderColor: '#fff',
          borderWidth: 2,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
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

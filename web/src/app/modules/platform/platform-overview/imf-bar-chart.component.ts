import { Component, Input, OnChanges, ViewChild, ElementRef, OnDestroy, NgZone } from '@angular/core';
import { Chart, BarController, BarElement, LinearScale, CategoryScale, Tooltip, Legend } from 'chart.js';

Chart.register(BarController, BarElement, LinearScale, CategoryScale, Tooltip, Legend);

@Component({
  selector: 'imf-platform-bar-chart',
  template: `<div class="cw"><canvas #c></canvas></div>`,
  styles: [`.cw { position:relative; height:220px; width:100%; }`]
})
export class PlatformBarChartComponent implements OnChanges, OnDestroy {
  @Input() labels: string[] = [];
  @Input() activeData: number[] = [];
  @Input() inactiveData: number[] = [];
  @ViewChild('c', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;
  private chart?: Chart;

  constructor(private ngZone: NgZone) {}

  ngOnChanges(): void { this.build(); }
  ngOnDestroy(): void { this.chart?.destroy(); }

  private build(): void {
    if (!this.labels.length) return;
    this.chart?.destroy();
    this.ngZone.runOutsideAngular(() => {
    this.chart = new Chart(this.canvasRef.nativeElement, {
      type: 'bar',
      data: {
        labels: this.labels,
        datasets: [
          {
            label: 'Actives',
            data: this.activeData,
            backgroundColor: '#0066FF',
            borderRadius: 6,
            borderSkipped: false,
            barPercentage: 0.55,
          },
          {
            label: 'Inactives',
            data: this.inactiveData,
            backgroundColor: '#E5E7EB',
            borderRadius: 6,
            borderSkipped: false,
            barPercentage: 0.55,
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#fff',
            titleColor: '#111827',
            bodyColor: '#374151',
            borderColor: '#E5E7EB',
            borderWidth: 1,
            padding: 10,
            callbacks: {
              label: (ctx) => ` ${ctx.dataset.label}: ${ctx.parsed.y}`
            }
          }
        },
        scales: {
          x: {
            grid: { display: false },
            border: { display: false },
            ticks: { color: '#9CA3AF', font: { size: 11 } }
          },
          y: {
            grid: { color: '#F3F4F6' },
            border: { display: false, dash: [4, 4] },
            ticks: {
              color: '#9CA3AF',
              font: { size: 11 },
              stepSize: 1,
              callback: (v) => Number.isInteger(v) ? v : ''
            },
            beginAtZero: true
          }
        }
      }
    });
    }); // runOutsideAngular
  }
}

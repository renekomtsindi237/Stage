import { Component, Input, OnChanges, SimpleChanges, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import { Chart, BarController, BarElement, LinearScale, CategoryScale, Tooltip, Legend } from 'chart.js';
import { CollecteStat } from '../../models/kpi.model';

Chart.register(BarController, BarElement, LinearScale, CategoryScale, Tooltip, Legend);

const CANAL_COLORS: Record<string, string> = {
  ESPECES: '#4caf50',
  MTN_MOBILE_MONEY: '#ffeb3b',
  ORANGE_MONEY: '#ff9800',
  VIREMENT: '#2196f3',
  CHEQUE: '#9c27b0',
};

@Component({
  selector: 'imf-collecte-chart',
  template: `
    <div class="chart-wrapper">
      <canvas #chartCanvas></canvas>
      <p class="no-data" *ngIf="!collecteStats || collecteStats.length === 0">Aucune donnée disponible</p>
    </div>
  `,
  styles: [`
    .chart-wrapper { position: relative; height: 280px; }
    .no-data { text-align: center; color: #999; padding: 60px 0; }
  `]
})
export class CollecteChartComponent implements OnChanges, OnDestroy {

  @Input() collecteStats: CollecteStat[] = [];
  @ViewChild('chartCanvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  private chart?: Chart;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['collecteStats']) {
      this.buildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private buildChart(): void {
    if (!this.collecteStats || this.collecteStats.length === 0) return;

    // Agréger par canal
    const byCanal = new Map<string, number>();
    for (const s of this.collecteStats) {
      const total = (byCanal.get(s.canal) ?? 0) + (s.montantTotal ?? 0);
      byCanal.set(s.canal, total);
    }

    const canaux = [...byCanal.keys()];
    const montants = canaux.map(c => byCanal.get(c)! / 1_000_000);
    const colors = canaux.map(c => CANAL_COLORS[c] ?? '#90a4ae');

    this.chart?.destroy();
    this.chart = new Chart(this.canvasRef.nativeElement, {
      type: 'bar',
      data: {
        labels: canaux,
        datasets: [{
          label: 'Volume (MFCFA)',
          data: montants,
          backgroundColor: colors,
          borderColor: colors.map(c => c),
          borderWidth: 1,
          borderRadius: 6,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => `${ctx.parsed.y?.toFixed(2) ?? '0'} MFCFA`
            }
          }
        },
        scales: {
          y: { beginAtZero: true, title: { display: true, text: 'MFCFA' } },
          x: { title: { display: true, text: 'Canal de paiement' } }
        }
      }
    });
  }
}

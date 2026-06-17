import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { ApiService } from '../../../core/http/api.service';
import { KpiPortefeuille } from '../../../core/models/kpi.model';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';

@Component({
  selector: 'app-dir-kpi',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, BaseChartDirective, StatCardComponent, FcfaPipe],
  templateUrl: './dir-kpi.component.html',
  styleUrls: ['./dir-kpi.component.scss']
})
export class DirKpiComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data    = signal<KpiPortefeuille | null>(null);

  parChartData: ChartConfiguration<'line'>['data'] = { labels: [], datasets: [] };
  parChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom' } },
    scales: {
      y: { ticks: { callback: v => `${v}%` }, grid: { color: '#f1f5f9' } },
      x: { grid: { display: false } }
    }
  };

  ngOnInit() {
    this.api.get<KpiPortefeuille>('/api/v1/kpi/portefeuille').subscribe({
      next: d => { this.data.set(d); this.buildChart(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  private buildChart(d: KpiPortefeuille) {
    const evo = d.evolutionPar ?? [];
    this.parChartData = {
      labels: evo.map(e => e.date.slice(5)),
      datasets: [
        { label: 'PAR 30', data: evo.map(e => e.par30), borderColor: '#3b82f6', backgroundColor: 'rgba(59,130,246,.08)', fill: true, tension: .4 },
        { label: 'PAR 90', data: evo.map(e => e.par90), borderColor: '#ef4444', backgroundColor: 'rgba(239,68,68,.05)', fill: true, tension: .4 },
        { label: 'Objectif', data: evo.map(e => e.objectif), borderColor: '#22c55e', borderDash: [4, 4], tension: .4, fill: false },
      ]
    };
  }

  parClass(val: number): string {
    if (val >= 5) return 'danger';
    if (val >= 3) return 'warn';
    return 'ok';
  }
}

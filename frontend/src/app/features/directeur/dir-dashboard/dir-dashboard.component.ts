import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { ApiService } from '../../../core/http/api.service';
import { KpiDashboard } from '../../../core/models/kpi.model';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { AlertBadgeComponent } from '../../../shared/components/alert-badge/alert-badge.component';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'app-dir-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, BaseChartDirective, StatCardComponent, AlertBadgeComponent, FcfaPipe, TimeAgoPipe],
  templateUrl: './dir-dashboard.component.html',
  styleUrls: ['./dir-dashboard.component.scss']
})
export class DirDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data    = signal<KpiDashboard | null>(null);

  parChartData: ChartConfiguration<'line'>['data'] = {
    labels: [],
    datasets: [
      {
        label: 'PAR 30',
        data: [],
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59,130,246,.1)',
        fill: true,
        tension: .4,
        pointRadius: 3,
      },
      {
        label: 'PAR 90',
        data: [],
        borderColor: '#ef4444',
        backgroundColor: 'rgba(239,68,68,.06)',
        fill: true,
        tension: .4,
        pointRadius: 3,
      },
    ]
  };

  parChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, font: { size: 11 } } } },
    scales: {
      y: { ticks: { callback: v => `${v}%` }, grid: { color: '#f1f5f9' } },
      x: { grid: { display: false } }
    }
  };

  ngOnInit() {
    this.api.get<KpiDashboard>('/api/v1/kpi/dashboard').subscribe({
      next: d => {
        this.data.set(d);
        this.buildChart(d);
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); }
    });
  }

  private buildChart(d: KpiDashboard) {
    const evo = d.evolutionPar30j ?? [];
    this.parChartData = {
      ...this.parChartData,
      labels: evo.map(e => e.date.slice(5)),
      datasets: [
        { ...this.parChartData.datasets[0], data: evo.map(e => e.par30) },
        { ...this.parChartData.datasets[1], data: evo.map(e => e.par90) },
      ]
    };
  }

  activityIcon(type: string): string {
    const map: Record<string, string> = {
      COLLECTE: 'payments', ALERTE: 'warning', KYC: 'verified_user', DOSSIER: 'folder'
    };
    return map[type] ?? 'circle';
  }
}

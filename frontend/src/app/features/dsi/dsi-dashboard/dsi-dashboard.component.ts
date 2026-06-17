import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/http/api.service';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';

interface DsiDashboardData {
  utilisateursActifs: number;
  alertesSysteme: number;
  rgpdScore: number;
  dernierAudit: string;
  violationsOuvertes: number;
  demandesDroits: number;
}

@Component({
  selector: 'app-dsi-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent],
  templateUrl: './dsi-dashboard.component.html',
  styleUrls: ['./dsi-dashboard.component.scss']
})
export class DsiDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  loading = signal(true);
  data    = signal<DsiDashboardData | null>(null);

  ngOnInit() {
    this.api.get<DsiDashboardData>('/api/v1/dsi/dashboard').subscribe({
      next: (d: DsiDashboardData) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}

import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/http/api.service';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { AlertBadgeComponent } from '../../../shared/components/alert-badge/alert-badge.component';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';

interface CreanceRow {
  id: string;
  clientNom: string;
  montant: number;
  joursRetard: number;
  statut: string;
  phase: string;
}

interface RecDashboard {
  creancesActives: number;
  montantEnRetard: number;
  actionsDuMois: number;
  tauxRecouvrement: number;
  creances: CreanceRow[];
}

@Component({
  selector: 'app-rec-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent, AlertBadgeComponent, FcfaPipe],
  templateUrl: './rec-dashboard.component.html',
  styleUrls: ['./rec-dashboard.component.scss']
})
export class RecDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  loading = signal(true);
  data    = signal<RecDashboard | null>(null);

  ngOnInit() {
    this.api.get<RecDashboard>('/api/v1/recouvrement/dashboard').subscribe({
      next: (d: RecDashboard) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  phaseClass(phase: string): string {
    const m: Record<string, string> = {
      AMIABLE: 'badge-basse', JUDICIAIRE: 'badge-haute', CONTENTIEUX: 'badge-critique'
    };
    return m[phase] ?? 'badge-moyenne';
  }
}

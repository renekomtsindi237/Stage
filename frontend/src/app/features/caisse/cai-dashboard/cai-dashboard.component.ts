import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/http/api.service';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';

interface OperationCaisse {
  id: string;
  type: 'ENCAISSEMENT' | 'DECAISSEMENT';
  clientNom: string;
  montant: number;
  canal: string;
  createdAt: string;
}

interface CaiDashboard {
  encaissementsJour: number;
  decaissementsJour: number;
  soldeNet: number;
  operations: OperationCaisse[];
}

@Component({
  selector: 'app-cai-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent, FcfaPipe],
  templateUrl: './cai-dashboard.component.html',
  styleUrls: ['./cai-dashboard.component.scss']
})
export class CaiDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  loading = signal(true);
  data    = signal<CaiDashboard | null>(null);

  get today() {
    return new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }

  ngOnInit() {
    this.api.get<CaiDashboard>('/api/v1/caisse/dashboard').subscribe({
      next: (d: CaiDashboard) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}

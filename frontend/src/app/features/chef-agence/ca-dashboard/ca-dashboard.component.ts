import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../../core/http/api.service';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { ToastService } from '../../../core/services/toast.service';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';

interface DossierPendant {
  uid: string;
  reference: string;
  clientNom: string;
  montant: number;
  agentNom: string;
  dateDepot: string;
}

interface CaDashboard {
  agentsCount: number;
  clientsCount: number;
  collectesJour: number;
  par30: number;
  dossiers: DossierPendant[];
}

@Component({
  selector: 'app-ca-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, StatCardComponent, FcfaPipe],
  templateUrl: './ca-dashboard.component.html',
  styleUrls: ['./ca-dashboard.component.scss']
})
export class CaDashboardComponent implements OnInit {
  private readonly api   = inject(ApiService);
  private readonly toast = inject(ToastService);

  loading   = signal(true);
  data      = signal<CaDashboard | null>(null);
  validating = signal<string | null>(null);

  ngOnInit() {
    this.api.get<CaDashboard>('/api/v1/chef-agence/dashboard').subscribe({
      next: (d: CaDashboard) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  valider(uid: string, decision: 'VALIDE' | 'REJETE') {
    this.validating.set(uid);
    this.api.patch(`/api/v1/dossiers-credit/${uid}/valider-chef`, { decision, commentaire: '' }).subscribe({
      next: () => {
        this.toast.showSuccess('Décision enregistrée', `Dossier ${decision === 'VALIDE' ? 'validé' : 'rejeté'}`);
        this.validating.set(null);
        this.data.update(d => d ? { ...d, dossiers: d.dossiers.filter(dos => dos.uid !== uid) } : d);
      },
      error: () => { this.toast.showError('Erreur', 'Impossible de traiter le dossier.'); this.validating.set(null); }
    });
  }
}

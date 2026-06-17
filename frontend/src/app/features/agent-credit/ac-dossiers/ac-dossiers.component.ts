import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../../core/http/api.service';

interface DossierCredit {
  uid: string;
  numeroReference: string;
  clientNom: string;
  clientPrenom: string;
  montant: number;
  duree: number;
  objectif: string;
  statut: 'BROUILLON'|'SOUMIS'|'EN_ETUDE'|'VALIDE_CHEF'|'VALIDE_DIRECTEUR'|'REFUSE'|'DECAISSE';
  createdAt: string;
  scoreCredit?: number;
}

interface DossierPage { content: DossierCredit[]; totalElements: number; totalPages: number; number: number; }

@Component({
  selector: 'app-ac-dossiers',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterModule],
  templateUrl: './ac-dossiers.component.html',
  styleUrls: ['./ac-dossiers.component.scss']
})
export class AcDossiersComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading     = signal(true);
  page        = signal<DossierPage | null>(null);
  currentPage = signal(0);
  activeTab   = signal<string>('TOUS');

  readonly tabs = ['TOUS','BROUILLON','SOUMIS','EN_ETUDE','VALIDE_CHEF','VALIDE_DIRECTEUR','DECAISSE','REFUSE'];

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    const statut = this.activeTab() === 'TOUS' ? undefined : this.activeTab();
    this.api.get<DossierPage>('/api/v1/credit/dossiers', { page: this.currentPage(), size: 20, statut }).subscribe({
      next: (p: DossierPage) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  switchTab(t: string) { this.activeTab.set(t); this.currentPage.set(0); this.load(); }
  goPage(n: number) { this.currentPage.set(n); this.load(); }

  statutClass(s: string) {
    const map: Record<string,string> = {
      'BROUILLON': 'badge-secondary', 'SOUMIS': 'badge-info', 'EN_ETUDE': 'badge-warning',
      'VALIDE_CHEF': 'badge-primary', 'VALIDE_DIRECTEUR': 'badge-primary',
      'DECAISSE': 'badge-success', 'REFUSE': 'badge-danger'
    };
    return map[s] ?? '';
  }

  scoreClass(score?: number) {
    if (!score) return '';
    if (score >= 700) return 'score-good';
    if (score >= 500) return 'score-medium';
    return 'score-bad';
  }
}

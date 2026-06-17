import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ApiService } from '../../../core/http/api.service';
import { ToastService } from '../../../core/services/toast.service';

interface Contrat {
  uid: string;
  numeroContrat: string;
  clientNom: string;
  clientPrenom: string;
  typeContrat: 'CREDIT' | 'EPARGNE' | 'ASSURANCE';
  montant: number;
  dateDebut: string;
  dateFin: string;
  statut: 'BROUILLON' | 'SOUMIS' | 'VALIDE' | 'REJETE' | 'ACTIF' | 'CLOS';
  observations?: string;
}

interface ContratPage { content: Contrat[]; totalElements: number; totalPages: number; number: number; }

@Component({
  selector: 'app-sai-contrats',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './sai-contrats.component.html',
  styleUrls: ['./sai-contrats.component.scss']
})
export class SaiContratsComponent implements OnInit {
  private readonly api   = inject(ApiService);
  private readonly fb    = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading     = signal(true);
  page        = signal<ContratPage | null>(null);
  currentPage = signal(0);
  showModal   = signal(false);
  submitting  = signal(false);
  activeTab   = signal<string>('BROUILLON');

  createForm = this.fb.group({
    clientId:     ['', Validators.required],
    typeContrat:  ['CREDIT', Validators.required],
    montant:      [0, [Validators.required, Validators.min(1)]],
    dateDebut:    ['', Validators.required],
    dateFin:      ['', Validators.required],
    observations: [''],
  });

  readonly tabs  = ['BROUILLON','SOUMIS','VALIDE','ACTIF','REJETE','TOUS'];
  readonly types = ['CREDIT','EPARGNE','ASSURANCE'];

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    const statut = this.activeTab() === 'TOUS' ? undefined : this.activeTab();
    this.api.get<ContratPage>('/api/v1/contrats', { page: this.currentPage(), size: 20, statut }).subscribe({
      next: (p: ContratPage) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  submit() {
    if (this.createForm.invalid) return;
    this.submitting.set(true);
    this.api.post('/api/v1/contrats', this.createForm.value).subscribe({
      next: () => {
        this.submitting.set(false); this.showModal.set(false);
        this.toast.showSuccess('Contrat créé', 'Brouillon enregistré avec succès');
        this.createForm.reset({ typeContrat: 'CREDIT', montant: 0 }); this.load();
      },
      error: () => { this.submitting.set(false); this.toast.showError('Erreur', 'Impossible de créer le contrat.'); }
    });
  }

  soumettre(uid: string) {
    this.api.patch(`/api/v1/contrats/${uid}/soumettre`, {}).subscribe({
      next: () => { this.toast.showSuccess('Soumis', 'Contrat soumis pour validation.'); this.load(); },
      error: () => this.toast.showError('Erreur', 'Soumission impossible.')
    });
  }

  switchTab(tab: string) { this.activeTab.set(tab); this.currentPage.set(0); this.load(); }
  goPage(n: number) { this.currentPage.set(n); this.load(); }

  statutClass(s: string) {
    return {
      'BROUILLON': 'badge-secondary', 'SOUMIS': 'badge-info', 'VALIDE': 'badge-primary',
      'ACTIF': 'badge-success', 'REJETE': 'badge-danger', 'CLOS': 'badge-warning'
    }[s] ?? '';
  }
}

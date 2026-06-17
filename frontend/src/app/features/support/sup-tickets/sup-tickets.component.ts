import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ApiService } from '../../../core/http/api.service';
import { ToastService } from '../../../core/services/toast.service';

export interface Ticket {
  id: number;
  titre: string;
  description: string;
  priorite: 'BASSE' | 'NORMALE' | 'HAUTE' | 'CRITIQUE';
  statut: 'OUVERT' | 'EN_COURS' | 'RESOLU' | 'FERME';
  categorie: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
}

interface TicketPage { content: Ticket[]; totalElements: number; totalPages: number; number: number; }

@Component({
  selector: 'app-sup-tickets',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './sup-tickets.component.html',
  styleUrls: ['./sup-tickets.component.scss']
})
export class SupTicketsComponent implements OnInit {
  private readonly api   = inject(ApiService);
  private readonly fb    = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading     = signal(true);
  page        = signal<TicketPage | null>(null);
  currentPage = signal(0);
  showModal   = signal(false);
  submitting  = signal(false);
  activeTab   = signal<string>('OUVERT');
  selected    = signal<Ticket | null>(null);

  createForm = this.fb.group({
    titre:      ['', Validators.required],
    description:['', Validators.required],
    priorite:   ['NORMALE', Validators.required],
    categorie:  ['TECHNIQUE', Validators.required],
  });

  readonly tabs       = ['OUVERT','EN_COURS','RESOLU','FERME','TOUS'];
  readonly priorites  = ['BASSE','NORMALE','HAUTE','CRITIQUE'];
  readonly categories = ['TECHNIQUE','FONCTIONNEL','SECURITE','PERFORMANCE','AUTRE'];

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    const statut = this.activeTab() === 'TOUS' ? undefined : this.activeTab();
    this.api.get<TicketPage>('/api/v1/support/tickets', { page: this.currentPage(), size: 20, statut }).subscribe({
      next: (p: TicketPage) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  create() {
    if (this.createForm.invalid) return;
    this.submitting.set(true);
    this.api.post<Ticket>('/api/v1/support/tickets', this.createForm.value).subscribe({
      next: () => {
        this.submitting.set(false); this.showModal.set(false);
        this.toast.showSuccess('Ticket créé', this.createForm.value.titre ?? '');
        this.createForm.reset({ priorite: 'NORMALE', categorie: 'TECHNIQUE' });
        this.load();
      },
      error: () => { this.submitting.set(false); this.toast.showError('Erreur', 'Impossible de créer le ticket.'); }
    });
  }

  updateStatut(id: number, statut: string) {
    this.api.patch(`/api/v1/support/tickets/${id}`, { statut }).subscribe({
      next: () => { this.toast.showSuccess('Ticket mis à jour', `Statut: ${statut}`); this.load(); },
      error: () => this.toast.showError('Erreur', 'Mise à jour impossible.')
    });
  }

  switchTab(tab: string) { this.activeTab.set(tab); this.currentPage.set(0); this.load(); }
  goPage(n: number) { this.currentPage.set(n); this.load(); }

  prioriteClass(p: string) {
    return { 'BASSE': 'badge-secondary', 'NORMALE': 'badge-info', 'HAUTE': 'badge-warning', 'CRITIQUE': 'badge-danger' }[p] ?? '';
  }
  statutClass(s: string) {
    return { 'OUVERT': 'badge-danger', 'EN_COURS': 'badge-warning', 'RESOLU': 'badge-success', 'FERME': 'badge-secondary' }[s] ?? '';
  }
}

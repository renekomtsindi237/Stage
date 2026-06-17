import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../core/http/api.service';
import { Alerte, PageResponse } from '../../../core/models/alerte.model';
import { AlertBadgeComponent } from '../../../shared/components/alert-badge/alert-badge.component';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';
import { TimeAgoPipe } from '../../../shared/pipes/time-ago.pipe';

type Tab = 'NON_TRAITEE' | 'EN_TRAITEMENT' | 'TOUTES';

@Component({
  selector: 'app-dir-alertes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, AlertBadgeComponent, FcfaPipe, TimeAgoPipe],
  templateUrl: './dir-alertes.component.html',
  styleUrls: ['./dir-alertes.component.scss']
})
export class DirAlertesComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading   = signal(true);
  alertes   = signal<Alerte[]>([]);
  total     = signal(0);
  critiques = signal(0);
  hautes    = signal(0);
  moyennes  = signal(0);
  search    = signal('');
  tab       = signal<Tab>('NON_TRAITEE');
  treating  = signal<string | null>(null);

  ngOnInit() { this.load(); }

  setTab(t: Tab) { this.tab.set(t); this.load(); }

  load() {
    this.loading.set(true);
    const statut = this.tab() === 'TOUTES' ? '' : this.tab();
    this.api.get<PageResponse<Alerte>>('/api/v1/alertes', { page: 0, size: 50, ...(statut ? { statut } : {}) })
      .subscribe({
        next: res => {
          this.alertes.set(res.content);
          this.total.set(res.totalElements);
          this.critiques.set(res.content.filter(a => a.severite === 'CRITIQUE').length);
          this.hautes.set(res.content.filter(a => a.severite === 'HAUTE').length);
          this.moyennes.set(res.content.filter(a => a.severite === 'MOYENNE').length);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
  }

  traiter(id: string) {
    this.treating.set(id);
    this.api.put(`/api/v1/alertes/${id}/traiter`).subscribe({
      next: () => { this.treating.set(null); this.load(); },
      error: () => this.treating.set(null)
    });
  }

  get filtered(): Alerte[] {
    const q = this.search().toLowerCase();
    if (!q) return this.alertes();
    return this.alertes().filter(a =>
      a.nomClient.toLowerCase().includes(q) || a.clientId.toLowerCase().includes(q)
    );
  }
}

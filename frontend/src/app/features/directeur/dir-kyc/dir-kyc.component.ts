import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../../core/http/api.service';
import { Kyc } from '../../../core/models/client.model';

interface KycPage { content: Kyc[]; totalElements: number; totalPages: number; number: number; }

@Component({
  selector: 'app-dir-kyc',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './dir-kyc.component.html',
  styleUrls: ['./dir-kyc.component.scss']
})
export class DirKycComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  page    = signal<KycPage | null>(null);
  tab     = signal<'EN_ATTENTE' | 'EXPIRE' | 'TOUS'>('EN_ATTENTE');

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    const statut = this.tab() === 'TOUS' ? undefined : this.tab();
    this.api.get<KycPage>('/api/v1/kyc', { statut, size: 20 }).subscribe({
      next: (p: KycPage) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  setTab(t: 'EN_ATTENTE' | 'EXPIRE' | 'TOUS') { this.tab.set(t); this.load(); }

  statutClass(s: string) {
    const m: Record<string, string> = {
      VALIDE: 'badge-basse', EN_ATTENTE: 'badge-moyenne', REFUSE: 'badge-critique', EXPIRE: 'badge-haute'
    };
    return m[s] ?? '';
  }
}

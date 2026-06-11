import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-back-office-list',
  templateUrl: './back-office-list.component.html',
})
export class BackOfficeListComponent implements OnInit {
  operations: any[] = [];
  loading = true;
  page = 0;
  size = 20;
  total = 0;

  readonly role = this.auth.getRole() ?? '';
  readonly isCaissier = ['CAISSIER', 'CHEF_AGENCE', 'DIRECTEUR', 'DSI'].includes(this.role);

  readonly columns = ['id', 'type', 'montant', 'reference', 'dateOperation'];

  // ── Décaissement ──────────────────────────────────────────────────────────
  showDecModal = false;
  decLoading = false;
  decSuccess = false;
  dec = { montantNet: null as number | null, mode: 'ESPECES', referencePaiement: '', pretId: '' };

  // ── Encaissement ──────────────────────────────────────────────────────────
  showEncModal = false;
  encLoading = false;
  encSuccess = false;
  enc = { montant: null as number | null, mode: 'ESPECES', referencePaiement: '', pretId: '' };

  readonly modeOptions = [
    { value: 'ESPECES',       label: 'Espèces' },
    { value: 'MOBILE_MONEY',  label: 'Mobile Money' },
    { value: 'VIREMENT',      label: 'Virement bancaire' },
    { value: 'CHEQUE',        label: 'Chèque' },
  ];

  constructor(
    private http: HttpClient,
    private auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.http
      .get<any>('/api/v1/caisse/journal', { params: { page: this.page, size: this.size } })
      .subscribe({
        next: (r) => {
          this.operations = r.data?.content ?? [];
          this.total = r.data?.totalElements ?? 0;
          this.loading = false;
        },
        error: () => { this.loading = false; },
      });
  }

  onPageChange(p: number): void {
    this.page = p;
    this.load();
  }

  // ── Décaissement ──────────────────────────────────────────────────────────

  ouvrirDecModal(): void {
    this.dec = { montantNet: null, mode: 'ESPECES', referencePaiement: '', pretId: '' };
    this.decSuccess = false;
    this.showDecModal = true;
  }

  soumettreDec(): void {
    if (!this.dec.montantNet) return;
    this.decLoading = true;
    const body = {
      montantNet: this.dec.montantNet,
      mode: this.dec.mode,
      referencePaiement: this.dec.referencePaiement || undefined,
      pretId: this.dec.pretId || undefined,
    };
    this.http.post<any>('/api/v1/caisse/decaissements', body).subscribe({
      next: () => {
        this.decSuccess = true;
        this.decLoading = false;
        setTimeout(() => { this.showDecModal = false; this.load(); }, 1200);
      },
      error: () => { this.decLoading = false; },
    });
  }

  // ── Encaissement ──────────────────────────────────────────────────────────

  ouvrirEncModal(): void {
    this.enc = { montant: null, mode: 'ESPECES', referencePaiement: '', pretId: '' };
    this.encSuccess = false;
    this.showEncModal = true;
  }

  soumettreEnc(): void {
    if (!this.enc.montant) return;
    this.encLoading = true;
    const body = {
      montant: this.enc.montant,
      mode: this.enc.mode,
      referencePaiement: this.enc.referencePaiement || undefined,
      pretId: this.enc.pretId || undefined,
    };
    this.http.post<any>('/api/v1/caisse/encaissements', body).subscribe({
      next: () => {
        this.encSuccess = true;
        this.encLoading = false;
        setTimeout(() => { this.showEncModal = false; this.load(); }, 1200);
      },
      error: () => { this.encLoading = false; },
    });
  }
}

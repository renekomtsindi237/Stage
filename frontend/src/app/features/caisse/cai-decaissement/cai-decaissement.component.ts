import { Component, inject, signal, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ApiService } from '../../../core/http/api.service';
import { ToastService } from '../../../core/services/toast.service';

interface DossierItem { uid: string; numeroReference: string; clientNom: string; montant: number; }

@Component({
  selector: 'app-cai-decaissement',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './cai-decaissement.component.html',
  styleUrls: ['./cai-decaissement.component.scss']
})
export class CaiDecaissementComponent implements OnInit {
  private readonly api   = inject(ApiService);
  private readonly fb    = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly cdr   = inject(ChangeDetectorRef);

  submitting      = signal(false);
  dossierFound    = signal<DossierItem | null>(null);
  searchLoading   = signal(false);
  confirmed       = signal(false);

  form = this.fb.group({
    reference:    ['', Validators.required],
    canal:        ['ESPECES', Validators.required],
    observations: [''],
  });

  readonly canaux = ['ESPECES','MOBILE_MONEY','VIREMENT','CHEQUE'];

  ngOnInit() {}

  searchDossier() {
    const ref = this.form.value.reference?.trim();
    if (!ref) return;
    this.searchLoading.set(true);
    this.dossierFound.set(null);
    this.confirmed.set(false);
    this.api.get<DossierItem>(`/api/v1/dossiers-credit/reference/${ref}`).subscribe({
      next: (d: DossierItem) => { this.dossierFound.set(d); this.searchLoading.set(false); this.cdr.markForCheck(); },
      error: () => {
        this.toast.showError('Dossier introuvable', `Référence: ${ref}`);
        this.searchLoading.set(false); this.cdr.markForCheck();
      }
    });
  }

  submit() {
    const dossier = this.dossierFound();
    if (!dossier || this.form.invalid) return;
    this.submitting.set(true);
    this.api.post('/api/v1/caisse/decaissements', {
      dossierUid: dossier.uid,
      canal: this.form.value.canal,
      observations: this.form.value.observations,
    }).subscribe({
      next: () => {
        this.submitting.set(false); this.confirmed.set(true);
        this.toast.showSuccess('Décaissement effectué', `${dossier.montant.toLocaleString()} FCFA versés`);
        this.cdr.markForCheck();
      },
      error: () => { this.submitting.set(false); this.toast.showError('Erreur', 'Décaissement impossible.'); }
    });
  }

  reset() { this.form.reset({ canal: 'ESPECES' }); this.dossierFound.set(null); this.confirmed.set(false); }
}

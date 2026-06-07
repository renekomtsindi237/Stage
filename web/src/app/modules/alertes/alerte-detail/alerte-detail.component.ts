import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlerteService } from '../alerte.service';
import { AlerteResponse, AlerteUpdateRequest, StatutAlerte } from '@core/models/alerte.model';
import { AuthService } from '@core/services/auth.service';

@Component({
  selector: 'imf-alerte-detail',
  templateUrl: './alerte-detail.component.html',
  styleUrls: ['./alerte-detail.component.scss']
})
export class AlerteDetailComponent implements OnInit {

  alerte: AlerteResponse | null = null;
  loading = false;
  saving = false;
  error = '';

  readonly peutModifier = this.authService.hasRole('RESPONSABLE_RECOUVREMENT', 'DSI');

  readonly statutOptions: StatutAlerte[] = ['ACTIVE', 'ESCALADEE', 'CLOTUREE'];

  constructor(
    private route: ActivatedRoute,
    private alerteService: AlerteService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading = true;
    this.alerteService.getById(id).subscribe({
      next: (a) => { this.alerte = a; this.loading = false; },
      error: () => { this.error = 'Alerte introuvable.'; this.loading = false; }
    });
  }

  updateStatut(statut: StatutAlerte): void {
    if (!this.alerte || this.saving) return;
    this.saving = true;
    const req: AlerteUpdateRequest = { statut };
    this.alerteService.updateStatut(this.alerte.id, req).subscribe({
      next: (updated) => { this.alerte = updated; this.saving = false; },
      error: () => { this.error = 'Mise à jour échouée.'; this.saving = false; }
    });
  }
}

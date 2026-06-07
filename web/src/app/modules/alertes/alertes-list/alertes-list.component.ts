import { Component, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { AlerteService } from '../alerte.service';
import { AlerteResponse, StatutAlerte } from '@core/models/alerte.model';

@Component({
  selector: 'imf-alertes-list',
  templateUrl: './alertes-list.component.html',
  styleUrls: ['./alertes-list.component.scss']
})
export class AlertesListComponent implements OnInit {

  alertes: AlerteResponse[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  error = '';
  statutFiltre: StatutAlerte | undefined;

  readonly statutOptions: Array<{ value: StatutAlerte | undefined; label: string }> = [
    { value: undefined,    label: 'Toutes' },
    { value: 'ACTIVE',     label: 'Actives' },
    { value: 'ESCALADEE',  label: 'Escaladées' },
    { value: 'CLOTUREE',   label: 'Clôturées' },
  ];

  readonly displayedColumns = ['idPret', 'joursRetard', 'montantEnRetard', 'statutAlerte', 'dateGeneration', 'actions'];

  constructor(private alerteService: AlerteService) {}

  ngOnInit(): void { this.loadAlertes(); }

  loadAlertes(): void {
    this.loading = true;
    this.error = '';
    this.alerteService.getAlertes(this.statutFiltre, this.page, this.pageSize).subscribe({
      next: (data) => {
        this.alertes = data.content;
        this.total = data.totalElements;
        this.loading = false;
      },
      error: () => {
        this.error = 'error';
        this.loading = false;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadAlertes();
  }

  onFiltreChange(): void {
    this.page = 0;
    this.loadAlertes();
  }

  getStatutClass(statut: StatutAlerte): string {
    const map: Record<string, string> = {
      ACTIVE: 'active', ESCALADEE: 'escaladee', CLOTUREE: 'cloturee',
    };
    return map[statut] ?? '';
  }

  getStatutLabel(statut: StatutAlerte): string {
    const map: Record<string, string> = {
      ACTIVE: 'Active', ESCALADEE: 'Escaladée', CLOTUREE: 'Clôturée',
    };
    return map[statut] ?? statut;
  }

  // Méthodes pour compter les alertes par statut
  getAlertesActivesCount(): number {
    return this.alertes.filter(a => a.statutAlerte === 'ACTIVE').length;
  }

  getAlertesEscaladeesCount(): number {
    return this.alertes.filter(a => a.statutAlerte === 'ESCALADEE').length;
  }

  getAlertesClotureesCount(): number {
    return this.alertes.filter(a => a.statutAlerte === 'CLOTUREE').length;
  }
}

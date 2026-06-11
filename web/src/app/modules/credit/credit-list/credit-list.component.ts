import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { DelegationService } from '../../../core/services/delegation.service';
import { AgentCredit } from '../../../core/models/delegation.model';

@Component({
  selector: 'app-credit-list',
  templateUrl: './credit-list.component.html',
})
export class CreditListComponent implements OnInit {
  dossiers: any[] = [];
  loading = true;
  statut = '';
  page = 0;
  size = 20;
  total = 0;

  readonly role = this.auth.getRole();
  readonly canReassign = ['CHEF_AGENCE', 'DIRECTEUR', 'DSI', 'SUPER_ADMIN'].includes(this.role ?? '');

  readonly columns = this.canReassign
    ? ['uid', 'clientNom', 'montantDemande', 'dureeMois', 'statut', 'createdAt', 'actions']
    : ['uid', 'clientNom', 'montantDemande', 'dureeMois', 'statut', 'createdAt'];

  // ── État modal réassignation ──────────────────────────────────────────────
  reassignDossier: any = null;
  agents: AgentCredit[] = [];
  agentsLoading = false;
  selectedAgentUid = '';
  reassignMotif = '';
  reassignLoading = false;
  reassignSuccess = false;

  constructor(
    private http: HttpClient,
    private auth: AuthService,
    private delegationSvc: DelegationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    const params: any = { page: this.page, size: this.size };
    if (this.statut) params['statut'] = this.statut;
    this.http
      .get<any>('/api/v1/dossiers-credit', { params })
      .subscribe({
        next: (r) => {
          this.dossiers = r.data?.content ?? [];
          this.total = r.data?.totalElements ?? 0;
          this.loading = false;
        },
        error: () => { this.loading = false; },
      });
  }

  onStatutChange(s: string): void {
    this.statut = s;
    this.page = 0;
    this.load();
  }

  onPageChange(p: number): void {
    this.page = p;
    this.load();
  }

  // ── Réassignation ─────────────────────────────────────────────────────────

  ouvrirReassign(dossier: any): void {
    this.reassignDossier = dossier;
    this.selectedAgentUid = '';
    this.reassignMotif = '';
    this.reassignSuccess = false;
    this.agentsLoading = true;
    this.delegationSvc.getAgentsCredit().subscribe({
      next: (list) => {
        this.agents = list.filter((a) => a.actif);
        this.agentsLoading = false;
      },
      error: () => { this.agentsLoading = false; },
    });
  }

  fermerReassign(): void {
    this.reassignDossier = null;
  }

  soumettrReassign(): void {
    if (!this.selectedAgentUid || !this.reassignDossier) return;
    this.reassignLoading = true;
    this.delegationSvc
      .reassignerDossier(this.reassignDossier.uid, {
        nouvelAgentUid: this.selectedAgentUid,
        motif: this.reassignMotif || undefined,
      })
      .subscribe({
        next: () => {
          this.reassignSuccess = true;
          this.reassignLoading = false;
          setTimeout(() => {
            this.fermerReassign();
            this.load();
          }, 1200);
        },
        error: () => { this.reassignLoading = false; },
      });
  }
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-credit-detail',
  templateUrl: './credit-detail.component.html',
})
export class CreditDetailComponent implements OnInit {
  dossier: any = null;
  loading = true;
  error = '';

  readonly role = this.auth.getRole() ?? '';
  readonly uid = this.route.snapshot.paramMap.get('uid') ?? '';

  // ── Actions ────────────────────────────────────────────────────────────────
  actionLoading = false;
  actionError = '';
  actionSuccess = '';

  // Validation chef d'agence
  showValiderModal = false;
  validerApprouve = true;
  validerMotif = '';

  // ── Comité ─────────────────────────────────────────────────────────────────
  seances: any[] = [];
  loadingSeances = false;
  showOuvrirSeance = false;
  savingSeance = false;

  showVoter = false;
  voteValue: 'POUR' | 'CONTRE' | 'ABSTENTION' = 'POUR';
  voteCommentaire = '';
  savingVote = false;
  activeSeanceUid = '';

  showDecision = false;
  decisionValue: 'APPROUVE' | 'REJETE' | 'AJOURNE' = 'APPROUVE';
  decisionMontantApprouve: number | null = null;
  decisionTaux: number | null = null;
  decisionDuree: number | null = null;
  decisionConditions = '';
  decisionMotifRejet = '';
  savingDecision = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.loadDossier();
  }

  loadDossier(): void {
    this.loading = true;
    this.http.get<any>(`/api/v1/dossiers-credit/${this.uid}`).subscribe({
      next: (r) => {
        this.dossier = r.data;
        this.loading = false;
        if (this.dossier?.statut === 'EN_COMITE') this.loadSeances();
      },
      error: () => {
        this.error = 'Dossier introuvable.';
        this.loading = false;
      },
    });
  }

  // ── Workflow actions ────────────────────────────────────────────────────────

  soumettre(): void {
    this.actionLoading = true;
    this.actionError = '';
    this.http.patch<any>(`/api/v1/dossiers-credit/${this.uid}/soumettre`, {}).subscribe({
      next: (r) => {
        this.dossier = r.data;
        this.actionSuccess = 'Dossier soumis au chef d\'agence.';
        this.actionLoading = false;
      },
      error: (e) => {
        this.actionError = e?.error?.message ?? 'Erreur lors de la soumission.';
        this.actionLoading = false;
      },
    });
  }

  ouvrirValiderModal(approuve: boolean): void {
    this.validerApprouve = approuve;
    this.validerMotif = '';
    this.showValiderModal = true;
  }

  validerChef(): void {
    this.actionLoading = true;
    this.actionError = '';
    this.showValiderModal = false;
    const body = { approuve: this.validerApprouve, motif: this.validerMotif || undefined };
    this.http.patch<any>(`/api/v1/dossiers-credit/${this.uid}/valider-chef`, body).subscribe({
      next: (r) => {
        this.dossier = r.data;
        this.actionSuccess = this.validerApprouve ? 'Dossier validé — envoyé en comité.' : 'Dossier rejeté.';
        this.actionLoading = false;
        if (this.dossier?.statut === 'EN_COMITE') this.loadSeances();
      },
      error: (e) => {
        this.actionError = e?.error?.message ?? 'Erreur lors de la validation.';
        this.actionLoading = false;
      },
    });
  }

  instructionComplete(): void {
    this.actionLoading = true;
    this.actionError = '';
    this.http.patch<any>(`/api/v1/dossiers-credit/${this.uid}/instruction-complete`, {}).subscribe({
      next: (r) => {
        this.dossier = r.data;
        this.actionSuccess = 'Instruction marquée comme complète.';
        this.actionLoading = false;
      },
      error: (e) => {
        this.actionError = e?.error?.message ?? 'Erreur.';
        this.actionLoading = false;
      },
    });
  }

  // ── Comité ─────────────────────────────────────────────────────────────────

  loadSeances(): void {
    this.loadingSeances = true;
    this.http.get<any>(`/api/v1/comite/dossier/${this.uid}/seances`).subscribe({
      next: (r) => { this.seances = r.data ?? []; this.loadingSeances = false; },
      error: () => { this.loadingSeances = false; },
    });
  }

  ouvrirSeance(): void {
    this.savingSeance = true;
    this.http.post<any>(`/api/v1/comite/dossier/${this.uid}/seance`, {}).subscribe({
      next: (r) => {
        if (r.data) this.seances.unshift(r.data);
        this.showOuvrirSeance = false;
        this.savingSeance = false;
      },
      error: (e) => {
        this.actionError = e?.error?.message ?? 'Impossible d\'ouvrir la séance.';
        this.savingSeance = false;
      },
    });
  }

  ouvrirVoter(seanceUid: string): void {
    this.activeSeanceUid = seanceUid;
    this.voteValue = 'POUR';
    this.voteCommentaire = '';
    this.showVoter = true;
  }

  voter(): void {
    this.savingVote = true;
    const body = { vote: this.voteValue, commentaire: this.voteCommentaire || undefined };
    this.http.post<any>(`/api/v1/comite/dossier/${this.uid}/vote`, body).subscribe({
      next: () => {
        this.showVoter = false;
        this.savingVote = false;
        this.actionSuccess = 'Vote enregistré.';
        this.loadSeances();
      },
      error: (e) => {
        this.actionError = e?.error?.message ?? 'Erreur lors du vote.';
        this.savingVote = false;
      },
    });
  }

  ouvrirDecision(): void {
    this.decisionValue = 'APPROUVE';
    this.decisionMontantApprouve = this.dossier?.montantDemande ?? null;
    this.decisionTaux = null;
    this.decisionDuree = this.dossier?.dureeMois ?? null;
    this.decisionConditions = '';
    this.decisionMotifRejet = '';
    this.showDecision = true;
  }

  enregistrerDecision(): void {
    this.savingDecision = true;
    const body: any = {
      decision: this.decisionValue,
      montantApprouve: this.decisionMontantApprouve,
      tauxApprouve: this.decisionTaux,
      dureeApprouvee: this.decisionDuree,
      conditions: this.decisionConditions || undefined,
      motifRejet: this.decisionMotifRejet || undefined,
    };
    this.http.post<any>(`/api/v1/comite/dossier/${this.uid}/decision`, body).subscribe({
      next: (r) => {
        this.dossier = r.data ?? this.dossier;
        this.showDecision = false;
        this.savingDecision = false;
        this.actionSuccess = 'Décision du comité enregistrée.';
        this.loadSeances();
      },
      error: (e) => {
        this.actionError = e?.error?.message ?? 'Erreur lors de l\'enregistrement.';
        this.savingDecision = false;
      },
    });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  get canSoumettre(): boolean {
    return this.role === 'AGENT_CREDIT' && this.dossier?.statut === 'INSTRUCTION';
  }

  get canValiderChef(): boolean {
    return this.role === 'CHEF_AGENCE' && this.dossier?.statut === 'INSTRUCTION';
  }

  get canInstructionComplete(): boolean {
    return this.role === 'ANALYSTE_ENGAGEMENTS' && this.dossier?.statut === 'EN_COMITE';
  }

  get canOuvrirSeance(): boolean {
    return ['CHEF_AGENCE', 'DIRECTEUR', 'DSI'].includes(this.role) && this.dossier?.statut === 'EN_COMITE';
  }

  get canVoter(): boolean {
    return ['CHEF_AGENCE', 'DIRECTEUR', 'ANALYSTE_ENGAGEMENTS'].includes(this.role) && this.dossier?.statut === 'EN_COMITE';
  }

  get canDecision(): boolean {
    return ['CHEF_AGENCE', 'DIRECTEUR'].includes(this.role) && this.dossier?.statut === 'EN_COMITE';
  }

  statutClass(s: string): string {
    const map: Record<string, string> = {
      INSTRUCTION: 'statut-instruction',
      EN_COMITE: 'statut-comite',
      APPROUVE: 'statut-approuve',
      REJETE: 'statut-rejete',
      AJOURNE: 'statut-ajourne',
      DEBLOQUE: 'statut-debloque',
    };
    return map[s] ?? '';
  }

  goBack(): void {
    this.router.navigate(['../'], { relativeTo: this.route });
  }
}

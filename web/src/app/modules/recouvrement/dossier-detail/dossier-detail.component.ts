import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { RecouvrementService } from '../recouvrement.service';
import {
  DossierRecouvrementResponse,
  ActionRecouvrementResponse,
  AccordReechelonnementResponse,
  RecouvrementPhase,
  CategorieCobtac,
  TypeActionRecouvrement,
  StatutVerifMomo,
  CanalPaiement,
  ResultatActionRecouvrement,
  AjouterActionRequest,
  EscaladerDossierRequest,
  AccordReechelonnementRequest,
} from '@core/models/recouvrement.model';

@Component({
  selector: 'imf-dossier-detail',
  templateUrl: './dossier-detail.component.html',
  styleUrls: ['./dossier-detail.component.scss']
})
export class DossierDetailComponent implements OnInit {

  dossier: DossierRecouvrementResponse | null = null;
  actions: ActionRecouvrementResponse[] = [];
  accords: AccordReechelonnementResponse[] = [];

  loading = false;
  loadingActions = false;
  loadingAccords = false;
  error = '';

  showActionForm = false;
  showEscaladeForm = false;
  showAccordForm = false;
  showCloreConfirm = false;

  savingAction = false;
  savingEscalade = false;
  savingAccord = false;
  savingClore = false;

  actionForm!: UntypedFormGroup;
  escaladeForm!: UntypedFormGroup;
  accordForm!: UntypedFormGroup;
  motifClore = '';

  readonly typeActionOptions: Array<{ value: TypeActionRecouvrement; label: string; group: string }> = [
    { value: 'APPEL_TELEPHONIQUE',      label: 'Appel téléphonique',          group: 'Contact' },
    { value: 'SMS_RELANCE',             label: 'SMS de relance',               group: 'Contact' },
    { value: 'EMAIL_RELANCE',           label: 'Email de relance',             group: 'Contact' },
    { value: 'VISITE_TERRAIN',          label: 'Visite terrain',               group: 'Terrain' },
    { value: 'CONTACT_CAUTION',         label: 'Contact caution solidaire',    group: 'Terrain' },
    { value: 'MEDIATION_CHEF_QUARTIER', label: 'Médiation chef de quartier',   group: 'Médiation' },
    { value: 'MEDIATION_FAMILLE',       label: 'Médiation famille',            group: 'Médiation' },
    { value: 'ENCAISSEMENT_PARTIEL',    label: 'Encaissement partiel',         group: 'Paiement' },
    { value: 'ENCAISSEMENT_TOTAL',      label: 'Encaissement total',           group: 'Paiement' },
    { value: 'MISE_EN_DEMEURE_LETTRE',  label: 'Lettre de mise en demeure',    group: 'Judiciaire' },
    { value: 'INTERVENTION_HUISSIER',   label: 'Intervention huissier',        group: 'Judiciaire' },
    { value: 'ASSIGNATION_TRIBUNAL',    label: 'Assignation tribunal (OHADA)', group: 'Judiciaire' },
    { value: 'SAISIE_GARANTIE',         label: 'Saisie garantie',              group: 'Judiciaire' },
    { value: 'COMITE_RECOUVREMENT',     label: 'Comité de recouvrement',       group: 'Interne' },
    { value: 'ACCORD_REECHELONNEMENT',  label: 'Accord de rééchelonnement',    group: 'Interne' },
    { value: 'CESSION_CREANCE',         label: 'Cession de créance',           group: 'Interne' },
    { value: 'RADIATION',               label: 'Radiation',                    group: 'Interne' },
  ];

  readonly phasesEscalade: Array<{ value: RecouvrementPhase; label: string }> = [
    { value: 'MEDIATION_AMIABLE', label: 'Médiation amiable' },
    { value: 'MISE_EN_DEMEURE',   label: 'Mise en demeure (OHADA art. 110)' },
    { value: 'CONTENTIEUX',       label: 'Contentieux' },
    { value: 'REECHELONNEMENT',   label: 'Rééchelonnement' },
    { value: 'PERTE',             label: 'Perte (radiation)' },
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private recouvrementService: RecouvrementService,
    private fb: UntypedFormBuilder
  ) {}

  ngOnInit(): void {
    this.initForms();
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadDossier(id);
  }

  private initForms(): void {
    this.actionForm = this.fb.group({
      typeAction:              ['', Validators.required],
      resultat:                [''],
      observation:             [''],
      promesseDate:            [''],
      promesseMontant:         [null],
      canalPaiement:           [''],
      referenceTransaction:    [''],
      fraisEngages:            [null],
      statutVerifMomo:         [''],
      numeroTelephonePaiement: [''],
    });

    this.escaladeForm = this.fb.group({
      nouvellePhase: ['', Validators.required],
      motif:         [''],
    });

    this.accordForm = this.fb.group({
      nouveauMontantMensuel:     [null, [Validators.required, Validators.min(1)]],
      nombreNouvellesEcheances:  [null, [Validators.required, Validators.min(1)]],
      dateDebutNouvelEcheancier: ['', Validators.required],
      tauxInteretAnnuel:         [null],
      observations:              [''],
    });
  }

  loadDossier(id: number): void {
    this.loading = true;
    this.recouvrementService.getDossier(id).subscribe({
      next: (d) => {
        this.dossier = d;
        this.loading = false;
        this.loadActions(d.id);
        this.loadAccords(d.id);
      },
      error: () => { this.error = 'Dossier introuvable.'; this.loading = false; }
    });
  }

  loadActions(id: number): void {
    this.loadingActions = true;
    this.recouvrementService.getActions(id).subscribe({
      next: (a) => { this.actions = a; this.loadingActions = false; },
      error: () => this.loadingActions = false
    });
  }

  loadAccords(id: number): void {
    this.loadingAccords = true;
    this.recouvrementService.getAccords(id).subscribe({
      next: (a) => { this.accords = a; this.loadingAccords = false; },
      error: () => this.loadingAccords = false
    });
  }

  ajouterAction(): void {
    if (this.actionForm.invalid || !this.dossier) return;
    this.savingAction = true;
    const val = this.actionForm.value;
    const req: AjouterActionRequest = {
      typeAction:              val.typeAction,
      resultat:                val.resultat             || undefined,
      observation:             val.observation          || undefined,
      promesseDate:            val.promesseDate         || undefined,
      promesseMontant:         val.promesseMontant      || undefined,
      canalPaiement:           val.canalPaiement        || undefined,
      referenceTransaction:    val.referenceTransaction || undefined,
      fraisEngages:            val.fraisEngages         || undefined,
      statutVerifMomo:         val.statutVerifMomo      || undefined,
      numeroTelephonePaiement: val.numeroTelephonePaiement || undefined,
    };
    this.recouvrementService.ajouterAction(this.dossier.id, req).subscribe({
      next: (a) => {
        this.actions.unshift(a);
        this.savingAction = false;
        this.showActionForm = false;
        this.actionForm.reset();
        if (this.dossier) this.dossier.fraisRecouvrement += a.fraisEngages ?? 0;
      },
      error: () => this.savingAction = false
    });
  }

  escalader(): void {
    if (this.escaladeForm.invalid || !this.dossier) return;
    this.savingEscalade = true;
    const req: EscaladerDossierRequest = {
      nouvellePhase: this.escaladeForm.value.nouvellePhase,
      motif:         this.escaladeForm.value.motif || undefined,
    };
    this.recouvrementService.escalader(this.dossier.id, req).subscribe({
      next: (d) => {
        this.dossier = d;
        this.savingEscalade = false;
        this.showEscaladeForm = false;
        this.escaladeForm.reset();
      },
      error: () => this.savingEscalade = false
    });
  }

  creerAccord(): void {
    if (this.accordForm.invalid || !this.dossier) return;
    this.savingAccord = true;
    const val = this.accordForm.value;
    const req: AccordReechelonnementRequest = {
      nouveauMontantMensuel:     val.nouveauMontantMensuel,
      nombreNouvellesEcheances:  val.nombreNouvellesEcheances,
      dateDebutNouvelEcheancier: val.dateDebutNouvelEcheancier,
      tauxInteretAnnuel:         val.tauxInteretAnnuel || undefined,
      observations:              val.observations || undefined,
    };
    this.recouvrementService.creerAccord(this.dossier.id, req).subscribe({
      next: (a) => {
        this.accords.unshift(a);
        // Deactivate previous accords in local state
        this.accords.forEach((acc, i) => { if (i > 0) acc.actif = false; });
        if (this.dossier) this.dossier.phase = 'REECHELONNEMENT';
        this.savingAccord = false;
        this.showAccordForm = false;
        this.accordForm.reset();
      },
      error: () => this.savingAccord = false
    });
  }

  cloreDossier(): void {
    if (!this.dossier) return;
    this.savingClore = true;
    this.recouvrementService.clore(this.dossier.id, this.motifClore).subscribe({
      next: (d) => {
        this.dossier = d;
        this.savingClore = false;
        this.showCloreConfirm = false;
      },
      error: () => this.savingClore = false
    });
  }

  isMomoAction(): boolean {
    return ['ENCAISSEMENT_PARTIEL', 'ENCAISSEMENT_TOTAL'].includes(this.actionForm.get('typeAction')?.value);
  }

  getPhaseLabel(phase: RecouvrementPhase): string {
    const map: Record<RecouvrementPhase, string> = {
      RELANCE_AMIABLE:   'Relance amiable',
      MEDIATION_AMIABLE: 'Médiation amiable',
      MISE_EN_DEMEURE:   'Mise en demeure',
      CONTENTIEUX:       'Contentieux',
      REECHELONNEMENT:   'Rééchelonnement',
      PERTE:             'Perte',
    };
    return map[phase] ?? phase;
  }

  getPhaseClass(phase: RecouvrementPhase): string {
    const map: Record<RecouvrementPhase, string> = {
      RELANCE_AMIABLE:   'phase-relance',
      MEDIATION_AMIABLE: 'phase-mediation',
      MISE_EN_DEMEURE:   'phase-demeure',
      CONTENTIEUX:       'phase-contentieux',
      REECHELONNEMENT:   'phase-reechelon',
      PERTE:             'phase-perte',
    };
    return map[phase] ?? '';
  }

  getCobacLabel(cat: CategorieCobtac): string {
    const map: Record<CategorieCobtac, string> = {
      EN_SURVEILLANCE: 'Surveillance (5%)',
      DOUTEUSE:        'Douteuse (25%)',
      LITIGIEUSE:      'Litigieuse (50%)',
      CONTENTIEUSE:    'Contentieuse (100%)',
    };
    return map[cat] ?? cat;
  }

  getCobacClass(cat: CategorieCobtac): string {
    const map: Record<CategorieCobtac, string> = {
      EN_SURVEILLANCE: 'cobac-surveillance',
      DOUTEUSE:        'cobac-douteuse',
      LITIGIEUSE:      'cobac-litigieuse',
      CONTENTIEUSE:    'cobac-contentieuse',
    };
    return map[cat] ?? '';
  }

  getActionLabel(type: TypeActionRecouvrement): string {
    const found = this.typeActionOptions.find(o => o.value === type);
    return found?.label ?? type;
  }

  getActionIcon(type: TypeActionRecouvrement): string {
    const map: Partial<Record<TypeActionRecouvrement, string>> = {
      APPEL_TELEPHONIQUE:      'call',
      SMS_RELANCE:             'sms',
      EMAIL_RELANCE:           'email',
      VISITE_TERRAIN:          'directions_walk',
      CONTACT_CAUTION:         'shield',
      MEDIATION_CHEF_QUARTIER: 'groups',
      MEDIATION_FAMILLE:       'family_restroom',
      ENCAISSEMENT_PARTIEL:    'payments',
      ENCAISSEMENT_TOTAL:      'check_circle',
      MISE_EN_DEMEURE_LETTRE:  'mail',
      INTERVENTION_HUISSIER:   'gavel',
      ASSIGNATION_TRIBUNAL:    'account_balance',
      SAISIE_GARANTIE:         'lock',
      COMITE_RECOUVREMENT:     'meeting_room',
      ACCORD_REECHELONNEMENT:  'handshake',
      CESSION_CREANCE:         'swap_horiz',
      RADIATION:               'cancel',
    };
    return map[type] ?? 'task';
  }

  getMomoStatutClass(statut?: StatutVerifMomo): string {
    if (!statut) return '';
    const map: Record<StatutVerifMomo, string> = {
      EN_ATTENTE: 'momo-attente',
      VERIFIE:    'momo-verifie',
      REJETE:     'momo-rejete',
    };
    return map[statut];
  }

  get dossierId(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }
}

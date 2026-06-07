import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { KycService } from '../kyc.service';
import {
  KycDossierResponse, KycDocumentResponse, KycVerificationResponse,
  StatutKyc, NiveauKyc, NiveauRisque, ResultatVerificationKyc, TypeDocumentKyc,
  VerifierKycRequest, EvaluerRisqueKycRequest, SoumettreDocumentKycRequest,
  ValiderDocumentKycRequest,
} from '@core/models/kyc.model';

@Component({
  selector: 'imf-kyc-detail',
  templateUrl: './kyc-detail.component.html',
  styleUrls: ['./kyc-detail.component.scss']
})
export class KycDetailComponent implements OnInit {

  dossier: KycDossierResponse | null = null;
  documents: KycDocumentResponse[] = [];
  verifications: KycVerificationResponse[] = [];

  loading = false;
  loadingDocs = false;
  loadingVerifs = false;
  error = '';

  showVerifForm = false;
  showRisqueForm = false;
  showDocForm = false;

  savingVerif = false;
  savingRisque = false;
  savingDoc = false;

  verifForm!: UntypedFormGroup;
  risqueForm!: UntypedFormGroup;
  docForm!: UntypedFormGroup;

  readonly niveauOptions: NiveauKyc[] = ['NIVEAU_1', 'NIVEAU_2', 'NIVEAU_3'];

  readonly typeDocOptions: Array<{ value: TypeDocumentKyc; label: string; niveau: number }> = [
    { value: 'CNI_RECTO',               label: 'CNI — recto',                  niveau: 1 },
    { value: 'CNI_VERSO',               label: 'CNI — verso',                  niveau: 1 },
    { value: 'PASSEPORT',               label: 'Passeport',                    niveau: 1 },
    { value: 'PERMIS_CONDUIRE',         label: 'Permis de conduire',           niveau: 1 },
    { value: 'CARTE_SEJOUR',            label: 'Carte de séjour',              niveau: 1 },
    { value: 'PHOTO_BIOMETRIQUE',       label: 'Photo biométrique',            niveau: 1 },
    { value: 'JUSTIFICATIF_DOMICILE',   label: 'Justificatif domicile',        niveau: 2 },
    { value: 'CERTIFICAT_RESIDENCE',    label: 'Certificat de résidence',      niveau: 2 },
    { value: 'CONTRAT_BAIL',            label: 'Contrat de bail',              niveau: 2 },
    { value: 'FICHE_PAIE',              label: 'Fiche de paie',               niveau: 2 },
    { value: 'CONTRAT_TRAVAIL',         label: 'Contrat de travail',           niveau: 2 },
    { value: 'DECLARATION_ACTIVITE',    label: 'Déclaration d\'activité',      niveau: 2 },
    { value: 'REGISTRE_COMMERCE',       label: 'Registre du commerce (RCCM)',  niveau: 2 },
    { value: 'EXTRAIT_BANCAIRE',        label: 'Extrait bancaire (3 mois)',    niveau: 2 },
    { value: 'DECLARATION_SOURCE_FONDS',label: 'Déclaration source des fonds', niveau: 3 },
    { value: 'ATTESTATION_PPE',         label: 'Attestation PPE',              niveau: 3 },
    { value: 'AUTRE',                   label: 'Autre',                        niveau: 1 },
  ];

  constructor(
    private route: ActivatedRoute,
    private kycService: KycService,
    private fb: UntypedFormBuilder
  ) {}

  ngOnInit(): void {
    this.initForms();
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadAll(id);
  }

  private initForms(): void {
    this.verifForm = this.fb.group({
      resultat:       ['', Validators.required],
      niveauApprouve: [''],
      commentaire:    [''],
      motifRejet:     [''],
    });

    this.risqueForm = this.fb.group({
      estPep:           [false],
      verifSanctions:   [false],
      verifListesNoires:[false],
      scoreManuel:      [null, [Validators.min(0), Validators.max(100)]],
      motifRisqueEleve: [''],
      observations:     [''],
    });

    this.docForm = this.fb.group({
      typeDocument:     ['', Validators.required],
      nomFichier:       ['', Validators.required],
      contenuBase64:    ['', Validators.required],
      mimeType:         [''],
      dateExpirationDoc:[''],
    });
  }

  loadAll(id: number): void {
    this.loading = true;
    this.kycService.getDossier(id).subscribe({
      next: (d) => {
        this.dossier = d;
        this.loading = false;
        this.patchRisqueForm(d);
        this.loadDocs(id);
        this.loadVerifs(id);
      },
      error: () => { this.error = 'Dossier KYC introuvable.'; this.loading = false; }
    });
  }

  private patchRisqueForm(d: KycDossierResponse): void {
    this.risqueForm.patchValue({
      estPep:           d.estPep,
      verifSanctions:   d.verifSanctions,
      verifListesNoires:d.verifListesNoires,
      motifRisqueEleve: d.motifRisqueEleve || '',
      observations:     d.observations || '',
    });
  }

  loadDocs(id: number): void {
    this.loadingDocs = true;
    this.kycService.getDocuments(id).subscribe({
      next: (d) => { this.documents = d; this.loadingDocs = false; },
      error: () => this.loadingDocs = false
    });
  }

  loadVerifs(id: number): void {
    this.loadingVerifs = true;
    this.kycService.getVerifications(id).subscribe({
      next: (v) => { this.verifications = v; this.loadingVerifs = false; },
      error: () => this.loadingVerifs = false
    });
  }

  soumettreDossier(): void {
    if (this.verifForm.invalid || !this.dossier) return;
    const val = this.verifForm.value;
    if (val.resultat === 'REJETE' && !val.motifRejet) return;

    this.savingVerif = true;
    const req: VerifierKycRequest = {
      resultat:       val.resultat,
      niveauApprouve: val.niveauApprouve || undefined,
      commentaire:    val.commentaire || undefined,
      motifRejet:     val.motifRejet || undefined,
    };
    this.kycService.verifier(this.dossier.id, req).subscribe({
      next: (d) => {
        this.dossier = d;
        this.verifications = [];
        this.loadVerifs(d.id);
        this.savingVerif = false;
        this.showVerifForm = false;
        this.verifForm.reset();
      },
      error: () => this.savingVerif = false
    });
  }

  evaluerRisque(): void {
    if (!this.dossier) return;
    this.savingRisque = true;
    const val = this.risqueForm.value;
    const req: EvaluerRisqueKycRequest = {
      estPep:           val.estPep,
      verifSanctions:   val.verifSanctions,
      verifListesNoires:val.verifListesNoires,
      scoreManuel:      val.scoreManuel || undefined,
      motifRisqueEleve: val.motifRisqueEleve || undefined,
      observations:     val.observations || undefined,
    };
    this.kycService.evaluerRisque(this.dossier.id, req).subscribe({
      next: (d) => {
        this.dossier = d;
        this.savingRisque = false;
        this.showRisqueForm = false;
      },
      error: () => this.savingRisque = false
    });
  }

  soumettreDocument(): void {
    if (this.docForm.invalid || !this.dossier) return;
    this.savingDoc = true;
    const val = this.docForm.value;
    const req: SoumettreDocumentKycRequest = {
      typeDocument:     val.typeDocument,
      nomFichier:       val.nomFichier,
      contenuBase64:    val.contenuBase64,
      mimeType:         val.mimeType || undefined,
      dateExpirationDoc:val.dateExpirationDoc || undefined,
    };
    this.kycService.soumettreDocument(this.dossier.id, req).subscribe({
      next: (d) => {
        this.documents.unshift(d);
        if (this.dossier) this.dossier.statut = 'DOCUMENTS_SOUMIS';
        this.savingDoc = false;
        this.showDocForm = false;
        this.docForm.reset();
      },
      error: () => this.savingDoc = false
    });
  }

  validerDoc(doc: KycDocumentResponse, valide: boolean): void {
    const motif = valide ? undefined : prompt('Motif de rejet du document :') ?? '';
    if (!valide && !motif) return;
    const req: ValiderDocumentKycRequest = { valide, motifRejet: motif };
    this.kycService.validerDocument(doc.id, req).subscribe({
      next: (updated) => {
        const idx = this.documents.findIndex(d => d.id === updated.id);
        if (idx >= 0) this.documents[idx] = updated;
      }
    });
  }

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const base64 = (reader.result as string).split(',')[1];
      this.docForm.patchValue({
        contenuBase64: base64,
        nomFichier: file.name,
        mimeType: file.type,
      });
    };
    reader.readAsDataURL(file);
  }

  get isRejete(): boolean {
    return this.verifForm.get('resultat')?.value === 'REJETE';
  }

  get isApprouve(): boolean {
    return this.verifForm.get('resultat')?.value === 'APPROUVE';
  }

  getStatutLabel(s: StatutKyc): string {
    const map: Record<StatutKyc, string> = {
      EN_ATTENTE:'En attente', DOCUMENTS_SOUMIS:'Docs soumis',
      EN_COURS_VERIFICATION:'En vérification', COMPLEMENT_REQUIS:'Complément requis',
      APPROUVE:'Approuvé', REJETE:'Rejeté', EXPIRE:'Expiré', SUSPENDU:'Suspendu',
    };
    return map[s] ?? s;
  }

  getStatutClass(s: StatutKyc): string {
    const map: Record<StatutKyc, string> = {
      EN_ATTENTE:'kyc-attente', DOCUMENTS_SOUMIS:'kyc-soumis',
      EN_COURS_VERIFICATION:'kyc-verif', COMPLEMENT_REQUIS:'kyc-complement',
      APPROUVE:'kyc-approuve', REJETE:'kyc-rejete', EXPIRE:'kyc-expire', SUSPENDU:'kyc-suspendu',
    };
    return map[s] ?? '';
  }

  getNiveauLabel(n: NiveauKyc): string {
    return { NIVEAU_1: 'Niveau 1 — Simplifié', NIVEAU_2: 'Niveau 2 — Standard', NIVEAU_3: 'Niveau 3 — Renforcé' }[n] ?? n;
  }

  getRisqueClass(r: NiveauRisque): string {
    const map: Record<NiveauRisque, string> = {
      FAIBLE:'risk-faible', MOYEN:'risk-moyen', ELEVE:'risk-eleve', TRES_ELEVE:'risk-tres-eleve'
    };
    return map[r] ?? '';
  }

  getDocLabel(t: TypeDocumentKyc): string {
    return this.typeDocOptions.find(o => o.value === t)?.label ?? t;
  }

  getDocIcon(t: TypeDocumentKyc): string {
    if (t.startsWith('CNI') || t === 'PASSEPORT' || t === 'PERMIS_CONDUIRE') return 'badge';
    if (t.includes('DOMICILE') || t.includes('RESIDENCE') || t === 'CONTRAT_BAIL') return 'home';
    if (t.includes('PAIE') || t.includes('TRAVAIL') || t.includes('EXTRAIT') || t.includes('ACTIVITE')) return 'work';
    if (t.includes('SOURCE') || t.includes('PPE')) return 'policy';
    if (t === 'PHOTO_BIOMETRIQUE') return 'face';
    return 'description';
  }

  getResultatLabel(r: ResultatVerificationKyc): string {
    return { APPROUVE: 'Approuvé', REJETE: 'Rejeté', COMPLEMENT_REQUIS: 'Complément requis' }[r] ?? r;
  }

  getResultatClass(r: ResultatVerificationKyc): string {
    return { APPROUVE: 'res-ok', REJETE: 'res-ko', COMPLEMENT_REQUIS: 'res-complement' }[r] ?? '';
  }
}

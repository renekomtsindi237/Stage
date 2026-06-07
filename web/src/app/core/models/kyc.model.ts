export type NiveauKyc = 'NIVEAU_1' | 'NIVEAU_2' | 'NIVEAU_3';

export type StatutKyc =
  | 'EN_ATTENTE'
  | 'DOCUMENTS_SOUMIS'
  | 'EN_COURS_VERIFICATION'
  | 'COMPLEMENT_REQUIS'
  | 'APPROUVE'
  | 'REJETE'
  | 'EXPIRE'
  | 'SUSPENDU';

export type NiveauRisque = 'FAIBLE' | 'MOYEN' | 'ELEVE' | 'TRES_ELEVE';

export type ResultatVerificationKyc = 'APPROUVE' | 'REJETE' | 'COMPLEMENT_REQUIS';

export type TypeDocumentKyc =
  | 'CNI_RECTO'
  | 'CNI_VERSO'
  | 'PASSEPORT'
  | 'PERMIS_CONDUIRE'
  | 'CARTE_SEJOUR'
  | 'JUSTIFICATIF_DOMICILE'
  | 'CERTIFICAT_RESIDENCE'
  | 'CONTRAT_BAIL'
  | 'FICHE_PAIE'
  | 'CONTRAT_TRAVAIL'
  | 'DECLARATION_ACTIVITE'
  | 'REGISTRE_COMMERCE'
  | 'EXTRAIT_BANCAIRE'
  | 'DECLARATION_SOURCE_FONDS'
  | 'ATTESTATION_PPE'
  | 'PHOTO_BIOMETRIQUE'
  | 'AUTRE';

export interface KycDossierResponse {
  id: number;
  imfId: number;
  clientId: string;
  nomClient: string;
  prenomClient?: string;
  dateNaissance?: string;
  lieuNaissance?: string;
  nationalite: string;
  telephone?: string;
  email?: string;
  adresse?: string;
  ville?: string;
  profession?: string;
  employeur?: string;
  revenuMensuelEstim?: number;

  typePieceIdentite?: TypeDocumentKyc;
  numeroPiece?: string;
  dateEmissionPiece?: string;
  dateExpirationPiece?: string;
  lieuEmissionPiece?: string;

  niveauActuel: NiveauKyc;
  niveauDemande: NiveauKyc;
  statut: StatutKyc;

  scoreRisque: number;
  niveauRisque: NiveauRisque;
  estPep: boolean;
  motifRisqueEleve?: string;

  verifSanctions: boolean;
  verifListesNoires: boolean;

  verificateurUsername?: string;
  dateVerification?: string;
  dateExpirationKyc?: string;
  observations?: string;

  createdAt: string;
  updatedAt: string;
}

export interface KycDocumentResponse {
  id: number;
  dossierId: number;
  typeDocument: TypeDocumentKyc;
  nomFichier: string;
  mimeType?: string;
  tailleOctets?: number;
  dateExpirationDoc?: string;
  valide?: boolean;
  motifRejet?: string;
  verifiePar?: string;
  dateVerification?: string;
  createdAt: string;
}

export interface KycVerificationResponse {
  id: number;
  dossierId: number;
  verificateurUsername?: string;
  ancienStatut: StatutKyc;
  nouveauStatut: StatutKyc;
  ancienNiveau: NiveauKyc;
  nouveauNiveau: NiveauKyc;
  resultat: ResultatVerificationKyc;
  commentaire?: string;
  motifRejet?: string;
  createdAt: string;
}

export interface InitierKycRequest {
  clientId: string;
  nomClient: string;
  prenomClient?: string;
  dateNaissance?: string;
  lieuNaissance?: string;
  nationalite?: string;
  telephone?: string;
  email?: string;
  adresse?: string;
  ville?: string;
  profession?: string;
  employeur?: string;
  revenuMensuelEstim?: number;
  typePieceIdentite?: TypeDocumentKyc;
  numeroPiece?: string;
  dateEmissionPiece?: string;
  dateExpirationPiece?: string;
  lieuEmissionPiece?: string;
  niveauDemande: NiveauKyc;
  estPep: boolean;
  observations?: string;
}

export interface SoumettreDocumentKycRequest {
  typeDocument: TypeDocumentKyc;
  nomFichier: string;
  contenuBase64: string;
  mimeType?: string;
  tailleOctets?: number;
  dateExpirationDoc?: string;
}

export interface VerifierKycRequest {
  resultat: ResultatVerificationKyc;
  niveauApprouve?: NiveauKyc;
  commentaire?: string;
  motifRejet?: string;
}

export interface EvaluerRisqueKycRequest {
  estPep: boolean;
  verifSanctions: boolean;
  verifListesNoires: boolean;
  scoreManuel?: number;
  motifRisqueEleve?: string;
  observations?: string;
}

export interface ValiderDocumentKycRequest {
  valide: boolean;
  motifRejet?: string;
}

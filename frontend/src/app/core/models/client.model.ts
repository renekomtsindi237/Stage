export interface Client {
  idClient: string;
  nomClient: string;
  telephoneClient?: string;
  agencePrincipale?: string;
  encours?: number;
  statut?: "ACTIF" | "EN_RETARD" | "DEFAILLANT";
}

export interface Collecte {
  id?: string;
  clientId: string;
  montant: number;
  typeOperation: "EPARGNE" | "REMBOURSEMENT";
  positionGps?: { lat: number; lng: number };
  agentId?: string;
  createdAt?: string;
}

export interface AgentDashboard {
  objectifJour: number;
  collecteJour: number;
  clientsVisites: number;
  clientsTotal: number;
  collectesCount: number;
  synchronise: boolean;
  alertesClients: {
    clientId: string;
    nom: string;
    severite: string;
    message: string;
  }[];
}

// ── KYC ────────────────────────────────────────────────────────────────────

export type StatutKyc =
  | "EN_ATTENTE"
  | "DOCUMENTS_SOUMIS"
  | "EN_COURS_VERIFICATION"
  | "COMPLEMENT_REQUIS"
  | "APPROUVE"
  | "REJETE"
  | "EXPIRE"
  | "SUSPENDU";

export type NiveauKyc = "NIVEAU_1" | "NIVEAU_2" | "NIVEAU_3";

export type NiveauRisqueKyc = "FAIBLE" | "MOYEN" | "ELEVE" | "CRITIQUE";

export type ResultatVerif = "APPROUVE" | "REJETE" | "COMPLEMENT_REQUIS";

export interface KycDossier {
  uid: string;
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
  typePieceIdentite?: string;
  numeroPiece?: string;
  dateEmissionPiece?: string;
  dateExpirationPiece?: string;
  lieuEmissionPiece?: string;
  niveauActuel: NiveauKyc;
  niveauDemande: NiveauKyc;
  statut: StatutKyc;
  scoreRisque: number;
  niveauRisque: NiveauRisqueKyc;
  estPep: boolean;
  motifRisqueEleve?: string;
  verifSanctions: boolean;
  verifListesNoires: boolean;
  verificateurUsername?: string;
  dateVerification?: string;
  dateExpirationKyc?: string;
  observations?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface KycDocument {
  uid: string;
  dossierUid: string;
  typeDocument: string;
  nomFichier?: string;
  mimeType?: string;
  tailleOctets?: number;
  dateExpirationDoc?: string;
  valide?: boolean;
  motifRejet?: string;
  verifiePar?: string;
  dateVerification?: string;
  createdAt?: string;
}

// Legacy alias (backward compat)
export interface Kyc {
  clientId: string;
  nomClient: string;
  statut: "VALIDE" | "EN_ATTENTE" | "REFUSE" | "EXPIRE";
  dateValidite?: string;
  documents: KycDocument[];
}

export interface Client {
  id: string;
  code: string;
  nom: string;
  prenom: string;
  telephone: string;
  agence: string;
  agenceNom?: string;
  agentNom?: string;
  encours: number;
  scoreMcrs?: number;
  statut: 'ACTIF' | 'EN_DIFFICULTE' | 'CONTENTIEUX' | 'EN_RETARD' | 'DEFAILLANT';
  dernierePaiement?: string;
}

export interface Collecte {
  id?: string;
  clientId: string;
  montant: number;
  typeOperation: 'EPARGNE' | 'REMBOURSEMENT';
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
  alertesClients: { clientId: string; nom: string; severite: string; message: string }[];
}

export interface Kyc {
  clientId: string;
  nomClient: string;
  statut: 'VALIDE' | 'EN_ATTENTE' | 'REFUSE' | 'EXPIRE';
  dateValidite?: string;
  documents: KycDocument[];
}

export interface KycDocument {
  type: string;
  statut: 'VALIDE' | 'MANQUANT' | 'EXPIRE';
  dateExpiration?: string;
}

export interface CollecteEpargne {
  id: number;
  uuidMobile: string;
  clientIdExterne: string;
  cycleId?: number;
  nomCycle?: string;
  agentId: number;
  agentUsername: string;
  agenceId?: number;
  nomAgence?: string;
  montantCollecte: number;
  dateCollecte: string;
  heureCollecte?: string;
  canalPaiement: string;
  referenceTransaction?: string;
  latitude?: number;
  longitude?: number;
  statut: "SOUMISE" | "VALIDEE" | "DOUBLON" | "REJETEE" | "EN_ATTENTE";
  motifRejet?: string;
  observation?: string;
  syncedAt?: string;
  createdAt: string;
}

export interface CollecteEpargneRequest {
  uuidMobile: string;
  clientIdExterne: string;
  cycleId?: number;
  agenceId?: number;
  montantCollecte: number;
  dateCollecte: string;
  heureCollecte?: string;
  canalPaiement: string;
  referenceTransaction?: string;
  latitude?: number;
  longitude?: number;
  precisionGpsMetres?: number;
  observation?: string;
}

export interface SyncCollectesRequest {
  collectes: CollecteEpargneRequest[];
}

export interface SyncCollectesResponse {
  totalRecu: number;
  acceptees: number;
  doublons: number;
  rejetees: number;
  uuidsAcceptes: string[];
  uuidsDoublons: string[];
  details: Array<{ uuidMobile: string; motif: string }>;
}

export interface KpiJourAgent {
  date: string;
  nbCollectes: number;
  montantTotal: number;
  montantEspeces: number;
  montantMobileMoney: number;
  nbClientsUniques: number;
}

export interface KpiCollecteAgence {
  agenceId: number;
  nomAgence: string;
  region: string;
  datePeriode: string;
  nbCollectes: number;
  montantTotal: number;
  montantMoyen: number;
  nbClientsUniques: number;
  objectifMontant?: number;
  tauxRealisationPct?: number;
  tauxPonctualitePct?: number;
  tauxRejetPct?: number;
  montantEspeces: number;
  montantMtn: number;
  montantOrange: number;
  montantWave: number;
  rangAgence?: number;
  scoreZscore?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

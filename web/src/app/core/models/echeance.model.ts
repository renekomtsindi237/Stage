export type StatutEcheance =
  | "EN_ATTENTE"
  | "PAYEE"
  | "PARTIELLE"
  | "EN_RETARD"
  | "ANNULEE";

export interface EcheanceResponse {
  id: number;
  idPret: string;
  agentId?: number;
  agentUsername?: string;
  numEcheance: number;
  dateEcheance: string;
  montantDu: number;
  montantPaye: number;
  resteAPayer: number;
  datePaiement?: string;
  statut: StatutEcheance;
  collecteId?: number;
  observation?: string;
  createdAt: string;
  updatedAt: string;
}

export interface EcheanceUpdateRequest {
  montantPaye?: number;
  datePaiement?: string;
  observation?: string;
}

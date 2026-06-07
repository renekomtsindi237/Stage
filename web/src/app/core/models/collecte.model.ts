import { CanalPaiement } from './recouvrement.model';

export type StatutCollecte =
  | 'SOUMISE'
  | 'CONFIRMEE'
  | 'DOUBLON'
  | 'REJETEE';

export interface CollecteResponse {
  id: number;
  idCollecteMobile: string;
  clientId: string;
  pretId: string;
  dateCollecte: string;
  montantCollecte: number;
  canalPaiement: CanalPaiement;
  referenceTransaction?: string;
  statut: StatutCollecte;
  createdAt: string;
}

export type StatutPret =
  | "ACTIF"
  | "EN_RETARD"
  | "EN_RECOUVREMENT"
  | "SOLDE"
  | "PERTE";

export interface PretResponse {
  idPret: string;
  idClient: string;
  nomClient: string;
  nomAgence: string;
  nomProduit: string;
  nomAgent: string;
  montantPret: number;
  dateDeblocage: string;
  dateEcheance: string;
  montantRembourse: number;
  soldeRestant: number;
  statutPret: StatutPret;
  joursRetard: number;
}

export type Severite = "CRITIQUE" | "HAUTE" | "MOYENNE" | "BASSE";
export type StatutAlerte = "NON_TRAITEE" | "EN_TRAITEMENT" | "RESOLUE";

export interface Alerte {
  id: string;
  clientId: string;
  nomClient: string;
  agence: string;
  severite: Severite;
  statut: StatutAlerte;
  message: string;
  encours: number;
  probabiliteDefaut?: number;
  createdAt: string;
  traitePar?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

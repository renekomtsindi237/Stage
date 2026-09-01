export type Severite = "CRITIQUE" | "HAUTE" | "MOYENNE" | "BASSE";
export type StatutAlerte =
  "NON_TRAITEE" | "EN_TRAITEMENT" | "RESOLUE" | "IGNOREE";

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
  recommandation?: string;
  typeAlerte?: string;
  resolutionNote?: string;
  joursRetard?: number;
  scoreMcrs?: number;
  actionRecommandee?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

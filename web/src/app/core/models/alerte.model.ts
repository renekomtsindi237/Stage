export type StatutAlerte = "ACTIVE" | "CLOTUREE" | "ESCALADEE";

export interface AlerteResponse {
  id: number;
  idPret: string;
  joursRetard: number;
  montantEnRetard: number;
  statutAlerte: StatutAlerte;
  dateGeneration: string;
  dateCloture?: string;
  fcmSent: boolean;
  emailSent: boolean;
}

export interface AlerteUpdateRequest {
  statut: StatutAlerte;
}

export type RecouvrementPhase =
  | "RELANCE_AMIABLE"
  | "MEDIATION_AMIABLE"
  | "MISE_EN_DEMEURE"
  | "CONTENTIEUX"
  | "REECHELONNEMENT"
  | "PERTE";

export type CategorieCobtac =
  | "EN_SURVEILLANCE"
  | "DOUTEUSE"
  | "LITIGIEUSE"
  | "CONTENTIEUSE";

export type TypeGarantie =
  | "CAUTION_SOLIDAIRE"
  | "CAUTIONNAIRE_PERSONNEL"
  | "NANTISSEMENT"
  | "HYPOTHEQUE"
  | "DEPOT_GARANTIE";

export type TypeActionRecouvrement =
  | "APPEL_TELEPHONIQUE"
  | "SMS_RELANCE"
  | "EMAIL_RELANCE"
  | "VISITE_TERRAIN"
  | "MEDIATION_CHEF_QUARTIER"
  | "MEDIATION_FAMILLE"
  | "CONTACT_CAUTION"
  | "SAISIE_GARANTIE"
  | "MISE_EN_DEMEURE_LETTRE"
  | "INTERVENTION_HUISSIER"
  | "COMITE_RECOUVREMENT"
  | "ASSIGNATION_TRIBUNAL"
  | "ENCAISSEMENT_PARTIEL"
  | "ENCAISSEMENT_TOTAL"
  | "ACCORD_REECHELONNEMENT"
  | "CESSION_CREANCE"
  | "RADIATION";

export type StatutVerifMomo = "EN_ATTENTE" | "VERIFIE" | "REJETE";

export type CanalPaiement = "MTN" | "ORANGE" | "ESPECES" | "VIREMENT";

export type ResultatActionRecouvrement =
  | "EN_ATTENTE"
  | "CONTACT_ETABLI"
  | "PROMESSE_PAIEMENT"
  | "SANS_REPONSE"
  | "REFUSE"
  | "PAIEMENT_EFFECTUE"
  | "ACCORD_OBTENU";

export interface DossierRecouvrementResponse {
  id: number;
  uid?: string;
  idPret: string;
  nomClient?: string;
  montantImpaye: number;
  joursRetard: number;
  categorieCobtac: CategorieCobtac;
  tauxProvision: number;
  montantProvision: number;
  datePremiereEcheanceImpayee?: string;
  nomCaution?: string;
  telephoneCaution?: string;
  typeGarantie?: TypeGarantie;
  fraisRecouvrement: number;
  phase: RecouvrementPhase;
  dateOuverture: string;
  dateDerniereAction?: string;
  agentResponsableUsername?: string;
  clos: boolean;
  dateCloture?: string;
  motifCloture?: string;
  updatedAt: string;
}

export interface ActionRecouvrementResponse {
  id: number;
  dossierId: number;
  typeAction: TypeActionRecouvrement;
  dateAction: string;
  agentUsername?: string;
  resultat?: ResultatActionRecouvrement;
  promesseDate?: string;
  promesseMontant?: number;
  canalPaiement?: CanalPaiement;
  referenceTransaction?: string;
  numeroTelephonePaiement?: string;
  statutVerifMomo?: StatutVerifMomo;
  fraisEngages?: number;
  observation?: string;
  createdAt: string;
}

export interface AccordReechelonnementResponse {
  id: number;
  dossierId: number;
  nouveauMontantMensuel: number;
  nombreNouvellesEcheances: number;
  dateDebutNouvelEcheancier: string;
  tauxInteretAnnuel?: number;
  approuveParUsername?: string;
  dateSignature?: string;
  observations?: string;
  actif: boolean;
  createdAt: string;
}

export interface OuvrirDossierRequest {
  idPret: string;
  nomClient?: string;
  montantImpaye: number;
  joursRetard: number;
  datePremiereEcheanceImpayee?: string;
  agentResponsableId?: number;
  nomCaution?: string;
  telephoneCaution?: string;
  typeGarantie?: TypeGarantie;
}

export interface AjouterActionRequest {
  typeAction: TypeActionRecouvrement;
  resultat?: ResultatActionRecouvrement;
  promesseDate?: string;
  promesseMontant?: number;
  canalPaiement?: CanalPaiement;
  referenceTransaction?: string;
  numeroTelephonePaiement?: string;
  statutVerifMomo?: StatutVerifMomo;
  fraisEngages?: number;
  observation?: string;
}

export interface EscaladerDossierRequest {
  nouvellePhase: RecouvrementPhase;
  motif?: string;
}

export interface AccordReechelonnementRequest {
  nouveauMontantMensuel: number;
  nombreNouvellesEcheances: number;
  dateDebutNouvelEcheancier: string;
  tauxInteretAnnuel?: number;
  observations?: string;
}

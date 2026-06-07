export interface Creance {
  id: number;
  idPretExterne: string;
  clientIdExterne: string;
  imfId: number;
  agenceId?: number;
  nomAgence?: string;
  montantInitial: number;
  montantImpaye: number;
  capitalRestantDu?: number;
  interetsRetard: number;
  montantProvision: number;
  joursRetard: number;
  categoriePar: CategoriePar;
  classeRisqueCobac?: ClasseRisqueCobac;
  tauxProvisionCobac: number;
  typeGarantie?: string;
  statut: StatutCreance;
  agentResponsableUsername?: string;
  dateOuvertureCreance: string;
  scoreMcrs?: ScoreMcrs;
  createdAt: string;
  updatedAt: string;
}

export type CategoriePar = 'COURANT' | 'PAR30' | 'PAR60' | 'PAR90' | 'PAR180' | 'PERTE';
export type ClasseRisqueCobac = 'A' | 'B' | 'C' | 'D' | 'E';
export type StatutCreance =
  | 'ACTIVE' | 'RECOUVREMENT_AMIABLE' | 'MISE_EN_DEMEURE'
  | 'CONTENTIEUX' | 'REECHELONNEE' | 'SOLDEE' | 'IRRECOVERABLE' | 'RADIEE';

export interface ScoreMcrs {
  scoreCrs: number;
  scoreRps: number;
  scoreCsi: number;
  scoreMcrs: number;
  classeRisque: ClasseRisqueMl;
  probabiliteDefaut90j: number;
  actionRecommandee: ActionRecommandee;
  prioriteRecouvrement: number;
  topFeature: string;
  topShapValue: number;
}

export type ClasseRisqueMl = 'FAIBLE' | 'MODERE' | 'ELEVE' | 'CRITIQUE';
export type ActionRecommandee =
  | 'AUCUNE' | 'RELANCE_PREVENTIVE' | 'VISITE_TERRAIN'
  | 'RESTRUCTURATION' | 'MISE_EN_DEMEURE' | 'ESCALADE_JURIDIQUE';

export interface KpiRecouvrement {
  imfId: number;
  agenceId?: number;
  datePeriode: string;
  par30Montant: number;
  par60Montant: number;
  par90Montant: number;
  par30TauxPct: number;
  par60TauxPct: number;
  par90TauxPct: number;
  tauxRecouvrementPct: number;
  montantRecouvre: number;
  montantPerteNette: number;
  encoursTotal: number;
  nbCreancesActives: number;
  nbCreancesProbleme: number;
  totalProvisions: number;
  rangAgence?: number;
  nbAgencesComparees?: number;
}

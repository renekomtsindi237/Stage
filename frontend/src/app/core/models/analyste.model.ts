export interface PipelineStatus {
  derniereExecution: string;
  statutGlobal: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'IDLE';
  dags: DagStatus[];
}

export interface DagStatus {
  id: string;
  nom: string;
  statut: 'SUCCESS' | 'RUNNING' | 'FAILED' | 'PENDING';
  duree?: string;
  lignesLues?: number;
  lignesEcrites?: number;
  derniereExec?: string;
  logUrl?: string;
}

export interface MlDrift {
  psiActuel: number;
  seuilCritique: number;
  driftDetecte: boolean;
  modeleActif: string;
  dernierEntrainement: string;
  evolutionPsi: { date: string; psi: number }[];
  contributionFeatures: FeatureContrib[];
}

export interface FeatureContrib {
  nom: string;
  psi: number;
  contribution: number;
}

export interface ScoringMcrs {
  scoresMoyens: number;
  distribution: { bucket: string; count: number }[];
  topClientsRisque: { clientId: string; nom: string; score: number; variation: number }[];
}

export interface PipelineStatus {
  derniereExecution: string;
  statutGlobal: "RUNNING" | "SUCCESS" | "FAILED" | "IDLE";
  dags: DagStatus[];
  run?: PipelineRun | null;
}

export interface DagStatus {
  id: string;
  nom: string;
  statut: "SUCCESS" | "RUNNING" | "FAILED" | "PENDING";
  duree?: string;
  lignesLues?: number;
  lignesEcrites?: number;
  derniereExec?: string;
  logUrl?: string;
}

export interface PipelineRun {
  runId: string;
  statut: "RUNNING" | "SUCCESS" | "FAILED";
  etapeCourante: number;
  etapesTotal: number;
  message: string;
  modeleVersion?: string;
  airflowDeclenche?: boolean;
  etapes: PipelineRunStep[];
}

export interface PipelineRunStep {
  id: string;
  nom: string;
  statut: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";
  detail?: string;
  lignesLues?: number;
  lignesEcrites?: number;
}

export interface PipelineTriggerResult {
  message: string;
  run: PipelineRun;
  dejaEnCours: boolean;
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
  topClientsRisque: {
    clientId: string;
    nom: string;
    score: number;
    variation: number;
  }[];
}

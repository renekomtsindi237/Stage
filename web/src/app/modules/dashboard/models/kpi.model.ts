// ── Dashboard Directeur ───────────────────────────────────────────────────
export interface DashboardDirecteur {
  // Collectes épargne
  collecteJour: number;
  montantCollecteJour: number;
  montantCollecteMois: number;
  tauxRealisationObjectifPct: number;
  variationCollecteSemaine: number;
  // Recouvrement
  encoursPar30: number;
  encoursPar60: number;
  encoursPar90: number;
  tauxPar30Pct: number;
  tauxPar90Pct: number;
  tauxRecouvrementPct: number;
  totalProvisions: number;
  nbCreancesActives: number;
  // Scoring ML
  nbClientsRisqueCritique: number;
  nbClientsRisqueEleve: number;
  nbAlertesMlActives: number;
  // Benchmarks
  rangAgence?: number;
  nbAgencesComparees?: number;
  // Temporel
  dateReference: string;
}

// ── Dashboard Responsable Recouvrement ────────────────────────────────────
export interface DashboardRecouvrement {
  nbDossiersOuverts: number;
  nbDossiersRelanceAmiable: number;
  nbDossiersMiseEnDemeure: number;
  nbDossiersContentieux: number;
  nbPromessesEnAttente: number;
  nbPromessesEchues: number;
  montantRecouvrerCible: number;
  montantRecouvreMois: number;
  tauxRecouvrementMoisPct: number;
  nbClientsCritiques: number;
  nbActionsAujourdhui: number;
}

// ── Dashboard Agent Terrain ───────────────────────────────────────────────
export interface DashboardAgent {
  kpiJour: KpiJourAgent;
  kpiSemaine: KpiSemaineAgent;
  collectesEnAttente: number;
  collectesNonSynchros: number;
  cycleCourant?: CycleInfo;
  objectifCourant?: ObjectifInfo;
}

export interface KpiJourAgent {
  date: string;
  nbCollectes: number;
  montantTotal: number;
  montantEspeces: number;
  montantMobileMoney: number;
  nbClientsUniques: number;
}

export interface KpiSemaineAgent {
  semaineCourante: number;
  nbCollectes: number;
  montantTotal: number;
  objectifMontant?: number;
  tauxRealisation?: number;
  rangAgence?: number;
}

export interface CycleInfo {
  id: number;
  nomCycle: string;
  periodicite: string;
  dateDebut: string;
  dateFin?: string;
  objectifMontant?: number;
}

export interface ObjectifInfo {
  objectifMontant: number;
  objectifNbTransactions: number;
  realiseMontant: number;
  realiseNbTransactions: number;
  tauxRealisationPct: number;
}

// ── Stats et séries temporelles ───────────────────────────────────────────
export interface ParStat {
  nomAgence: string;
  region: string;
  dateValeur: string;
  encoursPar30: number;
  encoursPar60: number;
  encoursPar90: number;
  tauxPar30Pct: number;
  tauxPar90Pct: number;
  encoursTotal: number;
  nbCreancesActives: number;
  statutPret?: string;
  montantPret?: number;
  montantRembourse?: number;
}

export interface CollecteStat {
  dateValeur: string;
  canal: string;
  nomAgence: string;
  nbCollectes: number;
  montantTotal: number;
  objectifMontant?: number;
  tauxRealisationPct?: number;
}

export interface TendancePrixProduit {
  codeProduit: string;
  nomProduit: string;
  categorie: string;
  zoneId: string;
  dateValeur: string;
  prixUnitaire: number;
  prixMoy30j: number;
  prixMoy90j: number;
  variation30jPct: number;
}

export interface BenchmarkAgence {
  nomAgence: string;
  region: string;
  rangCollecte: number;
  rangRecouvrement: number;
  rangGlobal: number;
  scoreGlobal: number;
  scoreCollecteZscore: number;
  scoreRecouvrementZscore: number;
  nbAgencesComparees: number;
}

// ── Ancien modèle conservé pour compatibilité ─────────────────────────────
export interface DashboardSummary {
  totalCollectes: number;
  nbCollectes: number;
  encoursPar30: number;
  encoursPar90: number;
  encoursTotal: number;
  nbAlertesActives: number;
  dateDebut: string;
  dateFin: string;
}

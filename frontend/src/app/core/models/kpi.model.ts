export interface KpiDashboard {
  encoursTotalFcfa: number;
  par30: number;
  par90: number;
  tauxRecouvrement: number;
  collectesDuJour: number;
  variation: {
    encours: number;
    par30: number;
    par90: number;
    collectes: number;
  };
  evolutionPar30j: { date: string; par30: number; par90: number }[];
  alertesActives: AlerteResume[];
  activiteRecente: ActiviteItem[];
}

export interface AlerteResume {
  id: string;
  nomClient: string;
  severite: string;
  message: string;
  createdAt: string;
}

export interface ActiviteItem {
  id: string;
  type: 'COLLECTE' | 'ALERTE' | 'KYC' | 'DOSSIER';
  description: string;
  montant?: number;
  auteur: string;
  createdAt: string;
}

export interface KpiPortefeuille {
  encoursTotalFcfa: number;
  par30: number;
  par90Cobac: number;
  tauxRecouvrement30j: number;
  variations: { encours: number; par30: number; par90: number; recouvrement: number };
  evolutionPar: { date: string; par30: number; par90: number; objectif: number }[];
  repartitionCobacParAgence: AgenceCobac[];
  provisionsCobac: ProvisionDetail[];
}

export interface AgenceCobac {
  agence: string;
  encours: number;
  enSouffrance: number;
  rembourse: number;
  pourcentage: number;
}

export interface ProvisionDetail {
  categorieCobac: string;
  nbEntrees: number;
  encours: number;
  typeProv: string;
  provRequise: number;
}

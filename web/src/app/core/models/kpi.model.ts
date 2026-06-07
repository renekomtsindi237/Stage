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

export interface ParStat {
  nomAgence: string;
  dateValeur: string;
  montantPret: number;
  encoursPar30: number;
  encoursPar90: number;
  joursRetard: number;
}

export interface CollecteStat {
  dateValeur: string;
  canal: string;
  nomAgence: string;
  nbCollectes: number;
  montantTotal: number;
}

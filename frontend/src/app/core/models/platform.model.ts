export interface ImfRow {
  id: string;
  nom: string;
  ville: string;
  actif: boolean;
  agencesCount: number;
  utilisateursCount: number;
  encoursFcfa: number;
  par30: number;
}

export interface AuditLogEntry {
  id: string;
  action: string;
  imf: string;
  utilisateur: string;
  date: string;
}

export interface PlatformStats {
  imfsActives: number;
  utilisateursTotal: number;
  collectes30j: number;
  encoursTotalFcfa: number;
  imfs: ImfRow[];
  auditLogs: AuditLogEntry[];
}

// Enveloppe standard retournée par le backend (ApiResponse<T>)
export interface ApiResp<T> {
  success: boolean;
  data: T;
  message?: string;
}

// Stats réelles de la plateforme (PlatformStatsResponse backend)
export interface PlatformActualStats {
  totalImfs: number;
  activeImfs: number;
  inactiveImfs: number;
  totalUsers: number;
  newImfsThisMonth: number;
}

// Détail complet d'une IMF (ImfResponse backend)
export interface ImfDetail {
  uid: string;
  code: string;
  nom: string;
  pays: string;
  actif: boolean;
  createdAt: string;
  denominationSociale: string;
  adresseSiege: string;
  formeJuridique: string;
  capitalSocial: number;
  numAgrement?: string;
  telephone?: string;
  email?: string;
  tauxInteretAnnuel: number;
  dureeMaxCreditMois: number;
  tauxPenaliteRetard: number;
  seuilRelanceJours: number;
  tauxEpargne?: number;
  soldeMinEpargne?: number;
  fraisTenueCompte?: number;
  segmentsClients?: string;
  typesGaranties?: string;
  maxDocumentKycOctets?: number;
  niveauKycMinimal?: string;
  maxTentativesConnexion?: number;
  logoUrl?: string;
  hasDsi: boolean;
}

// Entrée de la piste d'audit immuable (AuditTrailResponse backend)
export interface AuditEntry {
  id: number;
  imfId?: number;
  acteurUsername: string;
  acteurRole: string;
  action: string;
  entiteType: string;
  entiteId?: string;
  ancienneValeur?: Record<string, unknown>;
  nouvelleValeur?: Record<string, unknown>;
  motif?: string;
  ipClient?: string;
  statut: string;
  createdAt: string;
}

// Wrapper pagination Spring (Page<T>)
export interface PagedResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PlatformDashboard {
  imfActives: number;
  imfTotal: number;
  utilisateurs: number;
  volume30j: number;
  alertesCritiques: number;
  alerteCritique?: { imfNom: string; par90: number; seuil: number };
}

export interface RgpdDashboard {
  violationsActives: ViolationRgpd[];
  demandesDroits: DemandeDroit[];
  consentements: Consentement[];
}

export interface ViolationRgpd {
  id: string;
  titre: string;
  description: string;
  personnesConcernees: number;
  severite: "HAUTE" | "MOYENNE";
  delaiRestantSeconds: number;
  createdAt: string;
}

export interface DemandeDroit {
  id: string;
  type: "SUPPRESSION" | "ACCES" | "RECTIFICATION" | "PORTABILITE";
  sujetId: string;
  delaiRestantJours: number;
  statut: "EN_COURS" | "TRAITE";
}

export interface Consentement {
  utilisateur: string;
  finalite: string;
  statut: "ACCORDE" | "REVOQUE";
  updatedAt: string;
}

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

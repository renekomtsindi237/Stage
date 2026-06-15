export interface Delegation {
  uid: string;
  typeDelegation: "REASSIGNATION_DOSSIER" | "DELEGATION_AUTORITE";
  delegantId: number;
  delegataireId: number;
  objetId?: number;
  objetType?: string;
  motif?: string;
  roleDelegue?: string;
  montantSeuil?: number;
  dateDebut: string;
  dateFin?: string;
  actif: boolean;
  createdAt: string;
}

export interface ReassignerDossierRequest {
  nouvelAgentUid: string;
  motif?: string;
}

export interface DeleguerAutoriteRequest {
  delegataireUid: string;
  roleDelegue?: string;
  montantSeuil?: number;
  dateFin?: string;
  motif?: string;
}

export interface AgentCredit {
  uid: string;
  username: string;
  role: string;
  email?: string;
  actif: boolean;
}

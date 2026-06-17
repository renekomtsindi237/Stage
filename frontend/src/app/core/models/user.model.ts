export type Role =
  | 'AGENT'
  | 'ANALYSTE'
  | 'DIRECTEUR'
  | 'DSI'
  | 'SUPER_ADMIN'
  | 'RESPONSABLE_RECOUVREMENT'
  | 'CHEF_AGENCE'
  | 'AGENT_CREDIT'
  | 'AGENT_SAISIE'
  | 'CAISSIER'
  | 'ANALYSTE_ENGAGEMENTS'
  | 'SUPPORT';

export interface User {
  id: string;
  nom: string;
  prenom: string;
  email: string;
  role: Role;
  agenceId?: string;
  imfId?: string;
  nomImf?: string;
  nomAgence?: string;
  avatarUrl?: string;
  actif: boolean;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Notification {
  id: string;
  titre: string;
  message: string;
  type: 'INFO' | 'WARNING' | 'CRITICAL' | 'SUCCESS';
  lu: boolean;
  createdAt: string;
}

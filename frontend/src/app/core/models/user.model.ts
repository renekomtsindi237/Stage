export type Role =
  | "AGENT"
  | "ANALYSTE"
  | "DIRECTEUR"
  | "DSI"
  | "SUPER_ADMIN"
  | "RESPONSABLE_RECOUVREMENT"
  | "CHEF_AGENCE"
  | "AGENT_CREDIT"
  | "AGENT_SAISIE"
  | "CAISSIER"
  | "ANALYSTE_ENGAGEMENTS"
  | "SUPPORT";

export interface User {
  username: string;
  role: Role;
  imfUid?: string | null;
  imfCode?: string | null;
  imfNom?: string | null;
  imfLogoUrl?: string | null;
  mustChangePassword?: boolean;
  /** URL backend de l'avatar (chemin R2 ou local). Null = image par défaut. */
  avatarUrl?: string | null;
  /** @deprecated utiliser avatarUrl */
  avatarDataUrl?: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  role: string;
  username: string;
  imfUid?: string | null;
  imfCode?: string | null;
  imfNom?: string | null;
  imfLogoUrl?: string | null;
  mustChangePassword?: boolean;
  expiresIn: number;
  status?: string;
}

export interface Notification {
  id: string;
  titre: string;
  message: string;
  type: "INFO" | "WARNING" | "CRITICAL" | "SUCCESS";
  lu: boolean;
  createdAt: string;
}

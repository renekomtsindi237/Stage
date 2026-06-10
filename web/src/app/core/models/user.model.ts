import { Role } from "./auth.model";

/** Préférences personnalisables par chaque utilisateur. */
export interface UserPreferences {
  /** Thème visuel : light | dark | auto (suit le système). */
  prefTheme: "light" | "dark" | "auto";
  /** Langue de l'interface : fr | en. */
  prefLangue: "fr" | "en";
  /** Maître-switch : désactiver toutes les notifications. */
  notificationsActives: boolean;
  /** Recevoir les alertes d'impayés. */
  notifAlertes: boolean;
  /** Recevoir les confirmations de collectes terrain. */
  notifCollectes: boolean;
  /** Recevoir les fins de synchronisation hors-ligne. */
  notifSync: boolean;
  /** Recevoir les statuts pipeline (usage technique/DSI). */
  notifPipeline: boolean;
  /** Éléments affichés par page dans les listes paginées. */
  elementsParPage: number;
}

export interface UserResponse {
  id: number;
  uid?: string;
  username: string;
  role: Role;
  zoneId?: string;
  email?: string;
  avatarUrl?: string;
  latitude?: number;
  longitude?: number;
  imfId?: number;
  imfCode?: string;
  imfNom?: string;
  actif: boolean;
  mustChangePassword: boolean;
  lastLogin?: string;
  createdAt: string;
  // Préférences
  prefLangue?: "fr" | "en";
  prefTheme?: "light" | "dark" | "auto";
  notificationsActives?: boolean;
  notifAlertes?: boolean;
  notifCollectes?: boolean;
  notifSync?: boolean;
  notifPipeline?: boolean;
  elementsParPage?: number;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

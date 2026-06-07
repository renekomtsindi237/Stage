export type Role =
  | 'SUPER_ADMIN'
  | 'DIRECTEUR'
  | 'RESPONSABLE_RECOUVREMENT'
  | 'ANALYSTE'
  | 'DSI'
  | 'AGENT';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  role: Role;
  username: string;
  imfId?: number;
  imfCode?: string;
  imfNom?: string;
  mustChangePassword?: boolean;
  expiresIn: number;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

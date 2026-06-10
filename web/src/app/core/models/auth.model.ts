export type Role =
  | "SUPER_ADMIN"
  | "DIRECTEUR"
  | "RESPONSABLE_RECOUVREMENT"
  | "ANALYSTE"
  | "DSI"
  | "SUPPORT"
  | "AGENT";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  role: Role;
  username: string;
  imfUid?: string;
  imfCode?: string;
  imfNom?: string;
  mustChangePassword?: boolean;
  expiresIn: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface OtpInitResponse {
  message: string;
}

export interface OtpVerifyResponse {
  status: string;
  accessToken: string;
  refreshToken: string;
  role: string;
  username: string;
  imfUid?: string;
  imfCode?: string;
  imfNom?: string;
  mustChangePassword?: boolean;
  expiresIn: number;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface SseEvent {
  type: string;
  message: string;
  timestamp: string;
  targetRole?: string;
  payload?: unknown;
}

export interface NotificationItem {
  id: number;
  type: string;
  titre: string;
  message: string;
  targetRole?: string;
  lu: boolean;
  createdAt: string;
}

import { Injectable, OnDestroy, inject } from "@angular/core";
import { Subject, Observable } from "rxjs";
import { AuthService } from "../auth/auth.service";
import { environment } from "../../../environments/environment";

export interface SseEvent {
  type: string;
  message: string;
  timestamp: string;
  payload?: unknown;
}

/**
 * Flux SSE. EventSource same-origin + cookie (et token en query en secours).
 * Reconnexion exponentielle, pause si l'onglet est masqué — évite de spammer
 * QUIC/HTTP2 quand Cloudflare coupe le flux idle.
 */
@Injectable({ providedIn: "root" })
export class SseService implements OnDestroy {
  private readonly auth = inject(AuthService);

  private eventSource: EventSource | null = null;
  readonly events$ = new Subject<SseEvent>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private stopped = false;
  private visibilityHandler: (() => void) | null = null;

  private readonly EVENT_TYPES = [
    "HEARTBEAT",
    "ALERTE_CREATED",
    "ALERTE_UPDATED",
    "COLLECTE_CONFIRMED",
    "KPI_UPDATED",
    "PIPELINE_STATUS",
    "SYNC_COMPLETED",
    "MONITORING_UPDATE",
    "TICKET_MISE_A_JOUR",
    "SCORING_UPDATE",
    "AGENT_POSITION_UPDATED",
  ];

  connect(): Observable<SseEvent> {
    this.stopped = false;
    this.disconnectSocket();
    this.bindVisibility();
    this.open();
    return this.events$.asObservable();
  }

  private open() {
    if (this.stopped || !this.auth.isLoggedIn()) return;
    if (typeof document !== "undefined" && document.hidden) return;

    const token = this.auth.getToken();
    const base = `${environment.apiUrl}/api/v1/sse/stream`;
    // EventSource ne peut pas envoyer Authorization : JWT en query si disponible,
    // cookie httpOnly en complément (withCredentials).
    const url = token ? `${base}?token=${encodeURIComponent(token)}` : base;
    this.eventSource = new EventSource(url, { withCredentials: true });

    this.eventSource.onopen = () => {
      this.reconnectAttempts = 0;
    };

    this.eventSource.onmessage = (e) => {
      this.reconnectAttempts = 0;
      this.dispatch(e.data);
    };

    this.EVENT_TYPES.forEach((type) => {
      this.eventSource?.addEventListener(type, (e: Event) => {
        this.reconnectAttempts = 0;
        this.dispatch((e as MessageEvent).data, type);
      });
    });

    this.eventSource.onerror = () => {
      this.disconnectSocket();
      this.scheduleReconnect();
    };
  }

  private dispatch(raw: string, type?: string) {
    try {
      const parsed: SseEvent = JSON.parse(raw);
      this.events$.next(type ? { ...parsed, type } : parsed);
    } catch {
      /* ignore malformed */
    }
  }

  private scheduleReconnect() {
    if (this.stopped || !this.auth.isLoggedIn()) return;
    this.reconnectAttempts++;
    const delay = Math.min(
      30_000,
      2000 * 2 ** Math.min(this.reconnectAttempts - 1, 4),
    );
    this.reconnectTimer = setTimeout(() => this.open(), delay);
  }

  private bindVisibility() {
    if (this.visibilityHandler || typeof document === "undefined") return;
    this.visibilityHandler = () => {
      if (document.hidden) {
        this.disconnectSocket();
      } else if (this.auth.isLoggedIn() && !this.eventSource && !this.stopped) {
        this.reconnectAttempts = 0;
        this.open();
      }
    };
    document.addEventListener("visibilitychange", this.visibilityHandler);
  }

  private disconnectSocket() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  disconnect() {
    this.stopped = true;
    this.disconnectSocket();
    if (this.visibilityHandler && typeof document !== "undefined") {
      document.removeEventListener("visibilitychange", this.visibilityHandler);
      this.visibilityHandler = null;
    }
  }

  ngOnDestroy() {
    this.disconnect();
    this.events$.complete();
  }
}

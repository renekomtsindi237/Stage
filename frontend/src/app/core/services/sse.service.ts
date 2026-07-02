import { Injectable, OnDestroy, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Subject, Observable } from "rxjs";
import { AuthService } from "../auth/auth.service";

export interface SseEvent {
  type: string;
  message: string;
  timestamp: string;
  payload?: unknown;
}

@Injectable({ providedIn: "root" })
export class SseService implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);

  private eventSource: EventSource | null = null;
  readonly events$ = new Subject<SseEvent>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private readonly MAX_RECONNECT = 3;

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
    this.disconnect();
    if (!this.auth.isLoggedIn()) return this.events$.asObservable();

    const token = this.auth.getToken();
    const url = token
      ? `/api/v1/sse/stream?token=${encodeURIComponent(token)}`
      : "/api/v1/sse/stream";
    this.eventSource = new EventSource(url);

    this.eventSource.onmessage = (e) => {
      this.reconnectAttempts = 0;
      try {
        const event: SseEvent = JSON.parse(e.data);
        this.events$.next(event);
      } catch {
        /* ignore malformed */
      }
    };

    this.EVENT_TYPES.forEach((type) => {
      this.eventSource?.addEventListener(type, (e: Event) => {
        this.reconnectAttempts = 0;
        try {
          const parsed: SseEvent = JSON.parse((e as MessageEvent).data);
          this.events$.next({ ...parsed, type });
        } catch {
          /* ignore */
        }
      });
    });

    this.eventSource.onerror = () => {
      this.disconnect();
      this.reconnectAttempts++;
      if (this.reconnectAttempts >= this.MAX_RECONNECT) {
        this.reconnectAttempts = 0;
        this.http.get("/api/v1/users/me", { withCredentials: true }).subscribe({
          next: () => {
            if (this.auth.isLoggedIn()) {
              this.reconnectTimer = setTimeout(() => this.connect(), 5000);
            }
          },
          error: () => {
            /* auth interceptor handles logout */
          },
        });
        return;
      }
      this.reconnectTimer = setTimeout(() => {
        if (this.auth.isLoggedIn()) this.connect();
      }, 5000);
    };

    return this.events$.asObservable();
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  ngOnDestroy() {
    this.disconnect();
    this.events$.complete();
  }
}

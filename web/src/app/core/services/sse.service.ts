import { Injectable, OnDestroy } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Subject, Observable } from "rxjs";
import { AuthService } from "./auth.service";
import { UserPreferencesService } from "./user-preferences.service";
import { SseEvent } from "../models/api-response.model";

@Injectable({ providedIn: "root" })
export class SseService implements OnDestroy {
  private eventSource: EventSource | null = null;
  readonly events$ = new Subject<SseEvent>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  // Nombre de tentatives de reconnexion consécutives sans message reçu.
  // Réinitialisé à 0 dès qu'un message arrive (connexion saine).
  private reconnectAttempts = 0;
  private readonly MAX_RECONNECT = 3;

  constructor(
    private authService: AuthService,
    private userPrefs: UserPreferencesService,
    private http: HttpClient,
  ) {}

  connect(): Observable<SseEvent> {
    this.disconnect();
    if (!this.authService.isLoggedIn()) return this.events$.asObservable();

    // Maître-switch : si l'utilisateur a désactivé toutes ses notifications, ne pas ouvrir le flux
    if (!this.userPrefs.snapshot.notificationsActives)
      return this.events$.asObservable();

    this.eventSource = new EventSource("/api/sse/stream", {
      withCredentials: true,
    });

    this.eventSource.onmessage = (e) => {
      this.reconnectAttempts = 0; // connexion saine
      try {
        const event: SseEvent = JSON.parse(e.data);
        if (this.isAllowed(event.type)) this.events$.next(event);
      } catch {}
    };

    [
      "HEARTBEAT",
      "ALERTE_CREATED",
      "ALERTE_UPDATED",
      "COLLECTE_CONFIRMED",
      "PIPELINE_STATUS",
      "KPI_UPDATED",
      "SYNC_COMPLETED",
    ].forEach((type) => {
      this.eventSource?.addEventListener(type, (e: Event) => {
        this.reconnectAttempts = 0; // connexion saine
        try {
          if (!this.isAllowed(type)) return;
          const event: SseEvent = JSON.parse((e as MessageEvent).data);
          this.events$.next({ ...event, type });
        } catch {}
      });
    });

    this.eventSource.onerror = () => {
      this.disconnect();
      this.reconnectAttempts++;

      if (this.reconnectAttempts >= this.MAX_RECONNECT) {
        // Après plusieurs échecs consécutifs, vérifier la session via HTTP.
        // Cette requête passe par le JWT interceptor : si le token est expiré,
        // l'interceptor tentera un refresh (401) ou propagera l'erreur (logout).
        this.reconnectAttempts = 0;
        this.http.get("/api/users/me", { withCredentials: true }).subscribe({
          next: () => {
            // Session toujours valide — relancer SSE après un délai
            if (this.authService.isLoggedIn()) {
              this.reconnectTimer = setTimeout(() => this.connect(), 5000);
            }
          },
          error: () => {
            // Session invalide — le JWT interceptor a déclenché logout(), ne pas relancer
          },
        });
        return;
      }

      // Échec transitoire (réseau) — relancer dans 5 secondes
      this.reconnectTimer = setTimeout(() => {
        if (this.authService.isLoggedIn()) this.connect();
      }, 5000);
    };

    return this.events$.asObservable();
  }

  disconnect(): void {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.events$.complete();
  }

  /**
   * Filtre les événements selon les préférences de l'utilisateur.
   * HEARTBEAT et KPI_UPDATED passent toujours (nécessaires au bon fonctionnement de l'UI).
   */
  private isAllowed(type: string): boolean {
    const prefs = this.userPrefs.snapshot;
    switch (type) {
      case "ALERTE_CREATED":
      case "ALERTE_UPDATED":
        return prefs.notifAlertes;
      case "COLLECTE_CONFIRMED":
        return prefs.notifCollectes;
      case "SYNC_COMPLETED":
        return prefs.notifSync;
      case "PIPELINE_STATUS":
        return prefs.notifPipeline;
      default:
        return true; // HEARTBEAT, KPI_UPDATED
    }
  }
}

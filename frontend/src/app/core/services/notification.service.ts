import { Injectable, OnDestroy, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { BehaviorSubject, Observable, Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { SseService } from "./sse.service";
import { AuthService } from "../auth/auth.service";

export interface NotificationItem {
  uid: string;
  type: string;
  titre: string;
  message: string;
  lu: boolean;
  createdAt: string;
}

interface ApiPage {
  content: NotificationItem[];
}
interface ApiResponse<T> {
  success: boolean;
  data?: T;
}

@Injectable({ providedIn: "root" })
export class NotificationService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly sse = inject(SseService);
  private readonly auth = inject(AuthService);

  private items$ = new BehaviorSubject<NotificationItem[]>([]);
  private sseSub?: Subscription;

  get notifications$(): Observable<NotificationItem[]> {
    return this.items$.asObservable();
  }

  get unreadCount$(): Observable<number> {
    return this.items$.pipe(map((items) => items.filter((n) => !n.lu).length));
  }

  init() {
    this.loadInitial();
    this.sseSub = this.sse.connect().subscribe((event) => {
      if (event.type === "HEARTBEAT") return;
      const item: NotificationItem = {
        uid: "",
        type: event.type,
        titre: this.titleForType(event.type),
        message: event.message,
        lu: false,
        createdAt: event.timestamp,
      };
      this.items$.next([item, ...this.items$.value]);
      this.loadInitial();
    });
  }

  loadInitial() {
    if (!this.auth.isLoggedIn()) return;
    this.http
      .get<ApiResponse<ApiPage>>("/api/v1/notifications?page=0&size=30")
      .pipe(map((r) => r.data?.content ?? []))
      .subscribe({ next: (items) => this.items$.next(items), error: () => {} });
  }

  markAsRead(uid: string) {
    if (!uid) return;
    this.http
      .put<ApiResponse<void>>(`/api/v1/notifications/${uid}/read`, {})
      .subscribe({
        next: () => {
          this.items$.next(
            this.items$.value.map((n) =>
              n.uid === uid ? { ...n, lu: true } : n,
            ),
          );
        },
        error: () => {},
      });
  }

  markAllAsRead() {
    this.http
      .put<ApiResponse<void>>("/api/v1/notifications/read-all", {})
      .subscribe({
        next: () => {
          this.items$.next(this.items$.value.map((n) => ({ ...n, lu: true })));
        },
        error: () => {},
      });
  }

  reset() {
    this.items$.next([]);
    this.sseSub?.unsubscribe();
    this.sse.disconnect();
  }

  ngOnDestroy() {
    this.reset();
  }

  private titleForType(type: string): string {
    const titles: Record<string, string> = {
      // Alertes recouvrement
      ALERTE_CREATED: "Nouvelle alerte impayé",
      ALERTE_UPDATED: "Alerte mise à jour",
      // Collectes terrain
      COLLECTE_CONFIRMED: "Collecte confirmée",
      COLLECTE_SOUMISE: "Collecte soumise",
      // Analytics & pipeline
      KPI_UPDATED: "KPI mis à jour",
      PIPELINE_STATUS: "Statut pipeline",
      SYNC_COMPLETED: "Synchronisation terminée",
      // Tickets support
      TICKET_MISE_A_JOUR: "Réponse à votre ticket",
      NOUVEAU_TICKET: "Nouveau ticket support",
      // Agents & positions
      AGENT_POSITION_UPDATED: "Position agent mise à jour",
      // Dossiers crédit
      DOSSIER_EN_COMITE: "Dossier en comité",
      DOSSIER_VALIDE: "Dossier validé",
      DOSSIER_REJETE: "Dossier rejeté",
      // Accords recouvrement
      ACCORD_SIGNE: "Accord de rééchelonnement",
      // Conformité RGPD
      DEMANDE_RGPD: "Demande RGPD",
    };
    return titles[type] ?? "Notification";
  }
}

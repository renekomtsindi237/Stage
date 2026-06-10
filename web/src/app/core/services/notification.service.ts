import { Injectable, OnDestroy } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { BehaviorSubject, Observable, Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { ApiResponse, NotificationItem } from "../models/api-response.model";
import { SseService } from "./sse.service";
import { AuthService } from "./auth.service";

@Injectable({ providedIn: "root" })
export class NotificationService implements OnDestroy {
  private readonly API = "/api/v1/notifications";

  private items$ = new BehaviorSubject<NotificationItem[]>([]);
  private sseSub?: Subscription;

  constructor(
    private http: HttpClient,
    private sseService: SseService,
    private auth: AuthService,
  ) {}

  /** Initialise le service : charge l'historique + Ã©coute le flux SSE. */
  init(): void {
    this.loadInitial();
    this.sseSub = this.sseService.connect().subscribe((event) => {
      if (event.type === "HEARTBEAT") return;
      // PrÃ©pend une notification locale depuis l'Ã©vÃ©nement SSE
      const item: NotificationItem = {
        id: 0, // ID temporaire â€” sera remplacÃ© au prochain reload
        type: event.type,
        titre: this.titleForType(event.type),
        message: event.message,
        lu: false,
        createdAt: event.timestamp,
      };
      this.items$.next([item, ...this.items$.value]);
      // Recharge depuis l'API pour avoir les vrais IDs
      this.loadInitial();
    });
  }

  get notifications$(): Observable<NotificationItem[]> {
    return this.items$.asObservable();
  }

  get unreadCount$(): Observable<number> {
    return this.items$.pipe(map((items) => items.filter((n) => !n.lu).length));
  }

  loadInitial(): void {
    if (!this.auth.isLoggedIn()) return;
    this.http
      .get<ApiResponse<{ content: NotificationItem[] }>>(
        `${this.API}?page=0&size=30`,
      )
      .pipe(map((r) => r.data?.content ?? []))
      .subscribe({ next: (items) => this.items$.next(items), error: () => {} });
  }

  markAsRead(id: number): void {
    if (id === 0) return;
    this.http.put<ApiResponse<void>>(`${this.API}/${id}/read`, {}).subscribe({
      next: () => {
        this.items$.next(
          this.items$.value.map((n) => (n.id === id ? { ...n, lu: true } : n)),
        );
      },
      error: () => {},
    });
  }

  markAllAsRead(): void {
    this.http.put<ApiResponse<void>>(`${this.API}/read-all`, {}).subscribe({
      next: () => {
        this.items$.next(this.items$.value.map((n) => ({ ...n, lu: true })));
      },
      error: () => {},
    });
  }

  reset(): void {
    this.items$.next([]);
    this.sseSub?.unsubscribe();
  }

  ngOnDestroy(): void {
    this.reset();
  }

  private titleForType(type: string): string {
    const titles: Record<string, string> = {
      ALERTE_CREATED: "Nouvelle alerte impayÃ©",
      ALERTE_UPDATED: "Alerte mise Ã  jour",
      COLLECTE_CONFIRMED: "Collecte confirmÃ©e",
      KPI_UPDATED: "KPI mis Ã  jour",
      PIPELINE_STATUS: "Statut pipeline",
      SYNC_COMPLETED: "Synchronisation terminÃ©e",
    };
    return titles[type] ?? "Notification";
  }
}


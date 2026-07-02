import {
  Component,
  Output,
  EventEmitter,
  OnInit,
  OnDestroy,
  inject,
  signal,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { TranslatePipe } from "@ngx-translate/core";
import { trigger, transition, style, animate } from "@angular/animations";
import { Subscription } from "rxjs";
import {
  NotificationService,
  NotificationItem,
} from "../../../core/services/notification.service";

@Component({
  selector: "app-notification-panel",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./notification-panel.component.html",
  styleUrls: ["./notification-panel.component.scss"],
  animations: [
    trigger("slideIn", [
      transition(":enter", [
        style({ transform: "translateX(100%)", opacity: 0 }),
        animate(
          "280ms cubic-bezier(0.4,0,0.2,1)",
          style({ transform: "translateX(0)", opacity: 1 }),
        ),
      ]),
      transition(":leave", [
        animate(
          "220ms cubic-bezier(0.4,0,0.2,1)",
          style({ transform: "translateX(100%)", opacity: 0 }),
        ),
      ]),
    ]),
    trigger("fadeOverlay", [
      transition(":enter", [
        style({ opacity: 0 }),
        animate("200ms ease-out", style({ opacity: 1 })),
      ]),
      transition(":leave", [animate("180ms ease-in", style({ opacity: 0 }))]),
    ]),
  ],
})
export class NotificationPanelComponent implements OnInit, OnDestroy {
  @Output() closed = new EventEmitter<void>();

  private readonly notifService = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private sub?: Subscription;

  notifications: NotificationItem[] = [];
  activeTab = signal<"tous" | "non-lus" | "lus">("tous");

  get unreadCount() {
    return this.notifications.filter((n) => !n.lu).length;
  }

  get readCount() {
    return this.notifications.filter((n) => n.lu).length;
  }

  get filteredNotifications(): NotificationItem[] {
    const tab = this.activeTab();
    if (tab === "non-lus") return this.notifications.filter((n) => !n.lu);
    if (tab === "lus") return this.notifications.filter((n) => n.lu);
    return this.notifications;
  }

  ngOnInit() {
    this.sub = this.notifService.notifications$.subscribe((items) => {
      this.notifications = items;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  markRead(n: NotificationItem) {
    if (!n.lu) this.notifService.markAsRead(n.uid);
  }

  markAllRead() {
    this.notifService.markAllAsRead();
  }

  close() {
    this.closed.emit();
  }

  iconForType(type: string): string {
    const icons: Record<string, string> = {
      // Alertes
      ALERTE_CREATED: "warning_amber",
      ALERTE_UPDATED: "update",
      // Collectes
      COLLECTE_CONFIRMED: "payments",
      COLLECTE_SOUMISE: "assignment_turned_in",
      // Analytics
      KPI_UPDATED: "bar_chart",
      PIPELINE_STATUS: "sync",
      SYNC_COMPLETED: "cloud_done",
      // Tickets
      TICKET_MISE_A_JOUR: "support_agent",
      NOUVEAU_TICKET: "confirmation_number",
      // Agents
      AGENT_POSITION_UPDATED: "location_on",
      // Dossiers crédit
      DOSSIER_EN_COMITE: "gavel",
      DOSSIER_VALIDE: "check_circle",
      DOSSIER_REJETE: "cancel",
      // Accords
      ACCORD_SIGNE: "handshake",
      // RGPD
      DEMANDE_RGPD: "privacy_tip",
    };
    return icons[type] ?? "notifications";
  }

  colorForType(type: string): string {
    const colors: Record<string, string> = {
      ALERTE_CREATED: "#f59e0b",
      ALERTE_UPDATED: "#3b82f6",
      COLLECTE_CONFIRMED: "#22c55e",
      COLLECTE_SOUMISE: "#059669",
      KPI_UPDATED: "#8b5cf6",
      PIPELINE_STATUS: "#0d9488",
      SYNC_COMPLETED: "#6366f1",
      TICKET_MISE_A_JOUR: "#7c3aed",
      NOUVEAU_TICKET: "#db2777",
      AGENT_POSITION_UPDATED: "#0ea5e9",
      DOSSIER_EN_COMITE: "#d97706",
      DOSSIER_VALIDE: "#16a34a",
      DOSSIER_REJETE: "#dc2626",
      ACCORD_SIGNE: "#2563eb",
      DEMANDE_RGPD: "#0891b2",
    };
    return colors[type] ?? "var(--color-text-muted)";
  }

  timeAgo(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const s = Math.floor(diff / 1000);
    if (s < 60) return "à l'instant";
    const m = Math.floor(s / 60);
    if (m < 60) return `il y a ${m} min`;
    const h = Math.floor(m / 60);
    if (h < 24) return `il y a ${h}h`;
    const d = Math.floor(h / 24);
    return `il y a ${d}j`;
  }
}

import { Component, OnInit, OnDestroy, Output, EventEmitter } from '@angular/core';
import { Subscription } from 'rxjs';
import { NotificationService } from '@core/services/notification.service';
import { NotificationItem } from '@core/models/api-response.model';
import { trigger, transition, style, animate } from '@angular/animations';

@Component({
  selector: 'imf-notification-panel',
  templateUrl: './notification-panel.component.html',
  styleUrls: ['./notification-panel.component.scss'],
  animations: [
    trigger('slideIn', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('280ms cubic-bezier(0.4,0,0.2,1)',
                style({ transform: 'translateX(0)', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('220ms cubic-bezier(0.4,0,0.2,1)',
                style({ transform: 'translateX(100%)', opacity: 0 }))
      ])
    ]),
    trigger('fadeOverlay', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('200ms ease-out', style({ opacity: 1 }))
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0 }))
      ])
    ])
  ]
})
export class NotificationPanelComponent implements OnInit, OnDestroy {

  @Output() closed = new EventEmitter<void>();

  notifications: NotificationItem[] = [];
  private sub?: Subscription;

  constructor(public notifService: NotificationService) {}

  ngOnInit(): void {
    this.sub = this.notifService.notifications$.subscribe(items => {
      this.notifications = items;
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  markRead(n: NotificationItem): void {
    if (!n.lu) this.notifService.markAsRead(n.id);
    n = { ...n, lu: true };
  }

  markAllRead(): void {
    this.notifService.markAllAsRead();
  }

  close(): void {
    this.closed.emit();
  }

  iconForType(type: string): string {
    const icons: Record<string, string> = {
      ALERTE_CREATED:     'warning_amber',
      ALERTE_UPDATED:     'update',
      COLLECTE_CONFIRMED: 'payments',
      KPI_UPDATED:        'bar_chart',
      PIPELINE_STATUS:    'sync',
      SYNC_COMPLETED:     'cloud_done',
    };
    return icons[type] ?? 'notifications';
  }

  colorForType(type: string): string {
    const colors: Record<string, string> = {
      ALERTE_CREATED:     '#F59E0B',
      ALERTE_UPDATED:     '#3B82F6',
      COLLECTE_CONFIRMED: '#10B981',
      KPI_UPDATED:        '#8B5CF6',
      PIPELINE_STATUS:    '#0D9488',
      SYNC_COMPLETED:     '#6366F1',
    };
    return colors[type] ?? 'var(--color-text-muted)';
  }

  timeAgo(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const s = Math.floor(diff / 1000);
    if (s < 60)  return 'à l\'instant';
    const m = Math.floor(s / 60);
    if (m < 60)  return `il y a ${m} min`;
    const h = Math.floor(m / 60);
    if (h < 24)  return `il y a ${h}h`;
    const d = Math.floor(h / 24);
    return `il y a ${d}j`;
  }

  get unreadCount(): number {
    return this.notifications.filter(n => !n.lu).length;
  }
}

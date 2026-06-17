import { Component, Output, EventEmitter, OnInit, OnDestroy, inject, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { Subscription } from 'rxjs';
import { NotificationService, NotificationItem } from '../../../core/services/notification.service';

@Component({
  selector: 'app-notification-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './notification-panel.component.html',
  styleUrls: ['./notification-panel.component.scss'],
  animations: [
    trigger('slideIn', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('280ms cubic-bezier(0.4,0,0.2,1)', style({ transform: 'translateX(0)', opacity: 1 })),
      ]),
      transition(':leave', [
        animate('220ms cubic-bezier(0.4,0,0.2,1)', style({ transform: 'translateX(100%)', opacity: 0 })),
      ]),
    ]),
    trigger('fadeOverlay', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('200ms ease-out', style({ opacity: 1 })),
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0 })),
      ]),
    ]),
  ]
})
export class NotificationPanelComponent implements OnInit, OnDestroy {
  @Output() closed = new EventEmitter<void>();

  private readonly notifService = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private sub?: Subscription;

  notifications: NotificationItem[] = [];

  get unreadCount() { return this.notifications.filter(n => !n.lu).length; }

  ngOnInit() {
    this.sub = this.notifService.notifications$.subscribe(items => {
      this.notifications = items;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  markRead(n: NotificationItem) {
    if (!n.lu) this.notifService.markAsRead(n.id);
  }

  markAllRead() { this.notifService.markAllAsRead(); }

  close() { this.closed.emit(); }

  iconForType(type: string): string {
    const icons: Record<string, string> = {
      ALERTE_CREATED:    'warning_amber',
      ALERTE_UPDATED:    'update',
      COLLECTE_CONFIRMED:'payments',
      KPI_UPDATED:       'bar_chart',
      PIPELINE_STATUS:   'sync',
      SYNC_COMPLETED:    'cloud_done',
    };
    return icons[type] ?? 'notifications';
  }

  colorForType(type: string): string {
    const colors: Record<string, string> = {
      ALERTE_CREATED:    '#f59e0b',
      ALERTE_UPDATED:    '#3b82f6',
      COLLECTE_CONFIRMED:'#22c55e',
      KPI_UPDATED:       '#8b5cf6',
      PIPELINE_STATUS:   '#0d9488',
      SYNC_COMPLETED:    '#6366f1',
    };
    return colors[type] ?? 'var(--color-text-muted)';
  }

  timeAgo(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const s = Math.floor(diff / 1000);
    if (s < 60) return 'à l\'instant';
    const m = Math.floor(s / 60);
    if (m < 60) return `il y a ${m} min`;
    const h = Math.floor(m / 60);
    if (h < 24) return `il y a ${h}h`;
    const d = Math.floor(h / 24);
    return `il y a ${d}j`;
  }
}

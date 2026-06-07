import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-skeleton',
  template: `
    <ng-container [ngSwitch]="type">

      <!-- KPI grid skeleton -->
      <div *ngSwitchCase="'kpi-grid'" class="kpi-grid">
        <div *ngFor="let i of items(4)" class="skeleton skeleton-card" style="height:120px;"></div>
      </div>

      <!-- Table rows skeleton -->
      <div *ngSwitchCase="'table'" style="overflow:hidden;">
        <div *ngFor="let i of items(count); let j = index"
             class="skeleton skeleton-table-row"
             [style.--delay]="(j * 0.07) + 's'"
             [style.width]="'100%'">
        </div>
      </div>

      <!-- Card skeleton -->
      <div *ngSwitchCase="'card'" class="skeleton skeleton-card" [style.height]="height"></div>

      <!-- Text lines -->
      <div *ngSwitchCase="'text'">
        <div *ngFor="let i of items(count)"
             class="skeleton skeleton-text"
             [style.width]="i % 3 === 0 ? '60%' : i % 3 === 1 ? '85%' : '75%'">
        </div>
      </div>

      <!-- Chart skeleton -->
      <div *ngSwitchCase="'chart'" class="skeleton skeleton-card" style="height:220px;border-radius:12px;"></div>

    </ng-container>
  `,
})
export class SkeletonComponent {
  @Input() type: 'kpi-grid' | 'table' | 'card' | 'text' | 'chart' = 'card';
  @Input() count = 5;
  @Input() height = '120px';

  items(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
  }
}

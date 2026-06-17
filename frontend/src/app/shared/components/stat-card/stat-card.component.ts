import { Component, Input, ChangeDetectionStrategy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FcfaPipe } from "../../pipes/fcfa.pipe";

@Component({
  selector: "app-stat-card",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FcfaPipe],
  template: `
    @if (loading) {
      <div class="card stat-card stat-card-loading">
        <div
          class="skeleton"
          style="height:12px;width:60%;margin-bottom:12px"
        ></div>
        <div
          class="skeleton"
          style="height:28px;width:80%;margin-bottom:8px"
        ></div>
        <div class="skeleton" style="height:12px;width:40%"></div>
      </div>
    } @else {
      <div class="card stat-card">
        <div class="stat-card-label">{{ label }}</div>
        <div class="stat-card-value">
          @if (isCurrency) {
            {{ value | fcfa }}
          } @else if (isPercent) {
            {{ value | number: "1.1-1" }}%
          } @else {
            {{ value | number: "1.0-0" }}
          }
        </div>
        @if (trend !== undefined) {
          <div
            class="stat-card-trend"
            [class.up]="trendUp"
            [class.down]="!trendUp"
          >
            <span class="material-icons-round">{{
              trendUp ? "trending_up" : "trending_down"
            }}</span>
            {{ trend | number: "1.1-1" }}%
            <span class="stat-card-trend-label">{{ trendLabel }}</span>
          </div>
        }
        @if (subtitle) {
          <div class="stat-card-subtitle">{{ subtitle }}</div>
        }
      </div>
    }
  `,
  styles: [
    `
      .stat-card {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .stat-card-label {
        font-size: 12px;
        font-weight: 500;
        color: var(--color-text-muted);
        text-transform: uppercase;
        letter-spacing: 0.4px;
      }
      .stat-card-value {
        font-size: 24px;
        font-weight: 700;
        color: var(--color-text);
        line-height: 1.1;
      }
      .stat-card-trend {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        font-weight: 500;
        margin-top: 4px;
      }
      .stat-card-trend .material-icons-round {
        font-size: 14px;
      }
      .stat-card-trend.up {
        color: var(--color-success);
      }
      .stat-card-trend.down {
        color: var(--color-danger);
      }
      .stat-card-trend-label {
        color: var(--color-text-muted);
        font-weight: 400;
        margin-left: 2px;
      }
      .stat-card-subtitle {
        font-size: 11px;
        color: var(--color-text-light);
        margin-top: 2px;
      }
      .stat-card-loading {
        min-height: 90px;
      }
    `,
  ],
})
export class StatCardComponent {
  @Input() label = "";
  @Input() value: number = 0;
  @Input() trend?: number;
  @Input() trendLabel = "vs mois préc.";
  @Input() isCurrency = false;
  @Input() isPercent = false;
  @Input() subtitle?: string;
  @Input() loading = false;

  get trendUp(): boolean {
    return (this.trend ?? 0) >= 0;
  }
}

import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from "@angular/core";
import { RouterLink } from "@angular/router";

@Component({
  selector: "app-empty-state",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="empty-state">
      <span class="material-icons-round">{{ icon }}</span>
      <p>{{ title }}</p>
      @if (hint) {
        <p class="empty-state__hint">{{ hint }}</p>
      }
      @if (ctaLabel && ctaLink) {
        <a [routerLink]="ctaLink" class="btn btn-primary btn-sm">{{
          ctaLabel
        }}</a>
      } @else if (ctaLabel) {
        <button
          type="button"
          class="btn btn-primary btn-sm"
          (click)="ctaClick.emit()"
        >
          {{ ctaLabel }}
        </button>
      }
    </div>
  `,
  styles: [
    `
      .empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
        text-align: center;
        padding: 32px 16px;
        color: var(--color-text-muted);
      }
      .empty-state .material-icons-round {
        font-size: 40px;
        opacity: 0.45;
      }
      .empty-state p {
        margin: 0;
        font-size: 14px;
      }
      .empty-state__hint {
        font-size: 13px;
        max-width: 420px;
      }
      .empty-state .btn {
        margin-top: 8px;
      }
    `,
  ],
})
export class EmptyStateComponent {
  @Input() icon = "inbox";
  @Input() title = "Aucun résultat";
  @Input() hint = "";
  @Input() ctaLabel = "";
  @Input() ctaLink = "";
  @Output() ctaClick = new EventEmitter<void>();
}

import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-alert-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `<span class="badge" [ngClass]="cssClass">{{ label }}</span>`,
  styles: [``]
})
export class AlertBadgeComponent {
  @Input() severite: string = 'BASSE';

  get cssClass(): string {
    return `badge-${this.severite.toLowerCase()}`;
  }

  get label(): string {
    return this.severite;
  }
}

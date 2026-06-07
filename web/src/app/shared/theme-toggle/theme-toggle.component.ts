import { Component } from "@angular/core";
import { ThemeService } from "../../core/services/theme.service";

@Component({
  selector: "app-theme-toggle",
  template: `
    <button
      mat-icon-button
      class="toggle-btn"
      [title]="isDark ? 'Passer en mode clair' : 'Passer en mode sombre'"
      (click)="toggle()"
      [attr.aria-label]="
        isDark ? 'Passer en mode clair' : 'Passer en mode sombre'
      "
    >
      <mat-icon>{{ isDark ? "light_mode" : "dark_mode" }}</mat-icon>
    </button>
  `,
  styles: [
    `
      .toggle-btn {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: var(--color-surface-raised);
        border: 1.5px solid var(--color-border);
        color: var(--color-text-primary);
        display: flex;
        align-items: center;
        justify-content: center;
        transition:
          background 0.2s,
          border-color 0.2s,
          transform 0.15s;
      }
      .toggle-btn:hover {
        background: var(--color-primary-light);
        border-color: var(--color-primary);
        transform: scale(1.08);
      }
      .toggle-btn mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
      }
    `,
  ],
})
export class ThemeToggleComponent {
  get isDark(): boolean {
    return this.themeService.isDark;
  }

  constructor(private themeService: ThemeService) {}

  toggle(): void {
    this.themeService.toggle();
  }
}

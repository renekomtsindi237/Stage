import {
  Component,
  HostListener,
  ViewChild,
  ElementRef,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
  effect,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { TranslatePipe, TranslateService } from "@ngx-translate/core";
import { AuthService } from "../../../core/auth/auth.service";
import { MENU_BY_ROLE, AppMenuItem } from "../../nav/app-menu";
import { CommandPaletteService } from "./command-palette.service";

@Component({
  selector: "app-command-palette",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, TranslatePipe],
  template: `
    @if (palette.open()) {
      <div
        class="palette-backdrop"
        (click)="palette.hide()"
        (keydown.escape)="palette.hide()"
      >
        <div
          class="palette"
          (click)="$event.stopPropagation()"
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="'command_palette.title' | translate"
        >
          <div class="palette__search">
            <span class="material-icons-round">search</span>
            <input
              #queryInput
              type="text"
              [ngModel]="query()"
              (ngModelChange)="query.set($event); active.set(0)"
              [placeholder]="'command_palette.placeholder' | translate"
              (keydown)="onInputKey($event)"
            />
            <kbd>Esc</kbd>
          </div>
          <ul class="palette__list" role="listbox">
            @for (item of filtered(); track item.route; let i = $index) {
              <li
                [class.active]="i === active()"
                (click)="go(item)"
                (mouseenter)="active.set(i)"
                role="option"
                [attr.aria-selected]="i === active()"
              >
                <span class="material-icons-round">{{ item.icon }}</span>
                <span>{{ item.label | translate }}</span>
              </li>
            } @empty {
              <li class="empty">
                {{ "command_palette.empty" | translate }}
              </li>
            }
          </ul>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .palette-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(15, 23, 42, 0.45);
        z-index: 12000;
        display: flex;
        justify-content: center;
        padding: 12vh 16px 0;
      }
      .palette {
        width: min(520px, 100%);
        background: var(--color-surface);
        border-radius: 12px;
        box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
        overflow: hidden;
        border: 1px solid var(--color-border);
      }
      .palette__search {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px 14px;
        border-bottom: 1px solid var(--color-border);
      }
      .palette__search input {
        flex: 1;
        border: none;
        outline: none;
        background: transparent;
        font-size: 15px;
        color: var(--color-text);
      }
      .palette__search kbd {
        font-size: 11px;
        padding: 2px 6px;
        border-radius: 4px;
        background: var(--color-bg);
        color: var(--color-text-muted);
      }
      .palette__list {
        list-style: none;
        margin: 0;
        padding: 6px;
        max-height: 360px;
        overflow-y: auto;
      }
      .palette__list li {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px 12px;
        border-radius: 8px;
        cursor: pointer;
        font-size: 14px;
      }
      .palette__list li.active {
        background: var(--color-bg);
      }
      .palette__list li.empty {
        color: var(--color-text-muted);
        cursor: default;
        justify-content: center;
      }
      .palette__list .material-icons-round {
        font-size: 18px;
        color: var(--color-text-muted);
      }
    `,
  ],
})
export class CommandPaletteComponent {
  readonly palette = inject(CommandPaletteService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslateService);

  @ViewChild("queryInput") queryInput?: ElementRef<HTMLInputElement>;

  query = signal("");
  active = signal(0);

  readonly items = computed<AppMenuItem[]>(() => {
    const role = this.auth.role() ?? "";
    const menu = MENU_BY_ROLE[role] ?? [];
    return [
      ...menu,
      {
        label: "sidebar.profile_link",
        icon: "person",
        route: "/profile",
      },
    ];
  });

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.items();
    if (!q) return all;
    return all.filter((item) => {
      const label = this.i18n.instant(item.label).toLowerCase();
      return label.includes(q) || item.route.toLowerCase().includes(q);
    });
  });

  constructor() {
    effect(() => {
      if (this.palette.open()) {
        this.query.set("");
        this.active.set(0);
        queueMicrotask(() => this.queryInput?.nativeElement.focus());
      }
    });
  }

  @HostListener("document:keydown", ["$event"])
  onDocKey(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      this.palette.toggle();
    }
  }

  onInputKey(e: KeyboardEvent) {
    const list = this.filtered();
    if (e.key === "ArrowDown") {
      e.preventDefault();
      this.active.update((i) => Math.min(i + 1, list.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      this.active.update((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const item = list[this.active()];
      if (item) this.go(item);
    } else if (e.key === "Escape") {
      this.palette.hide();
    }
  }

  go(item: AppMenuItem) {
    this.palette.hide();
    void this.router.navigateByUrl(item.route);
  }
}

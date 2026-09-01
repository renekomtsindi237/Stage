import {
  Component,
  ElementRef,
  HostListener,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
} from "@angular/core";
import { LanguageService } from "../../../core/services/language.service";

const LANGUAGES = [
  { code: "fr" as const, label: "Français", flag: "🇫🇷" },
  { code: "en" as const, label: "English", flag: "🇺🇸" },
];

@Component({
  selector: "app-language-selector",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="lang-sel">
      <button class="lang-sel__trigger" (click)="toggle()">
        <span class="lang-sel__flag">{{ current().flag }}</span>
        <span class="lang-sel__label">{{ current().label }}</span>
        <span
          class="material-icons-round lang-sel__chevron"
          [class.rotated]="open()"
          >expand_more</span
        >
      </button>

      @if (open()) {
        <div class="lang-sel__menu">
          @for (lang of languages; track lang.code) {
            <button
              class="lang-sel__item"
              [class.lang-sel__item--active]="langSvc.lang() === lang.code"
              (click)="select(lang.code)"
            >
              <span class="lang-sel__flag">{{ lang.flag }}</span>
              <span class="lang-sel__item-label">{{ lang.label }}</span>
              @if (langSvc.lang() === lang.code) {
                <span class="material-icons-round lang-sel__check">check</span>
              }
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .lang-sel {
        position: relative;
      }

      .lang-sel__trigger {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 12px;
        border-radius: var(--radius-full);
        border: 1px solid var(--color-border);
        background: transparent;
        cursor: pointer;
        font-size: 13px;
        color: var(--color-text);
        transition:
          background 0.15s,
          border-color 0.15s;

        &:hover {
          background: var(--color-bg);
          border-color: var(--color-primary);
        }
      }

      .lang-sel__flag {
        font-size: 15px;
        line-height: 1;
      }

      .lang-sel__label {
        font-weight: 600;
        font-size: 13px;
      }

      .lang-sel__chevron {
        font-size: 18px;
        color: var(--color-text-muted);
        transition: transform 0.2s ease;

        &.rotated {
          transform: rotate(180deg);
        }
      }

      .lang-sel__menu {
        position: absolute;
        top: calc(100% + 6px);
        left: 0;
        min-width: 168px;
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: 12px;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.13);
        overflow: hidden;
        z-index: 300;
        animation: lang-fade 0.15s ease;
      }

      .lang-sel__item {
        display: flex;
        align-items: center;
        gap: 9px;
        width: 100%;
        padding: 10px 14px;
        font-size: 13px;
        font-family: inherit;
        text-align: left;
        cursor: pointer;
        color: var(--color-text);
        background: none;
        border: none;
        transition: background 0.12s;

        &:hover {
          background: var(--color-bg);
        }
      }

      .lang-sel__item--active {
        font-weight: 700;
        color: var(--color-primary);
      }

      .lang-sel__item-label {
        flex: 1;
      }

      .lang-sel__check {
        font-size: 16px;
        color: var(--color-primary);
      }

      @keyframes lang-fade {
        from {
          opacity: 0;
          transform: translateY(-4px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
    `,
  ],
})
export class LanguageSelectorComponent {
  readonly langSvc = inject(LanguageService);
  private readonly el = inject(ElementRef);

  readonly languages = LANGUAGES;
  readonly open = signal(false);
  readonly current = computed(
    () => LANGUAGES.find((l) => l.code === this.langSvc.lang()) ?? LANGUAGES[0],
  );

  toggle() {
    this.open.update((v) => !v);
  }

  select(code: "fr" | "en") {
    this.langSvc.setLang(code);
    this.open.set(false);
  }

  @HostListener("document:click", ["$event"])
  onOutsideClick(e: MouseEvent) {
    if (!this.el.nativeElement.contains(e.target)) {
      this.open.set(false);
    }
  }
}

import { Component, ChangeDetectionStrategy } from "@angular/core";
import { RouterLink, RouterOutlet } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { LanguageSelectorComponent } from "../../shared/components/language-selector/language-selector.component";

@Component({
  selector: "app-auth-shell",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, TranslatePipe, LanguageSelectorComponent],
  template: `
    <div class="auth-shell">
      <a routerLink="/" class="auth-back">
        <span class="material-icons-round">arrow_back</span>
        {{ "auth_shell.back_home" | translate }}
      </a>
      <div class="auth-lang">
        <app-language-selector />
      </div>
      <div class="auth-logo-wrap">
        <img
          src="assets/logo.png"
          alt="MicroRecouv"
          class="auth-logo-img"
          width="160"
          height="160"
        />
      </div>
      <router-outlet />
    </div>
  `,
  styles: [
    `
      .auth-shell {
        min-height: 100vh;
        background: var(--color-bg);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 40px 16px;
        position: relative;
      }
      .auth-lang {
        position: absolute;
        top: 20px;
        right: 20px;
      }
      .auth-back {
        position: absolute;
        top: 20px;
        left: 20px;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        font-weight: 600;
        color: var(--color-text-muted);
        text-decoration: none;
        padding: 8px 14px;
        border-radius: var(--radius-full);
        transition:
          background 0.15s,
          color 0.15s;
        &:hover {
          background: var(--color-surface);
          color: var(--color-primary);
        }
        .material-icons-round {
          font-size: 18px;
        }
      }
      .auth-logo-wrap {
        margin-bottom: 32px;
        display: flex;
        justify-content: center;
      }
      .auth-logo-img {
        width: 160px;
        height: 160px;
        object-fit: contain;
        filter: drop-shadow(0 4px 16px rgba(0, 0, 0, 0.12));
      }
    `,
  ],
})
export class AuthShellComponent {}

import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-auth-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: `
    <div class="auth-shell">
      <div class="auth-logo-wrap">
        <img src="assets/logo.png" alt="MicroRecouv" class="auth-logo-img" width="240" height="130">
      </div>
      <router-outlet />
    </div>
  `,
  styles: [`
    .auth-shell {
      min-height: 100vh;
      background: var(--color-bg);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 40px 16px;
    }
    .auth-logo-wrap {
      margin-bottom: 32px;
      display: flex;
      justify-content: center;
    }
    .auth-logo-img {
      width: 200px;
      height: auto;
      filter: drop-shadow(0 4px 16px rgba(0,0,0,.12));
    }
  `]
})
export class AuthShellComponent {}

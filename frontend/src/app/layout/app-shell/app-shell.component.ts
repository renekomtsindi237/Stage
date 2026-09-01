import { Component, ChangeDetectionStrategy, inject } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { SidebarComponent } from "../../shared/components/sidebar/sidebar.component";
import { TopbarComponent } from "../../shared/components/topbar/topbar.component";
import { ChatbotComponent } from "../../shared/components/chatbot/chatbot.component";
import { CommandPaletteComponent } from "../../shared/components/command-palette/command-palette.component";
import { OnboardingComponent } from "../../shared/components/onboarding/onboarding.component";
import { AuthService } from "../../core/auth/auth.service";

@Component({
  selector: "app-shell",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    SidebarComponent,
    TopbarComponent,
    ChatbotComponent,
    CommandPaletteComponent,
    OnboardingComponent,
  ],
  template: `
    <div class="shell">
      <app-sidebar />
      <div class="shell-main">
        <app-topbar />
        <main class="shell-content" [class.shell-content--dense]="dense">
          <router-outlet />
        </main>
      </div>
    </div>
    <app-chatbot />
    <app-command-palette />
    <app-onboarding />
  `,
  styles: [
    `
      .shell {
        display: flex;
        min-height: 100vh;
      }
      .shell-main {
        flex: 1;
        margin-left: var(--sidebar-width);
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .shell-content {
        flex: 1;
        padding: 24px;
        background: var(--color-bg);
        min-height: calc(100vh - var(--topbar-height));
      }
      .shell-content--dense {
        padding: 12px 16px;
      }
    `,
  ],
})
export class AppShellComponent {
  private readonly auth = inject(AuthService);

  get dense(): boolean {
    const r = this.auth.role();
    return r === "AGENT" || r === "CAISSIER" || r === "AGENT_CREDIT";
  }
}

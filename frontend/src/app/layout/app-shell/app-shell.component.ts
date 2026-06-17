import { Component, ChangeDetectionStrategy } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { SidebarComponent } from "../../shared/components/sidebar/sidebar.component";
import { TopbarComponent } from "../../shared/components/topbar/topbar.component";

@Component({
  selector: "app-shell",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  template: `
    <div class="shell">
      <app-sidebar />
      <div class="shell-main">
        <app-topbar />
        <main class="shell-content">
          <router-outlet />
        </main>
      </div>
    </div>
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
    `,
  ],
})
export class AppShellComponent {}

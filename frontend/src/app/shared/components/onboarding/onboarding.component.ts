import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from "@angular/core";
import { TranslatePipe } from "@ngx-translate/core";
import { AuthService } from "../../../core/auth/auth.service";

const ROLE_STEPS: Record<string, string[]> = {
  DIRECTEUR: [
    "onboarding.dir_1",
    "onboarding.dir_2",
    "onboarding.dir_3",
  ],
  CHEF_AGENCE: [
    "onboarding.chef_1",
    "onboarding.chef_2",
    "onboarding.chef_3",
  ],
  RESPONSABLE_RECOUVREMENT: [
    "onboarding.rr_1",
    "onboarding.rr_2",
    "onboarding.rr_3",
  ],
  AGENT: ["onboarding.agent_1", "onboarding.agent_2", "onboarding.agent_3"],
  AGENT_CREDIT: [
    "onboarding.ac_1",
    "onboarding.ac_2",
    "onboarding.ac_3",
  ],
  CAISSIER: [
    "onboarding.caisse_1",
    "onboarding.caisse_2",
    "onboarding.caisse_3",
  ],
  ANALYSTE: [
    "onboarding.anl_1",
    "onboarding.anl_2",
    "onboarding.anl_3",
  ],
  DSI: ["onboarding.dsi_1", "onboarding.dsi_2", "onboarding.dsi_3"],
  SUPPORT: [
    "onboarding.sup_1",
    "onboarding.sup_2",
    "onboarding.sup_3",
  ],
};

@Component({
  selector: "app-onboarding",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    @if (visible()) {
      <div class="onb-backdrop" role="dialog" aria-modal="true">
        <div class="onb-card">
          <h3>{{ "onboarding.title" | translate }}</h3>
          <p class="onb-sub">{{ "onboarding.subtitle" | translate }}</p>
          <ol>
            @for (step of steps(); track step) {
              <li>{{ step | translate }}</li>
            }
          </ol>
          <p class="onb-hint">{{ "onboarding.palette_hint" | translate }}</p>
          <button class="btn btn-primary" type="button" (click)="dismiss()">
            {{ "onboarding.got_it" | translate }}
          </button>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .onb-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(15, 23, 42, 0.5);
        z-index: 11000;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 16px;
      }
      .onb-card {
        background: var(--color-surface);
        border-radius: 14px;
        padding: 28px 24px;
        max-width: 440px;
        width: 100%;
        box-shadow: 0 20px 50px rgba(0, 0, 0, 0.2);
      }
      .onb-card h3 {
        margin: 0 0 4px;
        font-size: 18px;
      }
      .onb-sub,
      .onb-hint {
        color: var(--color-text-muted);
        font-size: 13px;
        margin: 0 0 16px;
      }
      .onb-card ol {
        margin: 0 0 16px;
        padding-left: 20px;
        display: flex;
        flex-direction: column;
        gap: 8px;
        font-size: 14px;
      }
      .onb-card .btn {
        width: 100%;
        justify-content: center;
      }
    `,
  ],
})
export class OnboardingComponent {
  private readonly auth = inject(AuthService);
  private readonly dismissed = signal(false);

  readonly visible = computed(() => {
    if (this.dismissed()) return false;
    const user = this.auth.currentUser();
    if (!user) return false;
    const key = this.storageKey();
    if (!key) return false;
    try {
      return localStorage.getItem(key) !== "1";
    } catch {
      return false;
    }
  });

  readonly steps = computed(() => {
    const role = this.auth.role() ?? "";
    return ROLE_STEPS[role] ?? ROLE_STEPS["DIRECTEUR"];
  });

  private storageKey(): string | null {
    const user = this.auth.currentUser();
    if (!user) return null;
    return `imf_onboarded_${user.username}_${user.role}`;
  }

  dismiss() {
    const key = this.storageKey();
    if (key) {
      try {
        localStorage.setItem(key, "1");
      } catch {
        /* ignore quota */
      }
    }
    this.dismissed.set(true);
  }
}

import { Component } from "@angular/core";
import { trigger, transition, style, animate } from "@angular/animations";
import { LoadingService } from "@core/services/loading.service";

@Component({
  selector: "imf-loading-overlay",
  template: `
    <div class="lo-overlay" *ngIf="loading.isLoading$ | async" [@fadeInOut]>
      <div class="lo-card">
        <imf-ios-spinner size="xl" color="#1e293b"></imf-ios-spinner>
        <p class="lo-label">Traitement en cours…</p>
      </div>
    </div>
  `,
  styles: [
    `
      .lo-overlay {
        position: fixed;
        inset: 0;
        z-index: 8000;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(15, 23, 42, 0.35);
        backdrop-filter: blur(6px);
        -webkit-backdrop-filter: blur(6px);
      }
      .lo-card {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 16px;
        background: #ffffff;
        border-radius: 20px;
        padding: 36px 48px;
        box-shadow:
          0 20px 60px rgba(0, 0, 0, 0.18),
          0 4px 12px rgba(0, 0, 0, 0.08);
      }
      .lo-label {
        font-size: 14px;
        font-weight: 600;
        color: #64748b;
        font-family:
          -apple-system, BlinkMacSystemFont, "SF Pro Display", sans-serif;
        margin: 0;
        letter-spacing: 0.1px;
      }
    `,
  ],
  animations: [
    trigger("fadeInOut", [
      transition(":enter", [
        style({ opacity: 0 }),
        animate("180ms ease-out", style({ opacity: 1 })),
      ]),
      transition(":leave", [animate("150ms ease-in", style({ opacity: 0 }))]),
    ]),
  ],
})
export class LoadingOverlayComponent {
  constructor(public loading: LoadingService) {}
}

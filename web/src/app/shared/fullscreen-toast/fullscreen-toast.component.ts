import { Component, NgZone, OnInit, OnDestroy } from "@angular/core";
import {
  trigger,
  state,
  style,
  transition,
  animate,
} from "@angular/animations";
import { Subscription } from "rxjs";
import {
  FullscreenToastService,
  FullscreenToast,
} from "@core/services/fullscreen-toast.service";
import confetti from "canvas-confetti";

@Component({
  selector: "app-fullscreen-toast",
  templateUrl: "./fullscreen-toast.component.html",
  styleUrls: ["./fullscreen-toast.component.scss"],
  animations: [
    trigger("overlayFade", [
      transition(":enter", [
        style({ opacity: 0 }),
        animate("220ms ease-out", style({ opacity: 1 })),
      ]),
      transition(":leave", [animate("180ms ease-in", style({ opacity: 0 }))]),
    ]),
    trigger("cardPop", [
      transition(":enter", [
        style({ opacity: 0, transform: "scale(0.82) translateY(24px)" }),
        animate(
          "340ms cubic-bezier(0.34, 1.56, 0.64, 1)",
          style({ opacity: 1, transform: "scale(1) translateY(0)" }),
        ),
      ]),
    ]),
    trigger("iconBounce", [
      transition(":enter", [
        style({ transform: "scale(0.4)" }),
        animate(
          "500ms 200ms cubic-bezier(0.34, 1.56, 0.64, 1)",
          style({ transform: "scale(1)" }),
        ),
      ]),
    ]),
  ],
})
export class FullscreenToastComponent implements OnInit, OnDestroy {
  toast: FullscreenToast = {
    type: "success",
    title: "",
    message: "",
    show: false,
  };

  private subscription?: Subscription;

  constructor(
    private toastService: FullscreenToastService,
    private ngZone: NgZone,
  ) {}

  ngOnInit(): void {
    this.subscription = this.toastService.toast$.subscribe((toast) => {
      this.toast = toast;
      if (toast.show && toast.type === "login") {
        this.ngZone.runOutsideAngular(() => this.fireConfetti());
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  close(): void {
    this.toastService.hide();
  }

  private fireConfetti(): void {
    setTimeout(() => {
      confetti({
        particleCount: 100,
        spread: 80,
        origin: { y: 0.55 },
        colors: ["#2563EB", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899"],
        scalar: 1.1,
      });

      const end = Date.now() + 1600;
      const frame = () => {
        confetti({
          particleCount: 2,
          angle: 60,
          spread: 52,
          origin: { x: 0, y: 0.7 },
          colors: ["#2563EB", "#10B981", "#F59E0B"],
        });
        confetti({
          particleCount: 2,
          angle: 120,
          spread: 52,
          origin: { x: 1, y: 0.7 },
          colors: ["#2563EB", "#10B981", "#F59E0B"],
        });
        if (Date.now() < end) requestAnimationFrame(frame);
      };
      frame();
    }, 180);
  }
}

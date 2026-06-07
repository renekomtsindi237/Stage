import { Component, OnInit, OnDestroy } from "@angular/core";
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

  constructor(private toastService: FullscreenToastService) {}

  ngOnInit(): void {
    this.subscription = this.toastService.toast$.subscribe((toast) => {
      this.toast = toast;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  close(): void {
    this.toastService.hide();
  }
}

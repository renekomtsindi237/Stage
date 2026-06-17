import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  trigger,
  state,
  style,
  transition,
  animate,
} from "@angular/animations";
import { Subscription } from "rxjs";
import { ToastService, ToastState } from "../../../core/services/toast.service";

@Component({
  selector: "app-toast",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./toast.component.html",
  styleUrls: ["./toast.component.scss"],
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
export class ToastComponent implements OnInit, OnDestroy {
  private readonly toastService = inject(ToastService);
  private readonly cdr = inject(ChangeDetectorRef);
  private sub?: Subscription;

  toast: ToastState = {
    type: "success",
    title: "",
    message: "",
    show: false,
    duration: 2500,
  };

  ngOnInit() {
    this.sub = this.toastService.toast$.subscribe((t) => {
      this.toast = t;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  close() {
    this.toastService.hide();
  }

  onOverlayClick() {
    if (this.toast.type === "error" || this.toast.type === "warning") {
      this.close();
    }
  }

  get initial(): string {
    return (this.toast.username?.charAt(0) ?? "").toUpperCase();
  }
}

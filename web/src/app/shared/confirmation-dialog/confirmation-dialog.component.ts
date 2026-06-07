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
  ConfirmationDialogService,
  ConfirmationDialog,
} from "@core/services/confirmation-dialog.service";

@Component({
  selector: "app-confirmation-dialog",
  templateUrl: "./confirmation-dialog.component.html",
  styleUrls: ["./confirmation-dialog.component.scss"],
  animations: [
    trigger("fadeIn", [
      transition(":enter", [
        style({ opacity: 0 }),
        animate("250ms ease-out", style({ opacity: 1 })),
      ]),
      transition(":leave", [animate("200ms ease-in", style({ opacity: 0 }))]),
    ]),
    trigger("slideIn", [
      transition(":enter", [
        style({ opacity: 0, transform: "scale(0.9) translateY(-20px)" }),
        animate(
          "350ms cubic-bezier(0.34, 1.56, 0.64, 1)",
          style({ opacity: 1, transform: "scale(1) translateY(0)" }),
        ),
      ]),
      transition(":leave", [
        animate(
          "200ms ease-in",
          style({ opacity: 0, transform: "scale(0.95) translateY(10px)" }),
        ),
      ]),
    ]),
  ],
})
export class ConfirmationDialogComponent implements OnInit, OnDestroy {
  dialog: ConfirmationDialog = {
    show: false,
    title: "",
    message: "",
    confirmText: "Confirmer",
    cancelText: "Annuler",
    type: "warning",
  };

  private subscription?: Subscription;

  constructor(private dialogService: ConfirmationDialogService) {}

  ngOnInit(): void {
    this.subscription = this.dialogService.dialog$.subscribe((dialog) => {
      this.dialog = dialog;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  onConfirm(): void {
    if (this.dialog.onConfirm) {
      this.dialog.onConfirm();
    }
  }

  onCancel(): void {
    if (this.dialog.onCancel) {
      this.dialog.onCancel();
    }
  }

  onOverlayClick(): void {
    this.onCancel();
  }
}

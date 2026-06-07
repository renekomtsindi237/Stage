import { Injectable } from "@angular/core";
import { BehaviorSubject, Observable } from "rxjs";

export interface ConfirmationDialog {
  show: boolean;
  title: string;
  message: string;
  confirmText: string;
  cancelText: string;
  type: "warning" | "danger" | "info";
  onConfirm?: () => void;
  onCancel?: () => void;
}

@Injectable({
  providedIn: "root",
})
export class ConfirmationDialogService {
  private dialogSubject = new BehaviorSubject<ConfirmationDialog>({
    show: false,
    title: "",
    message: "",
    confirmText: "Confirmer",
    cancelText: "Annuler",
    type: "warning",
  });

  dialog$ = this.dialogSubject.asObservable();

  confirm(
    title: string,
    message: string,
    options?: {
      confirmText?: string;
      cancelText?: string;
      type?: "warning" | "danger" | "info";
    },
  ): Promise<boolean> {
    return new Promise((resolve) => {
      this.dialogSubject.next({
        show: true,
        title,
        message,
        confirmText: options?.confirmText || "Confirmer",
        cancelText: options?.cancelText || "Annuler",
        type: options?.type || "warning",
        onConfirm: () => {
          this.hide();
          resolve(true);
        },
        onCancel: () => {
          this.hide();
          resolve(false);
        },
      });
    });
  }

  hide(): void {
    const current = this.dialogSubject.value;
    this.dialogSubject.next({ ...current, show: false });
  }
}

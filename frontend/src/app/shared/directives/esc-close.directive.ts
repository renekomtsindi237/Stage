import { Directive, EventEmitter, HostListener, Output } from "@angular/core";

/** Ferme une overlay/modale avec Échap. */
@Directive({
  selector: "[appEscClose]",
  standalone: true,
})
export class EscCloseDirective {
  @Output() appEscClose = new EventEmitter<void>();

  @HostListener("document:keydown.escape")
  onEsc(): void {
    this.appEscClose.emit();
  }
}

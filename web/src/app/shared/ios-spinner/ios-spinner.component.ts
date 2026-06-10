import { Component, Input } from "@angular/core";

@Component({
  selector: "imf-ios-spinner",
  template: `
    <div
      class="ios-spinner"
      [class]="'ios-spinner--' + size"
      [style.color]="color"
    >
      <div class="ios-blade" *ngFor="let i of blades"></div>
    </div>
  `,
})
export class IosSpinnerComponent {
  @Input() size: "sm" | "md" | "lg" | "xl" = "md";
  @Input() color = "currentColor";
  readonly blades = Array(12).fill(0);
}

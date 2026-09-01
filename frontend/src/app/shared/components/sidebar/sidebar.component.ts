import {
  Component,
  inject,
  ChangeDetectionStrategy,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink, RouterLinkActive } from "@angular/router";
import { AuthService } from "../../../core/auth/auth.service";
import { TranslatePipe } from "@ngx-translate/core";
import {
  MENU_BY_ROLE,
  AppMenuItem,
  groupMenuItems,
  AppMenuGroup,
} from "../../nav/app-menu";

@Component({
  selector: "app-sidebar",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: "./sidebar.component.html",
  styleUrls: ["./sidebar.component.scss"],
})
export class SidebarComponent {
  readonly auth = inject(AuthService);
  readonly collapsed = signal(false);

  get menuItems(): AppMenuItem[] {
    return MENU_BY_ROLE[this.auth.role() ?? ""] ?? [];
  }

  get menuGroups(): AppMenuGroup[] {
    return groupMenuItems(this.menuItems);
  }

  get institutionLabel(): string {
    return this.auth.currentUser()?.imfNom ?? "MicroRecouv";
  }

  get imfLogoSrc(): string {
    return this.auth.currentUser()?.imfLogoUrl ?? "assets/bank.png";
  }

  onImfLogoError(event: Event) {
    const img = event.target as HTMLImageElement;
    if (img.dataset["fb"] === "1") return;
    img.dataset["fb"] = "1";
    img.src = "assets/bank.png";
  }

  get isAgent(): boolean {
    return this.auth.role() === "AGENT";
  }

  toggle() {
    this.collapsed.update((v: boolean) => !v);
  }
}

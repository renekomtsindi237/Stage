import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule, AsyncPipe } from "@angular/common";
import { RouterLink } from "@angular/router";
import { AuthService } from "../../../core/auth/auth.service";
import { NotificationService } from "../../../core/services/notification.service";
import { NotificationPanelComponent } from "../notification-panel/notification-panel.component";
import { ToastService } from "../../../core/services/toast.service";
import { Observable } from "rxjs";

@Component({
  selector: "app-topbar",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, AsyncPipe, RouterLink, NotificationPanelComponent],
  templateUrl: "./topbar.component.html",
  styleUrls: ["./topbar.component.scss"],
})
export class TopbarComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly notifSvc = inject(NotificationService);

  showUserMenu = signal(false);
  showNotifPanel = signal(false);

  unreadCount$!: Observable<number>;

  ngOnInit() {
    this.unreadCount$ = this.notifSvc.unreadCount$;
  }

  get today(): string {
    return new Date().toLocaleDateString("fr-FR", {
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric",
    });
  }

  get roleLabel(): string {
    const map: Record<string, string> = {
      AGENT: "Agent Terrain",
      AGENT_CREDIT: "Agent Crédit",
      AGENT_SAISIE: "Agent Saisie",
      ANALYSTE: "Analyste ML",
      ANALYSTE_ENGAGEMENTS: "Analyste Engagements",
      CHEF_AGENCE: "Chef d'Agence",
      CAISSIER: "Caissier",
      DIRECTEUR: "Directeur",
      DSI: "DSI",
      RESPONSABLE_RECOUVREMENT: "Responsable Recouvrement",
      SUPER_ADMIN: "Super Admin",
      SUPPORT: "Support",
    };
    return map[this.auth.role() ?? ""] ?? this.auth.role() ?? "";
  }

  toggleMenu() {
    this.showUserMenu.update((v: boolean) => !v);
  }
  toggleNotif() {
    this.showNotifPanel.update((v: boolean) => !v);
    this.showUserMenu.set(false);
  }
  closeNotif() {
    this.showNotifPanel.set(false);
  }

  contactSupport() {
    const subject = encodeURIComponent("Demande de support — MicroRecouv");
    const body = encodeURIComponent(
      `Bonjour,\n\nJe suis ${this.auth.fullName()} (${this.roleLabel}).\n\nMon problème :\n\n`,
    );
    window.open(
      `mailto:support@microrecouv.cm?subject=${subject}&body=${body}`,
      "_blank",
    );
  }

  logout() {
    this.showUserMenu.set(false);
    const name = this.auth.fullName();
    this.auth.logout();
    this.toast.showLogout(name);
  }
}

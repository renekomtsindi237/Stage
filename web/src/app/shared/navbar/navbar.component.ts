import { Component, EventEmitter, Input, Output, OnInit } from "@angular/core";
import { AuthService } from "@core/services/auth.service";
import { ConfirmationDialogService } from "@core/services/confirmation-dialog.service";
import { FullscreenToastService } from "@core/services/fullscreen-toast.service";
import { NotificationService } from "@core/services/notification.service";
import { OnlineUsersService } from "@core/services/online-users.service";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { ChangePasswordComponent } from "../change-password/change-password.component";
import { ContactSupportDialogComponent } from "../contact-support/contact-support.component";
import { Observable } from "rxjs";

@Component({
  selector: "imf-navbar",
  templateUrl: "./navbar.component.html",
  styleUrls: ["./navbar.component.scss"],
})
export class NavbarComponent implements OnInit {
  @Output() toggleSidenav = new EventEmitter<void>();
  @Input() alerteBadge = 0;

  showNotifPanel = false;
  unreadCount$!: Observable<number>;
  onlineCount$!: Observable<number>;

  readonly today = (() => {
    const s = new Date().toLocaleDateString("fr-FR", {
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric",
    });
    return s.charAt(0).toUpperCase() + s.slice(1);
  })();

  constructor(
    public auth: AuthService,
    private confirmDialog: ConfirmationDialogService,
    private toast: FullscreenToastService,
    public notifService: NotificationService,
    public onlineUsers: OnlineUsersService,
    private dialog: MatDialog,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.unreadCount$ = this.notifService.unreadCount$;
    this.onlineCount$ = this.onlineUsers.count$;
  }

  get isSuperAdmin(): boolean {
    return this.auth.isSuperAdmin();
  }

  toggleNotifPanel(): void {
    this.showNotifPanel = !this.showNotifPanel;
  }

  openChangePassword(): void {
    this.dialog.open(ChangePasswordComponent, {
      width: "460px",
      panelClass: "cp-dialog-panel",
      disableClose: false,
    });
  }

  async logout(): Promise<void> {
    const confirmed = await this.confirmDialog.confirm(
      "Confirmation de déconnexion",
      "Êtes-vous sûr de vouloir vous déconnecter ?",
      {
        confirmText: "Oui, me déconnecter",
        cancelText: "Annuler",
        type: "warning",
      },
    );

    if (confirmed) {
      this.toast.showLogout(this.auth.getUsername() ?? undefined);
      this.auth.logout();
    }
  }

  openSupportDialog(): void {
    this.dialog.open(ContactSupportDialogComponent, {
      width: "560px",
      maxHeight: "90vh",
      panelClass: "support-dialog-panel",
      disableClose: false,
    });
  }

  navigateToProfile(): void {
    this.router.navigate(["/profile"]);
  }
}

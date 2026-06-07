import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { AuthService } from '@core/services/auth.service';
import { ConfirmationDialogService } from '@core/services/confirmation-dialog.service';
import { FullscreenToastService } from '@core/services/fullscreen-toast.service';
import { NotificationService } from '@core/services/notification.service';
import { OnlineUsersService } from '@core/services/online-users.service';
import { MatDialog } from '@angular/material/dialog';
import { ChangePasswordComponent } from '../change-password/change-password.component';
import { Observable } from 'rxjs';

@Component({
  selector: 'imf-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent implements OnInit {

  @Output() toggleSidenav = new EventEmitter<void>();
  @Input() alerteBadge = 0;

  showNotifPanel = false;
  unreadCount$!: Observable<number>;
  onlineCount$!: Observable<number>;

  constructor(
    public auth: AuthService,
    private confirmDialog: ConfirmationDialogService,
    private toast: FullscreenToastService,
    public notifService: NotificationService,
    public onlineUsers: OnlineUsersService,
    private dialog: MatDialog,
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
      width: '460px',
      panelClass: 'cp-dialog-panel',
      disableClose: false,
    });
  }

  async logout(): Promise<void> {
    const confirmed = await this.confirmDialog.confirm(
      'Confirmation de déconnexion',
      'Êtes-vous sûr de vouloir vous déconnecter ?',
      {
        confirmText: 'Oui, me déconnecter',
        cancelText: 'Annuler',
        type: 'warning'
      }
    );

    if (confirmed) {
      this.toast.showLogout(this.auth.getUsername() ?? undefined);
      this.auth.logout();
    }
  }

  openProfileMenu(): void {
    // TODO: Ouvrir un menu ou modal pour changer la photo de profil
    // Pour l'instant, on peut utiliser un input file
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = (event: any) => {
      const file = event.target.files[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (e: any) => {
          this.auth.setUserAvatar(e.target.result);
        };
        reader.readAsDataURL(file);
      }
    };
    input.click();
  }
}

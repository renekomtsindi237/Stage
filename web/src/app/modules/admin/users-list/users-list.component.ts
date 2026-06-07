import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { AdminService, ImfInfo, ROLE_LABELS } from '../admin.service';
import { UserResponse } from '@core/models/user.model';
import { AuthService } from '@core/services/auth.service';
import { ConfirmationDialogService } from '@core/services/confirmation-dialog.service';
import { ImageUploadService } from '@core/services/image-upload.service';

@Component({
  selector: 'imf-users-list',
  templateUrl: './users-list.component.html',
  styleUrls: ['./users-list.component.scss']
})
export class UsersListComponent implements OnInit {

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  imfInfo: ImfInfo | null = null;
  users: UserResponse[] = [];
  filteredUsers: UserResponse[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  error = '';
  searchQuery = '';

  readonly displayedColumns = ['avatar', 'username', 'role', 'zone', 'statut', 'lastLogin', 'actions'];

  avatarUploadingId: number | null = null;

  constructor(
    public auth: AuthService,
    private adminService: AdminService,
    private snackBar: MatSnackBar,
    private confirmDialog: ConfirmationDialogService,
    private imageUpload: ImageUploadService,
  ) {}

  ngOnInit(): void {
    this.adminService.getImfInfo().subscribe({ next: info => this.imfInfo = info });
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.adminService.listUsers(this.page, this.pageSize).subscribe({
      next: (data) => {
        this.users = data.content;
        this.filteredUsers = data.content;
        this.total = data.total;
        this.loading = false;
      },
      error: () => {
        this.error = 'Impossible de charger les utilisateurs.';
        this.loading = false;
      }
    });
  }

  applyFilter(event: Event): void {
    const q = (event.target as HTMLInputElement).value.toLowerCase().trim();
    this.searchQuery = q;
    this.filteredUsers = q
      ? this.users.filter(u =>
          u.username.toLowerCase().includes(q) ||
          u.role.toLowerCase().includes(q) ||
          (u.zoneId ?? '').toLowerCase().includes(q)
        )
      : [...this.users];
  }

  onPageChange(event: PageEvent): void {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadUsers();
  }

  toggleActif(user: UserResponse): void {
    const action$ = user.actif
      ? this.adminService.deactivate(user.id)
      : this.adminService.activate(user.id);

    action$.subscribe({
      next: (updated) => {
        this.users = this.users.map(u => u.id === updated.id ? updated : u);
        this.filteredUsers = this.filteredUsers.map(u => u.id === updated.id ? updated : u);
        const msg = updated.actif ? 'Compte réactivé' : 'Compte désactivé';
        this.snackBar.open(msg, 'OK', { duration: 3000 });
      },
      error: () => this.snackBar.open('Opération échouée', 'OK', { duration: 3000 })
    });
  }

  roleLabel(role: string): string {
    return ROLE_LABELS[role] ?? role;
  }

  roleIcon(role: string): string {
    const icons: Record<string, string> = {
      DSI: 'admin_panel_settings',
      DIRECTEUR: 'supervisor_account',
      RESPONSABLE_RECOUVREMENT: 'policy',
      ANALYSTE: 'analytics',
      AGENT: 'badge',
    };
    return icons[role] ?? 'person';
  }

  async resetPassword(user: UserResponse): Promise<void> {
    const newPassword = window.prompt(
      `Nouveau mot de passe pour « ${user.username} » (minimum 8 caractères) :`
    );
    if (newPassword === null) return;
    if (newPassword.length < 8) {
      this.snackBar.open('Le mot de passe doit contenir au moins 8 caractères', 'OK', { duration: 3000 });
      return;
    }

    const confirmed = await this.confirmDialog.confirm(
      'Réinitialiser le mot de passe',
      `Confirmer la réinitialisation du mot de passe de <strong>${user.username}</strong> ?<br>`
      + `<em>Communiquez le nouveau mot de passe directement à l'utilisateur.</em>`,
      { confirmText: 'Réinitialiser', cancelText: 'Annuler', type: 'warning' }
    );
    if (!confirmed) return;

    this.adminService.resetPassword(user.id, newPassword).subscribe({
      next: () => this.snackBar.open('Mot de passe réinitialisé', 'OK', { duration: 4000 }),
      error: () => this.snackBar.open('Échec de la réinitialisation', 'OK', { duration: 3000 })
    });
  }

  pickAvatarFor(user: UserResponse): void {
    this.imageUpload.pickImage((file) => {
      this.avatarUploadingId = user.id;
      this.imageUpload.uploadUserAvatar(user.id, file).subscribe({
        next: (updated) => {
          this.users = this.users.map(u => u.id === updated.id ? updated : u);
          this.filteredUsers = this.filteredUsers.map(u => u.id === updated.id ? updated : u);
          this.avatarUploadingId = null;
          this.snackBar.open('Photo mise à jour', 'OK', { duration: 3000 });
        },
        error: () => {
          this.avatarUploadingId = null;
          this.snackBar.open('Échec de l\'upload', 'OK', { duration: 3000 });
        },
      });
    });
  }

  get totalActive(): number { return this.users.filter(u => u.actif).length; }
}

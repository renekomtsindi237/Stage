import { Component } from "@angular/core";
import { AuthService } from "@core/services/auth.service";
import { Role } from "@core/models/auth.model";
import { ConfirmationDialogService } from "@core/services/confirmation-dialog.service";
import { FullscreenToastService } from "@core/services/fullscreen-toast.service";

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles?: Role[];
  /** Si true, visible UNIQUEMENT pour SUPER_ADMIN */
  superAdminOnly?: boolean;
  /** Si true, caché pour SUPER_ADMIN */
  hideSuperAdmin?: boolean;
  /** Si true, routerLinkActive utilise exact: true */
  exactMatch?: boolean;
}

@Component({
  selector: "imf-sidebar",
  templateUrl: "./sidebar.component.html",
  styleUrls: ["./sidebar.component.scss"],
})
export class SidebarComponent {
  // Items réservés au SUPER_ADMIN
  readonly superAdminItems: NavItem[] = [
    {
      label: "Vue plateforme",
      icon: "dashboard_customize",
      route: "/platform",
      superAdminOnly: true,
      exactMatch: true,
    },
    {
      label: "Gestion des IMF",
      icon: "corporate_fare",
      route: "/platform/imf",
      superAdminOnly: true,
      exactMatch: false,
    },
  ];

  // Items pour les utilisateurs IMF (DSI, DIRECTEUR, etc.)
  readonly navItems: NavItem[] = [
    {
      label: "Tableau de bord",
      icon: "dashboard",
      route: "/dashboard",
      hideSuperAdmin: true,
    },
    {
      label: "Alertes",
      icon: "warning_amber",
      route: "/admin/alertes",
      hideSuperAdmin: true,
      roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI"],
    },
    {
      label: "Prêts",
      icon: "account_balance_wallet",
      route: "/admin/prets",
      hideSuperAdmin: true,
      roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI"],
    },
    {
      label: "Clients",
      icon: "people",
      route: "/admin/clients",
      hideSuperAdmin: true,
      roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI"],
    },
    {
      label: "Reporting",
      icon: "bar_chart",
      route: "/admin/reporting",
      hideSuperAdmin: true,
      roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI"],
    },
    {
      label: "Recouvrement",
      icon: "account_balance",
      route: "/admin/recouvrement",
      hideSuperAdmin: true,
      roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI"],
    },
    {
      label: "KYC / Conformité",
      icon: "verified_user",
      route: "/admin/kyc",
      hideSuperAdmin: true,
      roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI"],
    },
    {
      label: "Administration",
      icon: "admin_panel_settings",
      route: "/admin",
      hideSuperAdmin: true,
      roles: ["DSI"],
    },
    {
      label: "Agences",
      icon: "business",
      route: "/admin/agences",
      hideSuperAdmin: true,
      roles: ["DSI"],
    },
  ];

  constructor(
    public auth: AuthService,
    private confirmDialog: ConfirmationDialogService,
    private toast: FullscreenToastService,
  ) {}

  get isSuperAdmin(): boolean {
    return this.auth.isSuperAdmin();
  }
  get isDsi(): boolean {
    return this.auth.isDsi();
  }
  get imfNom(): string | null {
    return this.auth.getImfNom();
  }
  get imfCode(): string | null {
    return this.auth.getImfCode();
  }

  isVisible(item: NavItem): boolean {
    if (item.superAdminOnly) return this.isSuperAdmin;
    if (item.hideSuperAdmin && this.isSuperAdmin) return false;
    if (!item.roles || item.roles.length === 0) return true;
    return this.auth.hasRole(...item.roles);
  }

  get displayItems(): NavItem[] {
    return this.isSuperAdmin
      ? this.superAdminItems
      : this.navItems.filter((i) => this.isVisible(i));
  }

  changeAvatar(): void {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
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
}

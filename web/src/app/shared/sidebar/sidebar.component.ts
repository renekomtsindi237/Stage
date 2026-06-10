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
  superAdminOnly?: boolean;
  supportOnly?: boolean;
  hideSuperAdmin?: boolean;
  hideSupport?: boolean;
  exactMatch?: boolean;
}

@Component({
  selector: "imf-sidebar",
  templateUrl: "./sidebar.component.html",
  styleUrls: ["./sidebar.component.scss"],
})
export class SidebarComponent {
  readonly superAdminItems: NavItem[] = [
    { label: "Dashboard plateforme", icon: "space_dashboard",  route: "/platform",       superAdminOnly: true, exactMatch: true },
    { label: "Gestion des IMF",      icon: "corporate_fare",   route: "/platform/imf",   superAdminOnly: true },
    { label: "Audit trail global",   icon: "receipt_long",     route: "/platform/audit", superAdminOnly: true },
    { label: "Configuration",        icon: "settings",         route: "/platform/config",superAdminOnly: true },
    { label: "Mon Profil",           icon: "account_circle",   route: "/profile",        superAdminOnly: true },
  ];

  readonly supportItems: NavItem[] = [
    { label: "Vue d'ensemble", icon: "monitor_heart", route: "/support", supportOnly: true, exactMatch: true },
    { label: "Infrastructure", icon: "dns", route: "/support/infrastructure", supportOnly: true },
    { label: "Traitements planifiés", icon: "schedule", route: "/support/traitements", supportOnly: true },
    { label: "Journaux système", icon: "article", route: "/support/journaux", supportOnly: true },
    { label: "Alertes système", icon: "notifications_active", route: "/support/alertes", supportOnly: true },
  ];

  readonly navItems: NavItem[] = [
    { label: "Tableau de bord", icon: "dashboard", route: "/dashboard", hideSuperAdmin: true, hideSupport: true },
    // Directeur
    { label: "Carte agents", icon: "map", route: "/admin/carte", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR"] },
    { label: "Alertes ML", icon: "warning_amber", route: "/admin/alertes", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT"] },
    { label: "KPI Portefeuille", icon: "bar_chart", route: "/admin/reporting", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR"] },
    { label: "Clients", icon: "people", route: "/admin/clients", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT"] },
    { label: "KYC", icon: "verified_user", route: "/admin/kyc", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT"] },
    { label: "Agences", icon: "business", route: "/admin/agences", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR"] },
    { label: "Utilisateurs", icon: "manage_accounts", route: "/admin/users", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR"] },
    { label: "Rapports", icon: "description", route: "/admin/rapports", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR"] },
    // Recouvrement
    { label: "Dossiers", icon: "folder_open", route: "/admin/recouvrement", hideSuperAdmin: true, hideSupport: true, roles: ["RESPONSABLE_RECOUVREMENT"] },
    { label: "Tableau terrain", icon: "location_on", route: "/admin/terrain", hideSuperAdmin: true, hideSupport: true, roles: ["RESPONSABLE_RECOUVREMENT"] },
    // Analyste
    { label: "Scoring clients", icon: "insights", route: "/analyste/scoring", hideSuperAdmin: true, hideSupport: true, roles: ["ANALYSTE"] },
    { label: "Suivi des traitements", icon: "sync_alt", route: "/analyste/traitements", hideSuperAdmin: true, hideSupport: true, roles: ["ANALYSTE"] },
    { label: "Qualité du modèle", icon: "model_training", route: "/analyste/modele", hideSuperAdmin: true, hideSupport: true, roles: ["ANALYSTE"] },
    // DSI
    { label: "Conformité RGPD", icon: "shield", route: "/dsi/rgpd", hideSuperAdmin: true, hideSupport: true, roles: ["DSI"] },
    { label: "Audit trail", icon: "receipt_long", route: "/dsi/audit", hideSuperAdmin: true, hideSupport: true, roles: ["DSI"] },
    { label: "Consentements", icon: "how_to_reg", route: "/dsi/consentements", hideSuperAdmin: true, hideSupport: true, roles: ["DSI"] },
    { label: "Santé des services", icon: "health_and_safety", route: "/dsi/monitoring", hideSuperAdmin: true, hideSupport: true, roles: ["DSI"] },
    { label: "Configuration", icon: "settings", route: "/dsi/configuration", hideSuperAdmin: true, hideSupport: true, roles: ["DSI"] },
    // Commun
    { label: "Prêts", icon: "account_balance_wallet", route: "/admin/prets", hideSuperAdmin: true, hideSupport: true, roles: ["DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE"] },
  ];

  constructor(
    public auth: AuthService,
    private confirmDialog: ConfirmationDialogService,
    private toast: FullscreenToastService,
  ) {}

  get isSuperAdmin(): boolean { return this.auth.isSuperAdmin(); }
  get isSupport(): boolean { return this.auth.hasRole("SUPPORT"); }
  get isDsi(): boolean { return this.auth.isDsi(); }
  get imfNom(): string | null { return this.auth.getImfNom(); }
  get imfCode(): string | null { return this.auth.getImfCode(); }
  get imfLogo(): string | null {
    const code = this.auth.getImfCode();
    return code ? this.auth.getImfLogo(code) : null;
  }

  isVisible(item: NavItem): boolean {
    if (item.superAdminOnly) return this.isSuperAdmin;
    if (item.supportOnly) return this.isSupport;
    if (item.hideSuperAdmin && this.isSuperAdmin) return false;
    if (item.hideSupport && this.isSupport) return false;
    if (!item.roles || item.roles.length === 0) return true;
    return this.auth.hasRole(...item.roles);
  }

  get displayItems(): NavItem[] {
    if (this.isSuperAdmin) return this.superAdminItems;
    if (this.isSupport) return this.supportItems;
    return this.navItems.filter((i) => this.isVisible(i));
  }

  get sectionLabel(): string {
    if (this.isSuperAdmin) return "Plateforme";
    if (this.isSupport) return "Système";
    if (this.isDsi) return "Administration IMF";
    return "Navigation";
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

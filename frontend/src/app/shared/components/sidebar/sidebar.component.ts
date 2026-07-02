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

interface MenuItem {
  label: string;
  icon: string;
  route: string;
  exact?: boolean;
}

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

  private readonly menuByRole: Record<string, MenuItem[]> = {
    AGENT: [
      { label: "sidebar.menu.accueil", icon: "home", route: "/agent", exact: true },
      { label: "sidebar.menu.mes_clients", icon: "people", route: "/agent/clients" },
      { label: "sidebar.menu.historique", icon: "history", route: "/agent/historique" },
    ],
    ANALYSTE: [
      { label: "sidebar.menu.dashboard", icon: "dashboard", route: "/analyste/dashboard" },
      { label: "sidebar.menu.scoring", icon: "insights", route: "/analyste/scoring" },
      {
        label: "sidebar.menu.pipeline",
        icon: "account_tree",
        route: "/analyste/pipeline",
      },
      { label: "sidebar.menu.drift", icon: "trending_up", route: "/analyste/drift" },
      {
        label: "sidebar.menu.mcrs_demo",
        icon: "model_training",
        route: "/analyste/mcrs-demo",
      },
    ],
    DIRECTEUR: [
      { label: "sidebar.menu.dashboard",         icon: "dashboard",       route: "/directeur/dashboard" },
      { label: "sidebar.menu.alertes_ml",        icon: "warning_amber",   route: "/directeur/alertes" },
      { label: "sidebar.menu.kpi_portefeuille",  icon: "analytics",       route: "/directeur/kpi" },
      { label: "sidebar.menu.scoring",           icon: "insights",        route: "/directeur/scoring" },
      { label: "sidebar.menu.recouvrement",      icon: "account_balance", route: "/directeur/recouvrement" },
      { label: "sidebar.menu.analytics",         icon: "account_tree",    route: "/directeur/analytics" },
      { label: "sidebar.menu.clients",           icon: "people",          route: "/directeur/clients" },
      { label: "sidebar.menu.carte_agents",      icon: "map",             route: "/directeur/carte-agents" },
      { label: "sidebar.menu.kyc",               icon: "verified_user",   route: "/directeur/kyc" },
      { label: "sidebar.menu.agences",           icon: "apartment",       route: "/directeur/agences" },
      { label: "sidebar.menu.utilisateurs",      icon: "manage_accounts", route: "/directeur/equipe" },
      { label: "sidebar.menu.rapports",          icon: "description",     route: "/directeur/rapports" },
      { label: "sidebar.menu.delegations",       icon: "swap_horiz",      route: "/directeur/delegations" },
    ],
    DSI: [
      { label: "sidebar.menu.dashboard", icon: "dashboard", route: "/dsi/dashboard" },
      { label: "sidebar.menu.utilisateurs", icon: "manage_accounts", route: "/dsi/users" },
      { label: "sidebar.menu.audit_trail", icon: "history_edu", route: "/dsi/audit" },
      { label: "sidebar.menu.violations_rgpd", icon: "security", route: "/dsi/rgpd" },
      { label: "sidebar.menu.monitoring", icon: "monitor_heart", route: "/dsi/monitoring" },
      { label: "sidebar.menu.parametres_imf", icon: "settings", route: "/dsi/settings" },
      {
        label: "sidebar.menu.delegations",
        icon: "swap_horiz",
        route: "/dsi/delegations",
      },
    ],
    SUPER_ADMIN: [
      {
        label: "sidebar.menu.dashboard_plateforme",
        icon: "domain",
        route: "/platform/dashboard",
      },
      { label: "sidebar.menu.gestion_imf", icon: "business", route: "/platform/imfs" },
      { label: "sidebar.menu.audit_global", icon: "history_edu", route: "/platform/audit" },
    ],
    RESPONSABLE_RECOUVREMENT: [
      {
        label: "sidebar.menu.dashboard",
        icon: "dashboard",
        route: "/recouvrement/dashboard",
      },
      {
        label: "sidebar.menu.creances",
        icon: "account_balance",
        route: "/recouvrement/creances",
      },
      { label: "sidebar.menu.actions", icon: "task_alt", route: "/recouvrement/actions" },
      {
        label: "sidebar.menu.alertes",
        icon: "warning_amber",
        route: "/recouvrement/alertes",
      },
    ],
    CHEF_AGENCE: [
      {
        label: "sidebar.menu.dashboard_agence",
        icon: "dashboard",
        route: "/chef-agence/dashboard",
      },
      {
        label: "sidebar.menu.dossiers_credit",
        icon: "folder_open",
        route: "/chef-agence/dossiers",
      },
      { label: "sidebar.menu.mon_equipe", icon: "groups", route: "/chef-agence/equipe" },
    ],
    AGENT_CREDIT: [
      { label: "sidebar.menu.dashboard", icon: "dashboard", route: "/credit/dashboard" },
      {
        label: "sidebar.menu.nouveau_dossier",
        icon: "add_circle",
        route: "/credit/nouveau-dossier",
      },
      { label: "sidebar.menu.mes_dossiers", icon: "folder_open", route: "/credit/dossiers" },
    ],
    ANALYSTE_ENGAGEMENTS: [
      {
        label: "sidebar.menu.conformite",
        icon: "verified",
        route: "/engagements/conformite",
      },
      {
        label: "sidebar.menu.dossiers",
        icon: "folder_open",
        route: "/engagements/dossiers",
      },
    ],
    AGENT_SAISIE: [
      { label: "sidebar.menu.contrats", icon: "description", route: "/saisie/contrats" },
      { label: "sidebar.menu.dossiers_credit", icon: "folder_special", route: "/saisie/dossiers-credit" },
    ],
    CAISSIER: [
      {
        label: "sidebar.menu.tableau_de_bord",
        icon: "dashboard",
        route: "/caisse/dashboard",
      },
      {
        label: "sidebar.menu.decaissement",
        icon: "payments",
        route: "/caisse/decaissement",
      },
      {
        label: "sidebar.menu.encaissement",
        icon: "account_balance_wallet",
        route: "/caisse/encaissement",
      },
    ],
    SUPPORT: [
      {
        label: "sidebar.menu.tickets",
        icon: "confirmation_number",
        route: "/support/tickets",
      },
      {
        label: "sidebar.menu.monitoring",
        icon: "monitor_heart",
        route: "/support/monitoring",
      },
    ],
  };

  get menuItems(): MenuItem[] {
    return this.menuByRole[this.auth.role() ?? ""] ?? [];
  }

  get institutionLabel(): string {
    return this.auth.currentUser()?.imfNom ?? "MicroRecouv";
  }

  get imfLogoSrc(): string {
    return this.auth.currentUser()?.imfLogoUrl ?? "assets/bank.png";
  }

  get isAgent(): boolean {
    return this.auth.role() === "AGENT";
  }

  toggle() {
    this.collapsed.update((v: boolean) => !v);
  }
}

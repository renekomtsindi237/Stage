import {
  Component,
  inject,
  ChangeDetectionStrategy,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink, RouterLinkActive } from "@angular/router";
import { AuthService } from "../../../core/auth/auth.service";

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
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: "./sidebar.component.html",
  styleUrls: ["./sidebar.component.scss"],
})
export class SidebarComponent {
  readonly auth = inject(AuthService);
  readonly collapsed = signal(false);

  private readonly menuByRole: Record<string, MenuItem[]> = {
    AGENT: [
      { label: "Accueil", icon: "home", route: "/agent", exact: true },
      { label: "Mes Clients", icon: "people", route: "/agent/clients" },
      { label: "Historique", icon: "history", route: "/agent/historique" },
    ],
    ANALYSTE: [
      { label: "Dashboard", icon: "dashboard", route: "/analyste/dashboard" },
      { label: "Scoring MCRS", icon: "insights", route: "/analyste/scoring" },
      {
        label: "Pipeline Airflow",
        icon: "account_tree",
        route: "/analyste/pipeline",
      },
      { label: "Drift ML", icon: "trending_up", route: "/analyste/drift" },
      {
        label: "Démo MCRS",
        icon: "model_training",
        route: "/analyste/mcrs-demo",
      },
    ],
    DIRECTEUR: [
      { label: "Dashboard", icon: "dashboard", route: "/directeur/dashboard" },
      {
        label: "Alertes ML",
        icon: "warning_amber",
        route: "/directeur/alertes",
      },
      { label: "KPI Portefeuille", icon: "analytics", route: "/directeur/kpi" },
      { label: "Clients", icon: "people", route: "/directeur/clients" },
      { label: "Carte Agents", icon: "map", route: "/directeur/carte-agents" },
      { label: "KYC", icon: "verified_user", route: "/directeur/kyc" },
      { label: "Agences", icon: "apartment", route: "/directeur/agences" },
      {
        label: "Utilisateurs",
        icon: "manage_accounts",
        route: "/directeur/equipe",
      },
      { label: "Rapports", icon: "description", route: "/directeur/rapports" },
      {
        label: "Délégations",
        icon: "swap_horiz",
        route: "/directeur/delegations",
      },
    ],
    DSI: [
      { label: "Dashboard", icon: "dashboard", route: "/dsi/dashboard" },
      { label: "Utilisateurs", icon: "manage_accounts", route: "/dsi/users" },
      { label: "Audit Trail", icon: "history_edu", route: "/dsi/audit" },
      { label: "Violations RGPD", icon: "security", route: "/dsi/rgpd" },
      { label: "Monitoring", icon: "monitor_heart", route: "/dsi/monitoring" },
      { label: "Paramètres IMF", icon: "settings", route: "/dsi/settings" },
      {
        label: "Délégations",
        icon: "swap_horiz",
        route: "/dsi/delegations",
      },
    ],
    SUPER_ADMIN: [
      {
        label: "Dashboard Plateforme",
        icon: "domain",
        route: "/platform/dashboard",
      },
      { label: "Gestion IMF", icon: "business", route: "/platform/imfs" },
      { label: "Audit Global", icon: "history_edu", route: "/platform/audit" },
    ],
    RESPONSABLE_RECOUVREMENT: [
      {
        label: "Dashboard",
        icon: "dashboard",
        route: "/recouvrement/dashboard",
      },
      {
        label: "Créances",
        icon: "account_balance",
        route: "/recouvrement/creances",
      },
      { label: "Actions", icon: "task_alt", route: "/recouvrement/actions" },
      {
        label: "Alertes",
        icon: "warning_amber",
        route: "/recouvrement/alertes",
      },
    ],
    CHEF_AGENCE: [
      {
        label: "Dashboard Agence",
        icon: "dashboard",
        route: "/chef-agence/dashboard",
      },
      {
        label: "Dossiers Crédit",
        icon: "folder_open",
        route: "/chef-agence/dossiers",
      },
      { label: "Mon Équipe", icon: "groups", route: "/chef-agence/equipe" },
    ],
    AGENT_CREDIT: [
      { label: "Dashboard", icon: "dashboard", route: "/credit/dashboard" },
      {
        label: "Nouveau Dossier",
        icon: "add_circle",
        route: "/credit/nouveau-dossier",
      },
      { label: "Mes Dossiers", icon: "folder_open", route: "/credit/dossiers" },
    ],
    ANALYSTE_ENGAGEMENTS: [
      {
        label: "Conformité COBAC",
        icon: "verified",
        route: "/engagements/conformite",
      },
      {
        label: "Dossiers",
        icon: "folder_open",
        route: "/engagements/dossiers",
      },
    ],
    AGENT_SAISIE: [
      { label: "Contrats", icon: "description", route: "/saisie/contrats" },
    ],
    CAISSIER: [
      {
        label: "Tableau de bord",
        icon: "dashboard",
        route: "/caisse/dashboard",
      },
      {
        label: "Décaissement",
        icon: "payments",
        route: "/caisse/decaissement",
      },
      {
        label: "Encaissement",
        icon: "account_balance_wallet",
        route: "/caisse/encaissement",
      },
    ],
    SUPPORT: [
      {
        label: "Tickets",
        icon: "confirmation_number",
        route: "/support/tickets",
      },
      {
        label: "Monitoring",
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

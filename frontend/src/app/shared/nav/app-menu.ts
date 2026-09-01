export interface AppMenuItem {
  label: string;
  icon: string;
  route: string;
  exact?: boolean;
  /** Clé i18n de groupe (optionnel). */
  group?: string;
}

export const MENU_BY_ROLE: Record<string, AppMenuItem[]> = {
  AGENT: [
    {
      label: "sidebar.menu.accueil",
      icon: "home",
      route: "/agent",
      exact: true,
    },
    {
      label: "sidebar.menu.mes_clients",
      icon: "people",
      route: "/agent/clients",
    },
    {
      label: "sidebar.menu.historique",
      icon: "history",
      route: "/agent/historique",
    },
  ],
  ANALYSTE: [
    {
      label: "sidebar.menu.dashboard",
      icon: "dashboard",
      route: "/analyste/dashboard",
    },
    {
      label: "sidebar.menu.scoring",
      icon: "insights",
      route: "/analyste/scoring",
    },
    {
      label: "sidebar.menu.pipeline",
      icon: "account_tree",
      route: "/analyste/pipeline",
    },
    {
      label: "sidebar.menu.drift",
      icon: "trending_up",
      route: "/analyste/drift",
    },
  ],
  DIRECTEUR: [
    {
      label: "sidebar.menu.dashboard",
      icon: "dashboard",
      route: "/directeur/dashboard",
      group: "sidebar.group_pilotage",
    },
    {
      label: "sidebar.menu.alertes_ml",
      icon: "warning_amber",
      route: "/directeur/alertes",
      group: "sidebar.group_pilotage",
    },
    {
      label: "sidebar.menu.kpi_portefeuille",
      icon: "analytics",
      route: "/directeur/kpi",
      group: "sidebar.group_pilotage",
    },
    {
      label: "sidebar.menu.scoring",
      icon: "insights",
      route: "/directeur/scoring",
      group: "sidebar.group_portefeuille",
    },
    {
      label: "sidebar.menu.recouvrement",
      icon: "account_balance",
      route: "/directeur/recouvrement",
      group: "sidebar.group_portefeuille",
    },
    {
      label: "sidebar.menu.analytics",
      icon: "account_tree",
      route: "/directeur/analytics",
      group: "sidebar.group_portefeuille",
    },
    {
      label: "sidebar.menu.clients",
      icon: "people",
      route: "/directeur/clients",
      group: "sidebar.group_portefeuille",
    },
    {
      label: "sidebar.menu.kyc",
      icon: "verified_user",
      route: "/directeur/kyc",
      group: "sidebar.group_portefeuille",
    },
    {
      label: "sidebar.menu.carte_agents",
      icon: "map",
      route: "/directeur/carte-agents",
      group: "sidebar.group_reseau",
    },
    {
      label: "sidebar.menu.agences",
      icon: "apartment",
      route: "/directeur/agences",
      group: "sidebar.group_reseau",
    },
    {
      label: "sidebar.menu.utilisateurs",
      icon: "manage_accounts",
      route: "/directeur/equipe",
      group: "sidebar.group_reseau",
    },
    {
      label: "sidebar.menu.delegations",
      icon: "swap_horiz",
      route: "/directeur/delegations",
      group: "sidebar.group_reseau",
    },
    {
      label: "sidebar.menu.rapports",
      icon: "description",
      route: "/directeur/rapports",
      group: "sidebar.group_reporting",
    },
  ],
  DSI: [
    {
      label: "sidebar.menu.dashboard",
      icon: "dashboard",
      route: "/dsi/dashboard",
      group: "sidebar.group_pilotage",
    },
    {
      label: "sidebar.menu.monitoring",
      icon: "monitor_heart",
      route: "/dsi/monitoring",
      group: "sidebar.group_pilotage",
    },
    {
      label: "sidebar.menu.utilisateurs",
      icon: "manage_accounts",
      route: "/dsi/users",
      group: "sidebar.group_gouvernance",
    },
    {
      label: "sidebar.menu.audit_trail",
      icon: "history_edu",
      route: "/dsi/audit",
      group: "sidebar.group_gouvernance",
    },
    {
      label: "sidebar.menu.violations_rgpd",
      icon: "security",
      route: "/dsi/rgpd",
      group: "sidebar.group_gouvernance",
    },
    {
      label: "sidebar.menu.delegations",
      icon: "swap_horiz",
      route: "/dsi/delegations",
      group: "sidebar.group_gouvernance",
    },
    {
      label: "sidebar.menu.parametres_imf",
      icon: "settings",
      route: "/dsi/settings",
      group: "sidebar.group_params",
    },
  ],
  SUPER_ADMIN: [
    {
      label: "sidebar.menu.dashboard_plateforme",
      icon: "domain",
      route: "/platform/dashboard",
    },
    {
      label: "sidebar.menu.gestion_imf",
      icon: "business",
      route: "/platform/imfs",
    },
    {
      label: "sidebar.menu.audit_global",
      icon: "history_edu",
      route: "/platform/audit",
    },
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
    {
      label: "sidebar.menu.actions",
      icon: "task_alt",
      route: "/recouvrement/actions",
    },
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
    {
      label: "sidebar.menu.mon_equipe",
      icon: "groups",
      route: "/chef-agence/equipe",
    },
  ],
  AGENT_CREDIT: [
    {
      label: "sidebar.menu.dashboard",
      icon: "dashboard",
      route: "/credit/dashboard",
    },
    {
      label: "sidebar.menu.nouveau_dossier",
      icon: "add_circle",
      route: "/credit/nouveau-dossier",
    },
    {
      label: "sidebar.menu.mes_dossiers",
      icon: "folder_open",
      route: "/credit/dossiers",
    },
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
    {
      label: "sidebar.menu.contrats",
      icon: "description",
      route: "/saisie/contrats",
    },
    {
      label: "sidebar.menu.dossiers_credit",
      icon: "folder_special",
      route: "/saisie/dossiers-credit",
    },
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

export interface AppMenuGroup {
  label: string | null;
  items: AppMenuItem[];
}

export function groupMenuItems(items: AppMenuItem[]): AppMenuGroup[] {
  if (!items.some((i) => i.group)) {
    return [{ label: null, items }];
  }
  const order: string[] = [];
  const map = new Map<string, AppMenuItem[]>();
  for (const item of items) {
    const g = item.group ?? "";
    if (!map.has(g)) {
      map.set(g, []);
      order.push(g);
    }
    map.get(g)!.push(item);
  }
  return order.map((g) => ({ label: g || null, items: map.get(g)! }));
}

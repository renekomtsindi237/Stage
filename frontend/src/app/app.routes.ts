import { Routes } from "@angular/router";
import { authGuard } from "./core/auth/auth.guard";
import { roleGuard } from "./core/auth/role.guard";

export const routes: Routes = [
  // Auth shell (no sidebar)
  {
    path: "login",
    loadComponent: () =>
      import("./layout/auth-shell/auth-shell.component").then(
        (m) => m.AuthShellComponent,
      ),
    children: [
      {
        path: "",
        loadComponent: () =>
          import("./features/auth/login/login.component").then(
            (m) => m.LoginComponent,
          ),
      },
      {
        path: "otp",
        loadComponent: () =>
          import("./features/auth/otp/otp.component").then(
            (m) => m.OtpComponent,
          ),
      },
    ],
  },
  {
    path: "admin/login",
    loadComponent: () =>
      import("./features/auth/login-admin/login-admin.component").then(
        (m) => m.LoginAdminComponent,
      ),
  },

  // App shell (sidebar + topbar)
  {
    path: "",
    loadComponent: () =>
      import("./layout/app-shell/app-shell.component").then(
        (m) => m.AppShellComponent,
      ),
    canActivate: [authGuard],
    children: [
      { path: "", redirectTo: "dashboard", pathMatch: "full" },
      {
        path: "dashboard",
        loadComponent: () =>
          import("./features/dashboard/dashboard.component").then(
            (m) => m.DashboardComponent,
          ),
      },

      // SUPER_ADMIN — Platform
      {
        path: "platform",
        canActivate: [roleGuard(["SUPER_ADMIN"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/platform/platform-dashboard/platform-dashboard.component").then(
                (m) => m.PlatformDashboardComponent,
              ),
          },
        ],
      },

      // DIRECTEUR
      {
        path: "directeur",
        canActivate: [roleGuard(["DIRECTEUR"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/directeur/dir-dashboard/dir-dashboard.component").then(
                (m) => m.DirDashboardComponent,
              ),
          },
          {
            path: "alertes",
            loadComponent: () =>
              import("./features/directeur/dir-alertes/dir-alertes.component").then(
                (m) => m.DirAlertesComponent,
              ),
          },
          {
            path: "kpi",
            loadComponent: () =>
              import("./features/directeur/dir-kpi/dir-kpi.component").then(
                (m) => m.DirKpiComponent,
              ),
          },
          {
            path: "clients",
            loadComponent: () =>
              import("./features/directeur/dir-clients/dir-clients.component").then(
                (m) => m.DirClientsComponent,
              ),
          },
          {
            path: "kyc",
            loadComponent: () =>
              import("./features/directeur/dir-kyc/dir-kyc.component").then(
                (m) => m.DirKycComponent,
              ),
          },
          {
            path: "agences",
            loadComponent: () =>
              import("./features/directeur/dir-agences/dir-agences.component").then(
                (m) => m.DirAgencesComponent,
              ),
          },
          {
            path: "rapports",
            loadComponent: () =>
              import("./features/directeur/dir-rapports/dir-rapports.component").then(
                (m) => m.DirRapportsComponent,
              ),
          },
          {
            path: "carte-agents",
            loadComponent: () =>
              import("./features/directeur/dir-carte-agents/dir-carte-agents.component").then(
                (m) => m.DirCarteAgentsComponent,
              ),
          },
          {
            path: "equipe",
            loadComponent: () =>
              import("./features/directeur/dir-users/dir-users.component").then(
                (m) => m.DirUsersComponent,
              ),
          },
        ],
      },

      // ANALYSTE
      {
        path: "analyste",
        canActivate: [roleGuard(["ANALYSTE"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/analyste/anl-dashboard/anl-dashboard.component").then(
                (m) => m.AnlDashboardComponent,
              ),
          },
          {
            path: "scoring",
            loadComponent: () =>
              import("./features/analyste/anl-scoring/anl-scoring.component").then(
                (m) => m.AnlScoringComponent,
              ),
          },
          {
            path: "pipeline",
            loadComponent: () =>
              import("./features/analyste/anl-pipeline/anl-pipeline.component").then(
                (m) => m.AnlPipelineComponent,
              ),
          },
          {
            path: "drift",
            loadComponent: () =>
              import("./features/analyste/anl-drift/anl-drift.component").then(
                (m) => m.AnlDriftComponent,
              ),
          },
        ],
      },

      // AGENT (mobile-first)
      {
        path: "agent",
        canActivate: [roleGuard(["AGENT"])],
        children: [
          {
            path: "",
            loadComponent: () =>
              import("./features/agent/agent-home/agent-home.component").then(
                (m) => m.AgentHomeComponent,
              ),
          },
          {
            path: "collecte",
            loadComponent: () =>
              import("./features/agent/nouvelle-collecte/nouvelle-collecte.component").then(
                (m) => m.NouvelleCollecteComponent,
              ),
          },
        ],
      },

      // AGENT_CREDIT
      {
        path: "credit",
        canActivate: [roleGuard(["AGENT_CREDIT"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/agent-credit/ac-dashboard/ac-dashboard.component").then(
                (m) => m.AcDashboardComponent,
              ),
          },
          {
            path: "dossiers",
            loadComponent: () =>
              import("./features/agent-credit/ac-dossiers/ac-dossiers.component").then(
                (m) => m.AcDossiersComponent,
              ),
          },
          {
            path: "nouveau-dossier",
            loadComponent: () =>
              import("./features/agent-credit/ac-nouveau-dossier/ac-nouveau-dossier.component").then(
                (m) => m.AcNouveauDossierComponent,
              ),
          },
        ],
      },

      // CHEF_AGENCE
      {
        path: "chef-agence",
        canActivate: [roleGuard(["CHEF_AGENCE"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/chef-agence/ca-dashboard/ca-dashboard.component").then(
                (m) => m.CaDashboardComponent,
              ),
          },
        ],
      },

      // RESPONSABLE_RECOUVREMENT
      {
        path: "recouvrement",
        canActivate: [roleGuard(["RESPONSABLE_RECOUVREMENT"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/recouvrement/rec-dashboard/rec-dashboard.component").then(
                (m) => m.RecDashboardComponent,
              ),
          },
          {
            path: "alertes",
            loadComponent: () =>
              import("./features/recouvrement/rec-alertes/rec-alertes.component").then(
                (m) => m.RecAlertesComponent,
              ),
          },
        ],
      },

      // CAISSIER
      {
        path: "caisse",
        canActivate: [roleGuard(["CAISSIER"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/caisse/cai-dashboard/cai-dashboard.component").then(
                (m) => m.CaiDashboardComponent,
              ),
          },
          {
            path: "encaissement",
            loadComponent: () =>
              import("./features/caisse/cai-encaissement/cai-encaissement.component").then(
                (m) => m.CaiEncaissementComponent,
              ),
          },
          {
            path: "decaissement",
            loadComponent: () =>
              import("./features/caisse/cai-decaissement/cai-decaissement.component").then(
                (m) => m.CaiDecaissementComponent,
              ),
          },
        ],
      },

      // AGENT_SAISIE
      {
        path: "saisie",
        canActivate: [roleGuard(["AGENT_SAISIE"])],
        children: [
          { path: "", redirectTo: "contrats", pathMatch: "full" },
          {
            path: "contrats",
            loadComponent: () =>
              import("./features/saisie/sai-contrats/sai-contrats.component").then(
                (m) => m.SaiContratsComponent,
              ),
          },
        ],
      },

      // ANALYSTE_ENGAGEMENTS
      {
        path: "engagements",
        canActivate: [roleGuard(["ANALYSTE_ENGAGEMENTS"])],
        children: [
          { path: "", redirectTo: "conformite", pathMatch: "full" },
          {
            path: "conformite",
            loadComponent: () =>
              import("./features/engagements/eng-conformite/eng-conformite.component").then(
                (m) => m.EngConformiteComponent,
              ),
          },
        ],
      },

      // SUPPORT
      {
        path: "support",
        canActivate: [roleGuard(["SUPPORT"])],
        children: [
          { path: "", redirectTo: "tickets", pathMatch: "full" },
          {
            path: "tickets",
            loadComponent: () =>
              import("./features/support/sup-tickets/sup-tickets.component").then(
                (m) => m.SupTicketsComponent,
              ),
          },
          {
            path: "monitoring",
            loadComponent: () =>
              import("./features/support/sup-monitoring/sup-monitoring.component").then(
                (m) => m.SupMonitoringComponent,
              ),
          },
        ],
      },

      // DSI
      {
        path: "dsi",
        canActivate: [roleGuard(["DSI"])],
        children: [
          { path: "", redirectTo: "dashboard", pathMatch: "full" },
          {
            path: "dashboard",
            loadComponent: () =>
              import("./features/dsi/dsi-dashboard/dsi-dashboard.component").then(
                (m) => m.DsiDashboardComponent,
              ),
          },
          {
            path: "rgpd",
            loadComponent: () =>
              import("./features/dsi/dsi-rgpd/dsi-rgpd.component").then(
                (m) => m.DsiRgpdComponent,
              ),
          },
          {
            path: "audit",
            loadComponent: () =>
              import("./features/dsi/dsi-audit/dsi-audit.component").then(
                (m) => m.DsiAuditComponent,
              ),
          },
          {
            path: "users",
            loadComponent: () =>
              import("./features/dsi/dsi-users/dsi-users.component").then(
                (m) => m.DsiUsersComponent,
              ),
          },
          {
            path: "monitoring",
            loadComponent: () =>
              import("./features/dsi/dsi-monitoring/dsi-monitoring.component").then(
                (m) => m.DsiMonitoringComponent,
              ),
          },
        ],
      },
    ],
  },

  { path: "**", redirectTo: "login" },
];

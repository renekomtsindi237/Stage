import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { AuthGuard } from "./core/guards/auth.guard";
import { RoleGuard } from "./core/guards/role.guard";
import { ErrorPageComponent } from "./shared/error-pages/error-page.component";

const routes: Routes = [
  /* Page d'accueil publique — première page affichée */
  {
    path: "",
    loadChildren: () =>
      import("./modules/landing/landing.module").then((m) => m.LandingModule),
    pathMatch: "full",
  },
  {
    path: "login",
    loadChildren: () =>
      import("./modules/auth/auth.module").then((m) => m.AuthModule),
  },
  /* ── SUPER_ADMIN ── */
  {
    path: "platform",
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ["SUPER_ADMIN"] },
    loadChildren: () =>
      import("./modules/platform/platform.module").then(
        (m) => m.PlatformModule,
      ),
  },
  /* ── Utilisateurs IMF ── */
  {
    path: "dashboard",
    canActivate: [AuthGuard],
    loadChildren: () =>
      import("./modules/dashboard/dashboard.module").then(
        (m) => m.DashboardModule,
      ),
  },
  {
    path: "admin/alertes",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: [
        "DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI",
        "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
      ],
    },
    loadChildren: () =>
      import("./modules/alertes/alertes.module").then((m) => m.AlertesModule),
  },
  {
    path: "admin/prets",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: [
        "DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI",
        "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
      ],
    },
    loadChildren: () =>
      import("./modules/prets/prets.module").then((m) => m.PretsModule),
  },
  {
    path: "admin/clients",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: [
        "DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI",
        "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
      ],
    },
    loadChildren: () =>
      import("./modules/clients/clients.module").then((m) => m.ClientsModule),
  },
  {
    path: "admin/reporting",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: [
        "DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI",
        "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
      ],
    },
    loadChildren: () =>
      import("./modules/reporting/reporting.module").then(
        (m) => m.ReportingModule,
      ),
  },
  {
    path: "admin/recouvrement",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: [
        "DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI",
        "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
      ],
    },
    loadChildren: () =>
      import("./modules/recouvrement/recouvrement.module").then(
        (m) => m.RecouvrementModule,
      ),
  },
  {
    path: "admin/kyc",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: [
        "DIRECTEUR", "RESPONSABLE_RECOUVREMENT", "ANALYSTE", "DSI",
        "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
      ],
    },
    loadChildren: () =>
      import("./modules/kyc/kyc.module").then((m) => m.KycModule),
  },
  /* ── Crédit (AGENT_CREDIT, CHEF_AGENCE, ANALYSTE_ENGAGEMENTS, DIRECTEUR) ── */
  {
    path: "credit",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: ["AGENT_CREDIT", "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS", "DIRECTEUR", "DSI"],
    },
    loadChildren: () =>
      import("./modules/credit/credit.module").then((m) => m.CreditModule),
  },
  /* ── Back-Office (AGENT_SAISIE, CAISSIER, CHEF_AGENCE) ── */
  {
    path: "back-office",
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ["AGENT_SAISIE", "CAISSIER", "CHEF_AGENCE", "DIRECTEUR", "DSI"] },
    loadChildren: () =>
      import("./modules/back-office/back-office.module").then(
        (m) => m.BackOfficeModule,
      ),
  },
  /* ── Délégations hiérarchiques (tout utilisateur IMF) ── */
  {
    path: 'delegations',
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: [
        'DIRECTEUR', 'CHEF_AGENCE', 'RESPONSABLE_RECOUVREMENT',
        'ANALYSTE_ENGAGEMENTS', 'AGENT_CREDIT', 'AGENT_SAISIE',
        'CAISSIER', 'AGENT', 'DSI',
      ],
    },
    loadChildren: () =>
      import('./modules/delegations/delegations.module').then(
        (m) => m.DelegationsModule,
      ),
  },
  /* ── Contentieux OHADA (RESPONSABLE_RECOUVREMENT, DIRECTEUR) ── */
  {
    path: "contentieux",
    canActivate: [AuthGuard, RoleGuard],
    data: {
      roles: ["RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI"],
    },
    loadChildren: () =>
      import("./modules/contentieux/contentieux.module").then(
        (m) => m.ContentieuxModule,
      ),
  },
  /* ── ANALYSTE ── */
  {
    path: "analyste",
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ["ANALYSTE", "ANALYSTE_ENGAGEMENTS"] },
    loadChildren: () =>
      import("./modules/analyste/analyste.module").then(
        (m) => m.AnalysteModule,
      ),
  },
  /* ── DSI ── */
  {
    path: "dsi",
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ["DSI"] },
    loadChildren: () =>
      import("./modules/dsi/dsi.module").then((m) => m.DsiModule),
  },
  /* ── SUPPORT ── */
  {
    path: "support",
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ["SUPPORT"] },
    loadChildren: () =>
      import("./modules/support/support.module").then((m) => m.SupportModule),
  },
  {
    path: "admin",
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ["DSI"] },
    loadChildren: () =>
      import("./modules/admin/admin.module").then((m) => m.AdminModule),
  },
  {
    path: "profile",
    canActivate: [AuthGuard],
    loadChildren: () =>
      import("./modules/profile/profile.module").then((m) => m.ProfileModule),
  },
  /* ── Pages d'erreur (shellless) ── */
  {
    path: "error",
    children: [
      {
        path: "404",
        component: ErrorPageComponent,
        data: { code: "404" },
      },
      {
        path: "403",
        component: ErrorPageComponent,
        data: { code: "403" },
      },
      {
        path: "500",
        component: ErrorPageComponent,
        data: { code: "500" },
      },
      {
        path: "offline",
        component: ErrorPageComponent,
        data: { code: "offline" },
      },
      { path: "**", redirectTo: "404" },
    ],
  },
  /* Wildcard — page 404 (URL préservée dans la barre) */
  {
    path: "**",
    component: ErrorPageComponent,
    data: { code: "404" },
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { scrollPositionRestoration: "top" })],
  exports: [RouterModule],
})
export class AppRoutingModule {}

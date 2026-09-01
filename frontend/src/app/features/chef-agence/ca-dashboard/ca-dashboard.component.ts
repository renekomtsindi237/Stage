import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { TranslatePipe, TranslateService } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";
import { ToastService } from "../../../core/services/toast.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { AppDatePipe } from "../../../shared/pipes/app-date.pipe";

interface DossierPendant {
  uid: string;
  clientNom: string;
  clientId: string;
  montantDemande: number;
  dureeMois: number;
  secteurActivite: string;
  objetFinancement: string;
  agentNom: string;
  dateSoumission: string;
  statut: string;
  noteAnalyse: string | null;
}

interface CaDashboard {
  agentsCount: number;
  clientsCount: number;
  collectesJour: number;
  par30: number;
  dossiersEnAttente: number;
  dossiersValidesMois: number;
  dossiers: DossierPendant[];
}

interface EquipePerf {
  membres: {
    uid: string;
    username: string;
    tendance: "HAUSSE" | "BAISSE" | "STABLE";
    evolutionPct: number;
  }[];
}

@Component({
  selector: "app-ca-dashboard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    StatCardComponent,
    FcfaPipe,
    TranslatePipe,
    AppDatePipe,
  ],
  templateUrl: "./ca-dashboard.component.html",
  styleUrls: ["./ca-dashboard.component.scss"],
})
export class CaDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslateService);

  loading = signal(true);
  data = signal<CaDashboard | null>(null);
  validating = signal<string | null>(null);
  declining = signal<{ username: string; evolutionPct: number }[]>([]);

  ngOnInit() {
    this.api.get<CaDashboard>("/api/v1/chef-agence/dashboard").subscribe({
      next: (d: CaDashboard) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.toast.showApiError(err);
        this.loading.set(false);
      },
    });
    this.api
      .get<EquipePerf>("/api/v1/chef-agence/equipe/performances", { jours: 30 })
      .subscribe({
        next: (p) => {
          this.declining.set(
            (p.membres ?? [])
              .filter((m) => m.tendance === "BAISSE")
              .map((m) => ({
                username: m.username,
                evolutionPct: m.evolutionPct,
              })),
          );
        },
        error: () => {},
      });
  }

  valider(uid: string, decision: "VALIDE" | "REJETE") {
    this.validating.set(uid);
    this.api
      .patch(`/api/v1/dossiers-credit/${uid}/valider-chef`, {
        action: decision,
        motif: "",
      })
      .subscribe({
        next: () => {
          this.validating.set(null);
          this.data.update((d) =>
            d
              ? { ...d, dossiers: d.dossiers.filter((dos) => dos.uid !== uid) }
              : d,
          );
          const remaining = this.data()?.dossiers.length ?? 0;
          const action = this.i18n.instant(
            decision === "VALIDE"
              ? "ca_dashboard.action_validated"
              : "ca_dashboard.action_rejected",
          );
          this.toast.showI18nSuccess(
            "ca_dashboard.toast_decision",
            remaining > 0
              ? "ca_dashboard.toast_remaining"
              : "ca_dashboard.toast_queue_empty",
            { action, count: remaining },
          );
        },
        error: (err: unknown) => {
          this.toast.showApiError(err);
          this.validating.set(null);
        },
      });
  }
}

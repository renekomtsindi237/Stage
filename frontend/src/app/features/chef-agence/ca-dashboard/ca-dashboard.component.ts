import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";
import { ToastService } from "../../../core/services/toast.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";

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

@Component({
  selector: "app-ca-dashboard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent, FcfaPipe, TranslatePipe],
  templateUrl: "./ca-dashboard.component.html",
  styleUrls: ["./ca-dashboard.component.scss"],
})
export class CaDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  data = signal<CaDashboard | null>(null);
  validating = signal<string | null>(null);

  ngOnInit() {
    this.api.get<CaDashboard>("/api/v1/chef-agence/dashboard").subscribe({
      next: (d: CaDashboard) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
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
          this.toast.showSuccess(
            "Décision enregistrée",
            `Dossier ${decision === "VALIDE" ? "validé" : "rejeté"}`,
          );
          this.validating.set(null);
          this.data.update((d) =>
            d
              ? { ...d, dossiers: d.dossiers.filter((dos) => dos.uid !== uid) }
              : d,
          );
        },
        error: () => {
          this.toast.showError("Erreur", "Impossible de traiter le dossier.");
          this.validating.set(null);
        },
      });
  }
}

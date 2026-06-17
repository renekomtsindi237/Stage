import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";

interface Alerte {
  id: number;
  clientNom: string;
  clientPrenom: string;
  montantDu: number;
  joursRetard: number;
  niveauAlerte: "AMIABLE" | "FORMEL" | "JUDICIAIRE";
  dateAlerte: string;
  statut: "OUVERTE" | "EN_TRAITEMENT" | "RESOLUE";
  agentNom?: string;
}

interface AlertePage {
  content: Alerte[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-rec-alertes",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./rec-alertes.component.html",
  styleUrls: ["./rec-alertes.component.scss"],
})
export class RecAlertesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  page = signal<AlertePage | null>(null);
  currentPage = signal(0);
  activeTab = signal<string>("OUVERTE");

  readonly tabs = ["OUVERTE", "EN_TRAITEMENT", "RESOLUE", "TOUS"];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const statut = this.activeTab() === "TOUS" ? undefined : this.activeTab();
    this.api
      .get<AlertePage>("/api/v1/recouvrement/alertes", {
        page: this.currentPage(),
        size: 20,
        statut,
      })
      .subscribe({
        next: (p: AlertePage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  prendre(id: number) {
    this.api.patch(`/api/v1/recouvrement/alertes/${id}/traiter`, {}).subscribe({
      next: () => {
        this.toast.showSuccess("En traitement", "Alerte prise en charge.");
        this.load();
      },
      error: () => this.toast.showError("Erreur", "Action impossible."),
    });
  }

  resoudre(id: number) {
    this.api
      .patch(`/api/v1/recouvrement/alertes/${id}/resoudre`, {})
      .subscribe({
        next: () => {
          this.toast.showSuccess("Résolue", "Alerte marquée résolue.");
          this.load();
        },
        error: () => this.toast.showError("Erreur", "Action impossible."),
      });
  }

  switchTab(t: string) {
    this.activeTab.set(t);
    this.currentPage.set(0);
    this.load();
  }
  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  niveauClass(n: string) {
    return (
      {
        AMIABLE: "badge-warning",
        FORMEL: "badge-primary",
        JUDICIAIRE: "badge-danger",
      }[n] ?? ""
    );
  }
  statutClass(s: string) {
    return (
      {
        OUVERTE: "badge-danger",
        EN_TRAITEMENT: "badge-warning",
        RESOLUE: "badge-success",
      }[s] ?? ""
    );
  }
}

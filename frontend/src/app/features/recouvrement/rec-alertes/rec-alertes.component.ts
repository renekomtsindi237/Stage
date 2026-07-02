import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";

interface Alerte {
  uid: string;
  idPret: string;
  joursRetard: number;
  montantEnRetard: number;
  statutAlerte: string;
  dateGeneration: string;
  dateCloture: string | null;
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
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./rec-alertes.component.html",
  styleUrls: ["./rec-alertes.component.scss"],
})
export class RecAlertesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  page = signal<AlertePage | null>(null);
  currentPage = signal(0);
  activeTab = signal<string>("ACTIVE");

  readonly tabs: { label: string; value: string }[] = [
    { label: "Actives", value: "ACTIVE" },
    { label: "En traitement", value: "EN_TRAITEMENT" },
    { label: "Résolues", value: "RESOLUE" },
    { label: "Toutes", value: "TOUS" },
  ];

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

  prendre(uid: string) {
    this.api
      .patch<unknown>(`/api/v1/recouvrement/alertes/${uid}/traiter`, {})
      .subscribe({
        next: () => {
          this.toast.showSuccess("En traitement", "Alerte prise en charge.");
          this.load();
        },
        error: () => this.toast.showError("Erreur", "Action impossible."),
      });
  }

  resoudre(uid: string) {
    this.api
      .patch<unknown>(`/api/v1/recouvrement/alertes/${uid}/resoudre`, {})
      .subscribe({
        next: () => {
          this.toast.showSuccess("Résolue", "Alerte marquée résolue.");
          this.load();
        },
        error: () => this.toast.showError("Erreur", "Action impossible."),
      });
  }

  switchTab(value: string) {
    this.activeTab.set(value);
    this.currentPage.set(0);
    this.load();
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  statutClass(s: string): string {
    const m: Record<string, string> = {
      ACTIVE: "badge-danger",
      NON_TRAITEE: "badge-danger",
      ESCALADEE: "badge-warning",
      EN_TRAITEMENT: "badge-warning",
      RESOLUE: "badge-success",
      CLOTUREE: "badge-success",
    };
    return m[s] ?? "";
  }

  statutLabel(s: string): string {
    const m: Record<string, string> = {
      ACTIVE: "Active",
      NON_TRAITEE: "Non traitée",
      ESCALADEE: "Escaladée",
      EN_TRAITEMENT: "En traitement",
      RESOLUE: "Résolue",
      CLOTUREE: "Clôturée",
    };
    return m[s] ?? s;
  }

  retardClass(jours: number): string {
    if (jours >= 180) return "retard-severe";
    if (jours >= 90)  return "retard-eleve";
    return "";
  }
}

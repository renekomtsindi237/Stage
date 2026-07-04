import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { TranslatePipe, TranslateService } from "@ngx-translate/core";
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
  private readonly translate = inject(TranslateService);

  loading = signal(true);
  page = signal<AlertePage | null>(null);
  currentPage = signal(0);
  activeTab = signal<string>("ACTIVE");

  readonly tabs: { label: string; value: string }[] = [
    { label: "rec_alertes.tab_active", value: "ACTIVE" },
    { label: "rec_alertes.tab_en_traitement", value: "EN_TRAITEMENT" },
    { label: "rec_alertes.tab_resolue", value: "RESOLUE" },
    { label: "rec_alertes.tab_tous", value: "TOUS" },
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
          this.toast.showSuccess(
            this.translate.instant("rec_alertes.toast_prendre_title"),
            this.translate.instant("rec_alertes.toast_prendre_body"),
          );
          this.load();
        },
        error: () =>
          this.toast.showError(
            this.translate.instant("common.error"),
            this.translate.instant("rec_alertes.toast_error"),
          ),
      });
  }

  resoudre(uid: string) {
    this.api
      .patch<unknown>(`/api/v1/recouvrement/alertes/${uid}/resoudre`, {})
      .subscribe({
        next: () => {
          this.toast.showSuccess(
            this.translate.instant("rec_alertes.toast_resoudre_title"),
            this.translate.instant("rec_alertes.toast_resoudre_body"),
          );
          this.load();
        },
        error: () =>
          this.toast.showError(
            this.translate.instant("common.error"),
            this.translate.instant("rec_alertes.toast_error"),
          ),
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
      ACTIVE: "rec_alertes.statut_active",
      NON_TRAITEE: "rec_alertes.statut_non_traitee",
      ESCALADEE: "rec_alertes.statut_escaladee",
      EN_TRAITEMENT: "rec_alertes.statut_en_traitement",
      RESOLUE: "rec_alertes.statut_resolue",
      CLOTUREE: "rec_alertes.statut_cloturee",
    };
    return m[s] ?? s;
  }

  retardClass(jours: number): string {
    if (jours >= 180) return "retard-severe";
    if (jours >= 90) return "retard-eleve";
    return "";
  }
}

import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { Alerte } from "../../../core/models/alerte.model";
import { AlertBadgeComponent } from "../../../shared/components/alert-badge/alert-badge.component";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { TimeAgoPipe } from "../../../shared/pipes/time-ago.pipe";

interface MlAlerteApi {
  id: number | string;
  clientIdExterne?: string;
  typeAlerte?: string;
  urgence?: string;
  titre?: string;
  description?: string;
  statut?: string;
  createdAt?: string;
}

type Tab = "NON_TRAITEE" | "EN_TRAITEMENT" | "TOUTES";

@Component({
  selector: "app-dir-alertes",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    AlertBadgeComponent,
    FcfaPipe,
    TimeAgoPipe,
    TranslatePipe,
  ],
  templateUrl: "./dir-alertes.component.html",
  styleUrls: ["./dir-alertes.component.scss"],
})
export class DirAlertesComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  alertes = signal<Alerte[]>([]);
  total = signal(0);
  critiques = signal(0);
  hautes = signal(0);
  moyennes = signal(0);
  search = signal("");
  tab = signal<Tab>("NON_TRAITEE");
  treating = signal<string | null>(null);

  ngOnInit() {
    this.load();
  }

  setTab(t: Tab) {
    this.tab.set(t);
    this.load();
  }

  load() {
    this.loading.set(true);
    const statut =
      this.tab() === "TOUTES"
        ? ""
        : this.tab() === "NON_TRAITEE"
          ? "ACTIVE"
          : "EN_TRAITEMENT";
    this.api
      .get<MlAlerteApi[] | { content?: MlAlerteApi[] }>("/api/v1/ml/alertes", {
        ...(statut ? { statut } : {}),
      })
      .subscribe({
        next: (res) => {
          const rows = Array.isArray(res) ? res : (res.content ?? []);
          const mapped = rows.map((a) => this.toAlerte(a));
          this.alertes.set(mapped);
          this.total.set(mapped.length);
          this.critiques.set(
            mapped.filter((a) => a.severite === "CRITIQUE").length,
          );
          this.hautes.set(mapped.filter((a) => a.severite === "HAUTE").length);
          this.moyennes.set(
            mapped.filter((a) => a.severite === "MOYENNE").length,
          );
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  traiter(id: string) {
    this.treating.set(id);
    this.api
      .put(`/api/v1/ml/alertes/${id}/statut?statut=EN_TRAITEMENT`)
      .subscribe({
        next: () => {
          this.treating.set(null);
          this.load();
        },
        error: () => this.treating.set(null),
      });
  }

  private toAlerte(a: MlAlerteApi): Alerte {
    const sev = (a.urgence ?? "MOYENNE").toUpperCase();
    return {
      id: String(a.id),
      clientId: a.clientIdExterne ?? "",
      nomClient: a.clientIdExterne ?? "—",
      agence: a.typeAlerte ?? "",
      severite:
        sev === "CRITIQUE" || sev === "HAUTE" || sev === "BASSE"
          ? sev
          : "MOYENNE",
      statut:
        a.statut === "EN_TRAITEMENT"
          ? "EN_TRAITEMENT"
          : a.statut === "RESOLUE"
            ? "RESOLUE"
            : "NON_TRAITEE",
      message: a.titre ?? a.description ?? "",
      encours: 0,
      createdAt: a.createdAt ?? new Date().toISOString(),
    };
  }

  get filtered(): Alerte[] {
    const q = this.search().toLowerCase();
    if (!q) return this.alertes();
    return this.alertes().filter(
      (a) =>
        a.nomClient.toLowerCase().includes(q) ||
        a.clientId.toLowerCase().includes(q),
    );
  }
}

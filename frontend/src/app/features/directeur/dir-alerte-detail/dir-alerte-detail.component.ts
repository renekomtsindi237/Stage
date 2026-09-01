import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { AlertBadgeComponent } from "../../../shared/components/alert-badge/alert-badge.component";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { TimeAgoPipe } from "../../../shared/pipes/time-ago.pipe";

interface MlAlerteDetail {
  id: number | string;
  clientIdExterne?: string;
  nomClient?: string;
  typeAlerte?: string;
  urgence?: string;
  titre?: string;
  description?: string;
  recommandation?: string;
  statut?: string;
  createdAt?: string;
  resolutionNote?: string;
  encours?: number | null;
  joursRetard?: number | null;
  scoreMcrs?: number | null;
  probabiliteDefaut90j?: number | null;
  actionRecommandee?: string | null;
}

@Component({
  selector: "app-dir-alerte-detail",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    TranslatePipe,
    AlertBadgeComponent,
    FcfaPipe,
    TimeAgoPipe,
  ],
  templateUrl: "./dir-alerte-detail.component.html",
  styleUrls: ["./dir-alerte-detail.component.scss"],
})
export class DirAlerteDetailComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  saving = signal(false);
  notFound = signal(false);
  alerte = signal<MlAlerteDetail | null>(null);
  note = signal("");

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get("id");
    if (!id) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  get urgence(): string {
    return (this.alerte()?.urgence ?? "MOYENNE").toUpperCase();
  }

  get clos(): boolean {
    const s = this.alerte()?.statut;
    return s === "RESOLUE" || s === "IGNOREE";
  }

  get pd90Label(): string {
    const v = this.alerte()?.probabiliteDefaut90j;
    if (v == null) return "—";
    const pct = v <= 1 ? v * 100 : v;
    return `${pct.toFixed(1)} %`;
  }

  load(id: string) {
    this.loading.set(true);
    this.api.get<MlAlerteDetail>(`/api/v1/ml/alertes/${id}`).subscribe({
      next: (a) => {
        this.alerte.set(a);
        this.note.set(a.resolutionNote ?? "");
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  appliquer(statut: "EN_TRAITEMENT" | "RESOLUE" | "IGNOREE") {
    const a = this.alerte();
    if (!a) return;
    if (
      (statut === "RESOLUE" || statut === "IGNOREE") &&
      this.note().trim().length < 8
    ) {
      this.toast.showError(
        "Note requise",
        "Indiquez une note de décision (8 caractères minimum).",
      );
      return;
    }
    this.saving.set(true);
    this.api
      .put<MlAlerteDetail>(`/api/v1/ml/alertes/${a.id}/traitement`, {
        statut,
        note: this.note().trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.alerte.set(updated);
          this.note.set(updated.resolutionNote ?? this.note());
          this.saving.set(false);
          const msg =
            statut === "EN_TRAITEMENT"
              ? "Alerte prise en charge."
              : statut === "RESOLUE"
                ? "Alerte marquée résolue."
                : "Alerte ignorée.";
          this.toast.showSuccess("Traitement enregistré", msg);
          if (statut !== "EN_TRAITEMENT") {
            this.router.navigate(["/directeur/alertes"]);
          }
        },
        error: () => {
          this.saving.set(false);
          this.toast.showError(
            "Échec",
            "Impossible d'enregistrer le traitement.",
          );
        },
      });
  }
}

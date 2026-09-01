import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
  computed,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";

interface CreanceRow {
  id: string;
  clientNom: string;
  montant: number;
  joursRetard: number;
  statut: string;
  phase: string;
}

interface RecDashboard {
  creancesActives: number;
  montantEnRetard: number;
  actionsDuMois: number;
  tauxRecouvrement: number;
  creances: CreanceRow[];
  parPhase: Record<string, number>;
}

@Component({
  selector: "app-rec-dashboard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    StatCardComponent,
    FcfaPipe,
    TranslatePipe,
    StatutLabelPipe,
  ],
  templateUrl: "./rec-dashboard.component.html",
  styleUrls: ["./rec-dashboard.component.scss"],
})
export class RecDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data = signal<RecDashboard | null>(null);

  readonly phaseEntries = computed(() => {
    const pp = this.data()?.parPhase ?? {};
    return Object.entries(pp).map(([phase, count]) => ({ phase, count }));
  });

  ngOnInit() {
    this.api.get<RecDashboard>("/api/v1/recouvrement/dashboard").subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  phaseClass(phase: string): string {
    const m: Record<string, string> = {
      RELANCE_AMIABLE: "badge-basse",
      MEDIATION_AMIABLE: "badge-moyenne",
      MISE_EN_DEMEURE: "badge-haute",
      CONTENTIEUX: "badge-critique",
      REECHELONNEMENT: "badge-primary",
      PERTE: "badge-dark",
    };
    return m[phase] ?? "badge-moyenne";
  }

  cobacClass(cat: string): string {
    return (
      {
        A: "badge-basse",
        B: "badge-moyenne",
        C: "badge-haute",
        D: "badge-critique",
        E: "badge-dark",
      }[cat] ?? ""
    );
  }
}

import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { map } from "rxjs";
import { ApiService } from "../../../core/http/api.service";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";

interface DsiDashboardData {
  utilisateursActifs: number;
  alertesSysteme: number;
  rgpdScore: number;
  dernierAudit: string;
  violationsOuvertes: number;
  demandesDroits: number;
}

const RING_RADIUS = 54;
const RING_CIRC = 2 * Math.PI * RING_RADIUS;

@Component({
  selector: "app-dsi-dashboard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent],
  templateUrl: "./dsi-dashboard.component.html",
  styleUrls: ["./dsi-dashboard.component.scss"],
})
export class DsiDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  loading = signal(true);
  data = signal<DsiDashboardData | null>(null);

  readonly ringCirc = RING_CIRC;

  readonly rgpdDash = computed(() => {
    const score = this.data()?.rgpdScore ?? 0;
    return (score / 100) * RING_CIRC;
  });

  readonly rgpdColor = computed(() => {
    const s = this.data()?.rgpdScore ?? 0;
    if (s >= 80) return "#059669";
    if (s >= 60) return "#d97706";
    return "#dc2626";
  });

  readonly indicators = computed(() => {
    const d = this.data();
    if (!d) return [];
    const max = Math.max(
      d.utilisateursActifs,
      d.violationsOuvertes,
      d.demandesDroits,
      1,
    );
    return [
      {
        label: "Utilisateurs actifs",
        value: d.utilisateursActifs,
        pct: Math.round((d.utilisateursActifs / max) * 100),
        color: "#4f46e5",
        icon: "group",
      },
      {
        label: "Violations ouvertes",
        value: d.violationsOuvertes,
        pct: Math.round((d.violationsOuvertes / max) * 100),
        color: d.violationsOuvertes > 0 ? "#dc2626" : "#059669",
        icon: "warning",
      },
      {
        label: "Demandes RGPD",
        value: d.demandesDroits,
        pct: Math.round((d.demandesDroits / max) * 100),
        color: d.demandesDroits > 0 ? "#d97706" : "#059669",
        icon: "gavel",
      },
      {
        label: "Alertes système",
        value: d.alertesSysteme,
        pct:
          d.alertesSysteme > 0
            ? Math.max(8, Math.round((d.alertesSysteme / max) * 100))
            : 0,
        color: d.alertesSysteme > 0 ? "#7c3aed" : "#059669",
        icon: "notifications_active",
      },
    ];
  });

  ngOnInit() {
    this.api
      .get<{ data: DsiDashboardData }>("/api/v1/dsi/dashboard")
      .pipe(map((r) => r.data))
      .subscribe({
        next: (d: DsiDashboardData) => {
          this.data.set(d);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}

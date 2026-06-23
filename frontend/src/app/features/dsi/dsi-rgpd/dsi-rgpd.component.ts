import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";

import { ApiService } from "../../../core/http/api.service";

interface RgpdStatus {
  conformite: number;
  consentements: { total: number; valides: number; expires: number };
  droitsExercices: { demandes: number; traitees: number; enCours: number };
  retentionAnomalies: number;
  dernierAudit: string;
}

@Component({
  selector: "app-dsi-rgpd",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./dsi-rgpd.component.html",
  styleUrls: ["./dsi-rgpd.component.scss"],
})
export class DsiRgpdComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data = signal<RgpdStatus | null>(null);
  exporting = signal(false);

  ngOnInit() {
    this.api
      .get<RgpdStatus>("/api/v1/dsi/rgpd/status")
      .subscribe({
        next: (d: RgpdStatus) => {
          this.data.set(d);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  exportRapport() {
    this.exporting.set(true);
    this.api.get<Blob>("/api/v1/dsi/rgpd/export").subscribe({
      next: () => this.exporting.set(false),
      error: () => this.exporting.set(false),
    });
  }

  get conformitePct() {
    return this.data()?.conformite ?? 0;
  }
  readonly checklistItems = [
    { label: "Registre de traitement des données à jour", ok: true },
    { label: "DPO désigné et notifié à l'autorité compétente", ok: true },
    { label: "Analyses d'impact réalisées (DPIA)", ok: false },
    { label: "Procédure de gestion des violations", ok: true },
    { label: "Clauses contractuelles sous-traitants", ok: true },
    { label: "Mécanisme de portabilité des données", ok: false },
  ];

  get conformiteClass() {
    const v = this.conformitePct;
    if (v >= 90) return "success";
    if (v >= 70) return "warning";
    return "danger";
  }
}

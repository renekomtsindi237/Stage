import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";

import { ApiService } from "../../../core/http/api.service";

interface ImfHealth {
  usersActifs: number;
  connexionsAujourdHui: number;
  tentativesEchouees: number;
  stockageUtilise: string;
  stockageTotal: string;
  derniereSync: string;
  statut: "OK" | "AVERTISSEMENT" | "CRITIQUE";
}

interface RecentLogin {
  utilisateur: string;
  role: string;
  heure: string;
  succes: boolean;
  ip: string;
}

@Component({
  selector: "app-dsi-monitoring",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./dsi-monitoring.component.html",
  styleUrls: ["./dsi-monitoring.component.scss"],
})
export class DsiMonitoringComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  loadingHealth = signal(true);
  loadingLogins = signal(true);
  imfHealth = signal<ImfHealth | null>(null);
  recentLogins = signal<RecentLogin[]>([]);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loadingHealth.set(true);
    this.loadingLogins.set(true);

    this.api
      .get<ImfHealth>("/api/v1/dsi/monitoring/health")
      .subscribe({
        next: (h: ImfHealth) => {
          this.imfHealth.set(h);
          this.loadingHealth.set(false);
          this.cdr.markForCheck();
        },
        error: () => {
          this.loadingHealth.set(false);
          this.cdr.markForCheck();
        },
      });

    this.api
      .get<RecentLogin[]>("/api/v1/dsi/monitoring/connexions-recentes")
      .subscribe({
        next: (list: RecentLogin[]) => {
          this.recentLogins.set(list ?? []);
          this.loadingLogins.set(false);
          this.cdr.markForCheck();
        },
        error: () => {
          this.loadingLogins.set(false);
          this.cdr.markForCheck();
        },
      });
  }

  statutClass(s: string) {
    return (
      {
        OK: "badge-success",
        AVERTISSEMENT: "badge-warning",
        CRITIQUE: "badge-danger",
      }[s] ?? ""
    );
  }

  storagePercent(h: ImfHealth): number {
    const toMb = (s: string): number => {
      const v = parseFloat(s);
      if (s.includes("GB") || s.includes("Go")) return v * 1024;
      return v;
    };
    const used = toMb(h.stockageUtilise);
    const total = toMb(h.stockageTotal);
    return total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0;
  }
}

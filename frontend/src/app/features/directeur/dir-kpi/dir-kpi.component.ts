import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { BaseChartDirective } from "ng2-charts";
import { ChartConfiguration } from "chart.js";
import { ApiService } from "../../../core/http/api.service";
import { AuthService } from "../../../core/auth/auth.service";
import { KpiPortefeuille } from "../../../core/models/kpi.model";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { environment } from "../../../../environments/environment";

@Component({
  selector: "app-dir-kpi",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, BaseChartDirective, StatCardComponent, FcfaPipe],
  templateUrl: "./dir-kpi.component.html",
  styleUrls: ["./dir-kpi.component.scss"],
})
export class DirKpiComponent implements OnInit {
  private readonly api  = inject(ApiService);
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly cdr  = inject(ChangeDetectorRef);

  loading      = signal(true);
  downloading  = signal(false);
  data         = signal<KpiPortefeuille | null>(null);

  parChartData: ChartConfiguration<"line">["data"] = {
    labels: [],
    datasets: [],
  };
  parChartOptions: ChartConfiguration<"line">["options"] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: "bottom" } },
    scales: {
      y: { ticks: { callback: (v) => `${v}%` }, grid: { color: "#f1f5f9" } },
      x: { grid: { display: false } },
    },
  };

  ngOnInit() {
    this.api.get<KpiPortefeuille>("/api/v1/kpi/portefeuille").subscribe({
      next: (d) => {
        this.data.set(d);
        this.buildChart(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  downloadCobac() {
    if (this.downloading()) return;
    this.downloading.set(true);
    this.cdr.markForCheck();

    const today = new Date();
    const dateFin   = today.toISOString().slice(0, 10);
    const dateDebut = `${today.getFullYear()}-01-01`;
    const token = this.auth.getToken();
    const headers: Record<string, string> = token
      ? { Authorization: `Bearer ${token}` }
      : {};

    this.http
      .get(
        `${environment.apiUrl}/api/v1/reporting/cobac/pdf?dateDebut=${dateDebut}&dateFin=${dateFin}`,
        { headers, responseType: "blob" },
      )
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a   = document.createElement("a");
          a.href     = url;
          a.download = `rapport_cobac_${dateFin}.pdf`;
          a.click();
          URL.revokeObjectURL(url);
          this.downloading.set(false);
          this.cdr.markForCheck();
        },
        error: () => {
          this.downloading.set(false);
          this.cdr.markForCheck();
        },
      });
  }

  private buildChart(d: KpiPortefeuille) {
    const evo = d.evolutionPar ?? [];
    this.parChartData = {
      labels: evo.map((e) => e.date.slice(5)),
      datasets: [
        {
          label: "PAR 30",
          data: evo.map((e) => e.par30),
          borderColor: "#3b82f6",
          backgroundColor: "rgba(59,130,246,.08)",
          fill: true,
          tension: 0.4,
        },
        {
          label: "PAR 90",
          data: evo.map((e) => e.par90),
          borderColor: "#ef4444",
          backgroundColor: "rgba(239,68,68,.05)",
          fill: true,
          tension: 0.4,
        },
        {
          label: "Objectif",
          data: evo.map((e) => e.objectif),
          borderColor: "#22c55e",
          borderDash: [4, 4],
          tension: 0.4,
          fill: false,
        },
      ],
    };
  }

  parClass(val: number): string {
    if (val >= 5) return "danger";
    if (val >= 3) return "warn";
    return "ok";
  }
}

import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { BaseChartDirective } from "ng2-charts";
import { ChartData, ChartOptions } from "chart.js";
import { ApiService } from "../../../core/http/api.service";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";
import { AlertBadgeComponent } from "../../../shared/components/alert-badge/alert-badge.component";
import { Alerte } from "../../../core/models/alerte.model";

interface AnlDashboardData {
  totalClients: number;
  scoresMoyen: number;
  alertesOuvertes: number;
  driftPsi: number;
  scoringDistribution: { label: string; count: number }[];
  alertesRecentes: Alerte[];
}

@Component({
  selector: "app-anl-dashboard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    BaseChartDirective,
    StatCardComponent,
    AlertBadgeComponent,
  ],
  templateUrl: "./anl-dashboard.component.html",
  styleUrls: ["./anl-dashboard.component.scss"],
})
export class AnlDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data = signal<AnlDashboardData | null>(null);

  barChartData: ChartData<"bar"> = {
    labels: ["Très faible", "Faible", "Moyen", "Bon", "Très bon"],
    datasets: [
      {
        data: [],
        label: "Clients",
        backgroundColor: [
          "#ef4444",
          "#f59e0b",
          "#3b82f6",
          "#22c55e",
          "#1b2f4b",
        ],
      },
    ],
  };

  barChartOptions: ChartOptions<"bar"> = {
    responsive: true,
    plugins: { legend: { display: false } },
    scales: { y: { beginAtZero: true } },
  };

  ngOnInit() {
    this.api.get<AnlDashboardData>("/api/v1/analyste/dashboard").subscribe({
      next: (d: AnlDashboardData) => {
        this.data.set(d);
        this.barChartData = {
          ...this.barChartData,
          datasets: [
            {
              ...this.barChartData.datasets[0],
              data: (d.scoringDistribution ?? []).map((s) => s.count),
            },
          ],
        };
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}

import { Component, OnInit } from "@angular/core";
import { AnalysteService, ModeleInfo } from "../analyste.service";
import { ChartData, ChartOptions } from "chart.js";

@Component({
  selector: "imf-anl-modele",
  templateUrl: "./anl-modele.component.html",
  styleUrls: ["./anl-modele.component.scss"],
})
export class AnlModeleComponent implements OnInit {
  modele: ModeleInfo | null = null;
  loading = false;
  psiChartData: ChartData<"line"> = { labels: [], datasets: [] };

  readonly lineOptions: ChartOptions<"line"> = {
    responsive: true,
    plugins: {
      legend: { display: false },
      annotation: {
        annotations: {
          seuilAttn: {
            type: "line",
            yMin: 0.1,
            yMax: 0.1,
            borderColor: "#f59e0b",
            borderWidth: 2,
            borderDash: [6, 4],
          },
          seuilDrift: {
            type: "line",
            yMin: 0.2,
            yMax: 0.2,
            borderColor: "#dc2626",
            borderWidth: 2,
            borderDash: [6, 4],
          },
        },
      },
    } as any,
    scales: { y: { beginAtZero: true, max: 0.4 } },
  };

  constructor(private service: AnalysteService) {}

  ngOnInit(): void {
    this.loading = true;
    this.service.getModeleInfo().subscribe({
      next: (m) => {
        this.modele = m;
        this.buildChart(m);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  private buildChart(m: ModeleInfo): void {
    this.psiChartData = {
      labels: m.evolutionPsi.map((e) => e.date),
      datasets: [
        {
          label: "PSI",
          data: m.evolutionPsi.map((e) => e.valeur),
          borderColor: "#1e3a5f",
          backgroundColor: "rgba(30,58,95,0.1)",
          fill: true,
          tension: 0.4,
        },
      ],
    };
  }

  getStatutClass(s: string): string {
    return (
      { STABLE: "badge-ok", ATTENTION: "badge-warn", DERIVE: "badge-alert" }[
        s
      ] ?? ""
    );
  }
  getStatutLabel(s: string): string {
    return (
      { STABLE: "STABLE", ATTENTION: "ATTENTION", DERIVE: "DÉRIVE DÉTECTÉE" }[
        s
      ] ?? s
    );
  }
}

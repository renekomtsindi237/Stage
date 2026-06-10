import { Component, OnInit } from "@angular/core";
import { PageEvent } from "@angular/material/paginator";
import { AnalysteService, ScoringClient } from "../analyste.service";
import { ChartData, ChartOptions } from "chart.js";

@Component({
  selector: "imf-anl-scoring",
  templateUrl: "./anl-scoring.component.html",
  styleUrls: ["./anl-scoring.component.scss"],
})
export class AnlScoringComponent implements OnInit {
  clients: ScoringClient[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  niveauFiltre = "";

  readonly niveaux = ["", "FAIBLE", "MODERE", "ELEVE", "CRITIQUE"];
  readonly colonnes = ["clientIdExterne", "nomComplet", "agence", "scoreMcrs", "niveauRisque", "probaDefaut30j", "probaDefaut90j", "encours", "cobacClasse", "provisionRequise"];

  distributionData: ChartData<"bar"> = { labels: [], datasets: [] };
  repartitionData: ChartData<"doughnut"> = { labels: [], datasets: [] };

  readonly barOptions: ChartOptions<"bar"> = {
    responsive: true,
    plugins: { legend: { display: false } },
    scales: { y: { beginAtZero: true } },
  };

  readonly doughnutOptions: ChartOptions<"doughnut"> = {
    responsive: true,
    plugins: { legend: { position: "bottom" } },
  };

  constructor(private service: AnalysteService) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.service.getScoringClients(this.page, this.pageSize, this.niveauFiltre || undefined).subscribe({
      next: (res) => {
        this.clients = res.content ?? res;
        this.total = res.totalElements ?? this.clients.length;
        this.buildCharts();
        this.loading = false;
      },
      error: () => { this.loading = false; },
    });
  }

  onPageChange(e: PageEvent): void { this.page = e.pageIndex; this.pageSize = e.pageSize; this.charger(); }
  onFiltreChange(): void { this.page = 0; this.charger(); }

  private buildCharts(): void {
    const buckets: Record<string, number> = { FAIBLE: 0, MODERE: 0, ELEVE: 0, CRITIQUE: 0 };
    this.clients.forEach(c => { if (buckets[c.niveauRisque] !== undefined) buckets[c.niveauRisque]++; });
    this.repartitionData = {
      labels: ["Faible", "Modéré", "Élevé", "Critique"],
      datasets: [{ data: Object.values(buckets), backgroundColor: ["#22c55e", "#f59e0b", "#ef4444", "#991b1b"] }],
    };
    const ranges = Array.from({ length: 20 }, (_, i) => `${i * 50}–${(i + 1) * 50}`);
    const counts = new Array(20).fill(0);
    this.clients.forEach(c => { const i = Math.min(Math.floor(c.scoreMcrs / 50), 19); counts[i]++; });
    this.distributionData = {
      labels: ranges,
      datasets: [{ label: "Clients", data: counts, backgroundColor: "rgba(37,99,235,0.6)" }],
    };
  }

  getBadgeClass(niveau: string): string {
    return { CRITIQUE: "badge-critique", ELEVE: "badge-eleve", MODERE: "badge-modere", FAIBLE: "badge-faible" }[niveau] ?? "";
  }
}

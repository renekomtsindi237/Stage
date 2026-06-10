import { Component, OnInit } from "@angular/core";
import { AnalysteService, TraitementDag } from "../analyste.service";

@Component({
  selector: "imf-anl-traitements",
  templateUrl: "./anl-traitements.component.html",
  styleUrls: ["./anl-traitements.component.scss"],
})
export class AnlTraitementsComponent implements OnInit {
  traitements: TraitementDag[] = [];
  loading = false;
  dernierRun: string | null = null;
  statutGlobal: "SUCCESS" | "RUNNING" | "FAILED" | "PENDING" = "PENDING";

  constructor(private service: AnalysteService) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.service.getTraitements().subscribe({
      next: (data) => {
        this.traitements = data.map(d => ({ ...d, nomMetier: this.service.getNomMetier(d.dagId) }));
        this.dernierRun = data[0]?.derniereExecution ?? null;
        this.statutGlobal = data.some(d => d.statut === "FAILED") ? "FAILED"
          : data.some(d => d.statut === "RUNNING") ? "RUNNING" : "SUCCESS";
        this.loading = false;
      },
      error: () => { this.loading = false; },
    });
  }

  getStatutClass(statut: string): string {
    return { SUCCESS: "badge-ok", RUNNING: "badge-running", FAILED: "badge-alert", PENDING: "badge-gray" }[statut] ?? "";
  }

  formatDuree(s: number): string {
    if (!s) return "—";
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return m > 0 ? `${m}m ${sec}s` : `${sec}s`;
  }
}

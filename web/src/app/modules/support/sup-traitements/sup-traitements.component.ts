import { Component, OnInit } from "@angular/core";
import { SupportService, DagRun } from "../support.service";
import { MatSnackBar } from "@angular/material/snack-bar";

@Component({
  selector: "imf-sup-traitements",
  templateUrl: "./sup-traitements.component.html",
  styleUrls: ["./sup-traitements.component.scss"],
})
export class SupTraitementsComponent implements OnInit {
  dags: DagRun[] = [];
  loading = false;
  triggering: string | null = null;

  readonly cols = ["nom", "schedule", "statut", "dernier", "duree", "prochain", "tentative", "actions"];

  constructor(private svc: SupportService, private snack: MatSnackBar) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.svc.getDagRuns().subscribe({
      next: d => { this.dags = d; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  trigger(dagId: string, nom: string): void {
    this.triggering = dagId;
    this.svc.triggerDag(dagId).subscribe({
      next: () => {
        this.snack.open(`Traitement "${nom}" déclenché manuellement`, "Fermer", { duration: 3000 });
        this.triggering = null;
        setTimeout(() => this.charger(), 2000);
      },
      error: () => { this.triggering = null; this.snack.open("Erreur lors du déclenchement", "Fermer", { duration: 3000 }); },
    });
  }

  getStatutClass(s: string): string {
    return { success: "badge-ok", running: "badge-info", failed: "badge-critique", queued: "badge-warn", skipped: "" }[s] ?? "";
  }

  formatDuree(sec?: number): string {
    if (!sec) return "—";
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
  }

  get nbEchoues(): number { return this.dags.filter(d => d.statut === "failed").length; }
  get nbEnCours(): number { return this.dags.filter(d => d.statut === "running").length; }
}

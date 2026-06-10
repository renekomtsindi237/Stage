import { Component, OnInit } from "@angular/core";
import { SupportService, AlerteSysteme } from "../support.service";
import { MatSnackBar } from "@angular/material/snack-bar";

@Component({
  selector: "imf-sup-alertes",
  templateUrl: "./sup-alertes.component.html",
  styleUrls: ["./sup-alertes.component.scss"],
})
export class SupAlertesComponent implements OnInit {
  alertes: AlerteSysteme[] = [];
  loading = false;
  filtreStatut = "";
  filtreSeverite = "";

  readonly cols = [
    "timestamp",
    "type",
    "titre",
    "source",
    "severite",
    "statut",
    "actions",
  ];

  constructor(
    private svc: SupportService,
    private snack: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.loading = true;
    this.svc.getAlertes().subscribe({
      next: (a) => {
        this.alertes = a;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  acquitter(id: number): void {
    this.svc.acquitterAlerte(id).subscribe({
      next: () => {
        this.snack.open("Alerte acquittée", "Fermer", { duration: 2500 });
        this.charger();
      },
      error: () => this.snack.open("Erreur", "Fermer", { duration: 2500 }),
    });
  }

  getFiltrees(): AlerteSysteme[] {
    return this.alertes.filter(
      (a) =>
        (!this.filtreStatut || a.statut === this.filtreStatut) &&
        (!this.filtreSeverite || a.severite === this.filtreSeverite),
    );
  }

  getBadgeSeverite(s: string): string {
    return (
      { CRITIQUE: "badge-critique", WARN: "badge-warn", INFO: "badge-info" }[
        s
      ] ?? ""
    );
  }

  getBadgeStatut(s: string): string {
    return (
      { ACTIVE: "badge-critique", EN_COURS: "badge-warn", RESOLUE: "badge-ok" }[
        s
      ] ?? ""
    );
  }

  get nbCritiques(): number {
    return this.alertes.filter(
      (a) => a.severite === "CRITIQUE" && a.statut !== "RESOLUE",
    ).length;
  }
  get nbActives(): number {
    return this.alertes.filter((a) => a.statut === "ACTIVE").length;
  }
}

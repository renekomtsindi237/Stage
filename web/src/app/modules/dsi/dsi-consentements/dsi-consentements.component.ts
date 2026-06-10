import { Component, OnInit } from "@angular/core";
import { DsiService, Consentement } from "../dsi.service";
import { MatSnackBar } from "@angular/material/snack-bar";

@Component({
  selector: "imf-dsi-consentements",
  templateUrl: "./dsi-consentements.component.html",
  styleUrls: ["./dsi-consentements.component.scss"],
})
export class DsiConsentementsComponent implements OnInit {
  consentements: Consentement[] = [];
  total = 0;
  loading = false;
  page = 0;
  size = 20;
  filtreStatut = "";

  readonly cols = ["utilisateur", "role", "finalite", "canal", "date", "statut", "actions"];

  readonly FINALITES: Record<string, string> = {
    TRAITEMENT_CREDIT: "Évaluation crédit",
    COMMUNICATION_MARKETING: "Communications marketing",
    PARTAGE_PARTENAIRES: "Partage avec partenaires",
    PROFILAGE_RISQUE: "Profilage risque",
    STATISTIQUES: "Statistiques agrégées",
  };

  constructor(private dsi: DsiService, private snack: MatSnackBar) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.dsi.getConsentements(this.page, this.size).subscribe({
      next: r => { this.consentements = r.content; this.total = r.totalElements; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  onPage(e: any): void { this.page = e.pageIndex; this.size = e.pageSize; this.charger(); }

  revoquer(id: number): void {
    this.dsi.revoquerConsentement(id).subscribe({
      next: () => { this.snack.open("Consentement révoqué", "Fermer", { duration: 3000 }); this.charger(); },
      error: () => this.snack.open("Erreur lors de la révocation", "Fermer", { duration: 3000 }),
    });
  }

  getFinalite(code: string): string { return this.FINALITES[code] ?? code; }

  get accordes(): number { return this.consentements.filter(c => c.statut === "ACCORDE").length; }
  get revoques(): number { return this.consentements.filter(c => c.statut === "REVOQUE").length; }

  getFiltres(): Consentement[] {
    return this.filtreStatut ? this.consentements.filter(c => c.statut === this.filtreStatut) : this.consentements;
  }
}

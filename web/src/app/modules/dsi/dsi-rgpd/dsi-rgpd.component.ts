import { Component, OnInit, OnDestroy } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { DsiService, ViolationDonnees, DemandreDroit, Consentement } from "../dsi.service";
import { DsiViolationDialogComponent } from "./dsi-violation-dialog.component";

@Component({
  selector: "imf-dsi-rgpd",
  templateUrl: "./dsi-rgpd.component.html",
  styleUrls: ["./dsi-rgpd.component.scss"],
})
export class DsiRgpdComponent implements OnInit, OnDestroy {
  violations: ViolationDonnees[] = [];
  demandes: DemandreDroit[] = [];
  consentements: Consentement[] = [];
  loading = false;

  countdowns: Record<string, string> = {};
  private timerInterval?: number;

  readonly colsViolations = ["type", "date", "concernes", "severite", "delai", "statut", "actions"];
  readonly colsDemandes = ["type", "sujet", "date", "delai", "statut", "actions"];
  readonly droitsLegende = [
    { code: "ACCES", label: "Droit d'accès" },
    { code: "RECTIFICATION", label: "Rectification" },
    { code: "EFFACEMENT", label: "Effacement" },
    { code: "OPPOSITION", label: "Opposition" },
    { code: "PORTABILITE", label: "Portabilité" },
  ];

  readonly TYPE_DROITS: Record<string, string> = {
    ACCES: "Droit d'accès",
    RECTIFICATION: "Rectification des données",
    EFFACEMENT: "Effacement (droit à l'oubli)",
    OPPOSITION: "Opposition au traitement",
    PORTABILITE: "Portabilité des données",
    LIMITATION: "Limitation du traitement",
  };

  constructor(private dsi: DsiService, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.charger();
    this.timerInterval = window.setInterval(() => this.mettreAJourCompteurs(), 1000);
  }

  ngOnDestroy(): void {
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  mettreAJourCompteurs(): void {
    this.violations.forEach(v => {
      if (v.statut === 'EN_COURS' && v.delaiRestantHeures != null) {
        const totalSec = v.delaiRestantHeures * 3600;
        const h = Math.floor(totalSec / 3600);
        const m = Math.floor((totalSec % 3600) / 60);
        const s = totalSec % 60;
        this.countdowns[v.id] = `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
      }
    });
  }

  charger(): void {
    this.loading = true;
    this.dsi.getViolations().subscribe({ next: v => { this.violations = v; this.mettreAJourCompteurs(); this.loading = false; }, error: () => { this.loading = false; } });
    this.dsi.getDemandesDroits().subscribe({ next: d => { this.demandes = d; }, error: () => {} });
    this.dsi.getConsentements(0, 10).subscribe({ next: (page: any) => { this.consentements = page.content ?? []; }, error: () => {} });
  }

  ouvrirDeclaration(): void {
    const ref = this.dialog.open(DsiViolationDialogComponent, { width: "640px" });
    ref.afterClosed().subscribe(data => { if (data) this.charger(); });
  }

  traiter(id: number, statut: string): void {
    this.dsi.traiterDemande(id, statut).subscribe(() => this.charger());
  }

  getDelaiClass(heures?: number): string {
    if (!heures) return "";
    if (heures < 12) return "delai-urgent";
    if (heures < 36) return "delai-warning";
    return "";
  }

  getTypeDroit(code: string): string {
    return this.TYPE_DROITS[code] ?? code;
  }

  getBadgeSeverite(s: string): string {
    return { CRITIQUE: "badge-critique", MAJEURE: "badge-majeur", MINEURE: "badge-ok" }[s] ?? "";
  }

  getBadgeStatut(s: string): string {
    return { EN_COURS: "badge-warn", TRAITE: "badge-ok", DEPASSE: "badge-critique" }[s] ?? "";
  }

  getViolationsEnCours(): number {
    return this.violations.filter(v => v.statut === "EN_COURS").length;
  }

  getDemandesEnAttente(): number {
    return this.demandes.filter(d => d.statut === "EN_ATTENTE").length;
  }

  getViolationsDepassees(): number {
    return this.violations.filter(v => v.statut === "DEPASSE").length;
  }

  getViolationsEnCoursList(): ViolationDonnees[] {
    return this.violations.filter(v => v.statut === "EN_COURS");
  }

  notifierAutorite(v: ViolationDonnees): void {
    // Placeholder — ouvre dialog ou appelle endpoint dédié
    alert(`Notification de l'autorité pour la violation #${v.id} — fonctionnalité à brancher.`);
  }

  revoquerConsentement(id: number): void {
    this.dsi.revoquerConsentement(id).subscribe(() => this.charger());
  }

  getBadgeConsentement(statut: string): string {
    return statut === 'ACCORDE' ? 'badge-ok' : 'badge-critique';
  }
}

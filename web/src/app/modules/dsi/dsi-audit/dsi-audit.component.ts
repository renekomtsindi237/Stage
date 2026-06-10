import { Component, OnInit, ViewChild } from "@angular/core";
import { MatPaginator } from "@angular/material/paginator";
import { DsiService, AuditEntry } from "../dsi.service";

@Component({
  selector: "imf-dsi-audit",
  templateUrl: "./dsi-audit.component.html",
  styleUrls: ["./dsi-audit.component.scss"],
})
export class DsiAuditComponent implements OnInit {
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  entries: AuditEntry[] = [];
  total = 0;
  loading = false;
  search = "";
  actionFiltree = "";
  entiteFiltree = "";
  page = 0;
  size = 50;

  readonly cols = ["horodatage", "utilisateur", "action", "entite", "resume", "ip"];

  readonly ACTIONS = ["", "CONNEXION", "DECONNEXION", "CREATION", "MODIFICATION", "SUPPRESSION", "EXPORT", "CONSULTATION"];
  readonly ENTITES = ["", "CLIENT", "PRET", "COLLECTE", "DOSSIER", "UTILISATEUR", "CONFIGURATION", "RAPPORT"];

  constructor(private dsi: DsiService) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.dsi.getAuditTrail(this.page, this.size, this.search, this.actionFiltree, this.entiteFiltree).subscribe({
      next: r => { this.entries = r.content; this.total = r.totalElements; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  onPage(e: any): void {
    this.page = e.pageIndex;
    this.size = e.pageSize;
    this.charger();
  }

  exporter(): void {
    this.dsi.exportAudit().subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `audit_trail_${new Date().toISOString().split("T")[0]}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  getActionClass(action: string): string {
    const map: Record<string, string> = {
      CONNEXION: "badge-ok", DECONNEXION: "badge-ok",
      CREATION: "badge-info", MODIFICATION: "badge-warn",
      SUPPRESSION: "badge-critique", EXPORT: "badge-info",
    };
    return map[action] ?? "";
  }
}

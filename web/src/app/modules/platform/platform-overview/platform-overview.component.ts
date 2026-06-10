import { Component, OnInit } from "@angular/core";
import { Router } from "@angular/router";
import { AuthService } from "@core/services/auth.service";
import { PlatformService, PlatformStats, ImfRecord } from "../platform.service";
import { fadeInUp, staggerIn, reveal } from "../../../shared/animations";

@Component({
  selector: "imf-platform-overview",
  templateUrl: "./platform-overview.component.html",
  styleUrls: ["./platform-overview.component.scss"],
  animations: [fadeInUp, staggerIn, reveal],
})
export class PlatformOverviewComponent implements OnInit {
  stats: PlatformStats | null = null;
  allImfs: ImfRecord[] = [];
  recentImfs: ImfRecord[] = [];
  paysStatsList: { pays: string; count: number; pct: number }[] = [];
  loading = true;
  errorCode = 0;

  readonly today = new Date();

  // ── Données graphique barres (12 derniers mois) ──────────────────────────
  barLabels: string[] = [];
  barActive: number[] = [];
  barInactive: number[] = [];

  constructor(
    public auth: AuthService,
    private platformService: PlatformService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorCode = 0;

    this.platformService.getStats().subscribe({
      next: (s) => {
        this.stats = s;
        this.loading = false;
      },
      error: (err) => {
        this.errorCode = err?.status ?? -1;
        this.loading = false;
      },
    });

    this.platformService.listImfs().subscribe({
      next: (list) => {
        this.allImfs = list;
        this.recentImfs = [...list]
          .sort(
            (a, b) =>
              new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
          )
          .slice(0, 5);
        this.buildBarData(list);
        this.buildPaysStats(list);
      },
      error: () => {},
    });
  }

  private buildPaysStats(imfs: ImfRecord[]): void {
    const map: Record<string, number> = {};
    imfs.forEach((i) => {
      map[i.pays] = (map[i.pays] || 0) + 1;
    });
    const total = imfs.length || 1;
    this.paysStatsList = Object.entries(map)
      .map(([pays, count]) => ({
        pays,
        count,
        pct: Math.round((count / total) * 100),
      }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 6);
  }

  /** Construit les données du graphique en barres : IMF créées par mois (12 derniers mois) */
  private buildBarData(imfs: ImfRecord[]): void {
    const now = new Date();
    const months: { label: string; active: number; inactive: number }[] = [];

    for (let i = 11; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      const label = d.toLocaleDateString("fr-FR", { month: "short" });
      const y = d.getFullYear();
      const m = d.getMonth();

      const inMonth = imfs.filter((imf) => {
        const c = new Date(imf.createdAt);
        return c.getFullYear() === y && c.getMonth() === m;
      });

      months.push({
        label: label.charAt(0).toUpperCase() + label.slice(1),
        active: inMonth.filter((i) => i.actif).length,
        inactive: inMonth.filter((i) => !i.actif).length,
      });
    }

    this.barLabels = months.map((m) => m.label);
    this.barActive = months.map((m) => m.active);
    this.barInactive = months.map((m) => m.inactive);
  }

  get inactiveImfsCount(): number {
    return this.allImfs.filter((i) => !i.actif).length;
  }

  get hasInactiveImfs(): boolean {
    return this.inactiveImfsCount > 0;
  }

  /** Taux d'activation en % */
  get activationRate(): number {
    if (!this.stats || this.stats.totalImfs === 0) return 0;
    return Math.round((this.stats.activeImfs / this.stats.totalImfs) * 100);
  }

  get dsiCount(): number {
    return this.allImfs.filter((i) => i.hasDsi).length;
  }

  /** Taux de couverture DSI */
  get dsiRate(): number {
    if (!this.allImfs.length) return 0;
    return Math.round(
      (this.allImfs.filter((i) => i.hasDsi).length / this.allImfs.length) * 100,
    );
  }

  trackByImf(_: number, imf: ImfRecord): string {
    return imf.uid;
  }
  trackByPays(_: number, p: { pays: string }): string {
    return p.pays;
  }

  getImfLogo(code: string): string {
    return this.auth.getImfLogo(code) || "assets/photo_profil.jpg";
  }

  goToImf(): void {
    this.router.navigate(["/platform/imf"]);
  }
  goToCreateImf(): void {
    this.router.navigate(["/platform/imf"], {
      queryParams: { action: "create" },
    });
  }
  logout(): void {
    this.auth.logout();
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString("fr-FR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  }

  get greeting(): string {
    const h = this.today.getHours();
    if (h < 12) return "Bonjour";
    if (h < 18) return "Bon après-midi";
    return "Bonsoir";
  }
}

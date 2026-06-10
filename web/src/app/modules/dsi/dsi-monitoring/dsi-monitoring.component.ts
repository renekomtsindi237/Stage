import { Component, OnInit, OnDestroy } from "@angular/core";
import { interval, Subscription } from "rxjs";
import { switchMap } from "rxjs/operators";
import { DsiService, ServiceSante } from "../dsi.service";

@Component({
  selector: "imf-dsi-monitoring",
  templateUrl: "./dsi-monitoring.component.html",
  styleUrls: ["./dsi-monitoring.component.scss"],
})
export class DsiMonitoringComponent implements OnInit, OnDestroy {
  services: ServiceSante[] = [];
  loading = false;
  derniereMaj = new Date();
  private poll$?: Subscription;

  readonly ICONES: Record<string, string> = {
    "Backend API": "dns",
    "Backend Core": "dns",
    "ML Scoring": "psychology",
    "ML API": "psychology",
    "Base de données": "storage",
    PostgreSQL: "storage",
    Cache: "memory",
    "Redis Cache": "memory",
  };

  constructor(private dsi: DsiService) {}

  ngOnInit(): void {
    this.charger();
    this.poll$ = interval(30_000)
      .pipe(switchMap(() => this.dsi.getSantesServices()))
      .subscribe({
        next: (s) => {
          this.services = s;
          this.derniereMaj = new Date();
        },
      });
  }

  ngOnDestroy(): void {
    this.poll$?.unsubscribe();
  }

  charger(): void {
    this.loading = true;
    this.dsi.getSantesServices().subscribe({
      next: (s) => {
        this.services = s;
        this.loading = false;
        this.derniereMaj = new Date();
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  getIcone(nom: string): string {
    return this.ICONES[nom] ?? "dns";
  }

  getStatutClass(s: string): string {
    return (
      { UP: "statut-up", DOWN: "statut-down", DEGRADE: "statut-degrade" }[s] ??
      ""
    );
  }

  getStatutLabel(s: string): string {
    return (
      { UP: "Opérationnel", DOWN: "Hors service", DEGRADE: "Dégradé" }[s] ?? s
    );
  }

  get nbUp(): number {
    return this.services.filter((s) => s.statut === "UP").length;
  }
  get nbDown(): number {
    return this.services.filter((s) => s.statut === "DOWN").length;
  }
  get nbDegrade(): number {
    return this.services.filter((s) => s.statut === "DEGRADE").length;
  }
}

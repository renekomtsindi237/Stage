import { Component, OnInit, OnDestroy } from "@angular/core";
import { interval, Subscription } from "rxjs";
import { switchMap } from "rxjs/operators";
import { SupportService } from "../support.service";

@Component({
  selector: "imf-sup-overview",
  templateUrl: "./sup-overview.component.html",
  styleUrls: ["./sup-overview.component.scss"],
})
export class SupOverviewComponent implements OnInit, OnDestroy {
  overview: any = null;
  loading = false;
  derniereMaj = new Date();
  private poll$?: Subscription;

  constructor(private svc: SupportService) {}

  ngOnInit(): void {
    this.charger();
    this.poll$ = interval(20_000).pipe(switchMap(() => this.svc.getSystemOverview())).subscribe({
      next: d => { this.overview = d; this.derniereMaj = new Date(); },
    });
  }

  ngOnDestroy(): void { this.poll$?.unsubscribe(); }

  charger(): void {
    this.loading = true;
    this.svc.getSystemOverview().subscribe({
      next: d => { this.overview = d; this.loading = false; this.derniereMaj = new Date(); },
      error: () => { this.loading = false; },
    });
  }

  getGlobalStatut(): "ok" | "warn" | "down" {
    if (!this.overview) return "ok";
    if (this.overview.alertesCritiques > 0 || this.overview.containersDown > 0) return "down";
    if (this.overview.alertesActives > 0 || this.overview.containersDegrade > 0) return "warn";
    return "ok";
  }
}

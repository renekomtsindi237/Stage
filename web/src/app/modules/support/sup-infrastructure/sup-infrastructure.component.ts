import { Component, OnInit, OnDestroy } from "@angular/core";
import { interval, Subscription } from "rxjs";
import { switchMap } from "rxjs/operators";
import {
  SupportService,
  ContainerDocker,
  VpsMetrics,
} from "../support.service";

@Component({
  selector: "imf-sup-infrastructure",
  templateUrl: "./sup-infrastructure.component.html",
  styleUrls: ["./sup-infrastructure.component.scss"],
})
export class SupInfrastructureComponent implements OnInit, OnDestroy {
  containers: ContainerDocker[] = [];
  vps: VpsMetrics | null = null;
  loading = false;
  private poll$?: Subscription;

  readonly cols = [
    "nom",
    "image",
    "statut",
    "uptime",
    "cpu",
    "mem",
    "ports",
    "restarts",
  ];

  constructor(private svc: SupportService) {}

  ngOnInit(): void {
    this.charger();
    this.poll$ = interval(15_000)
      .pipe(switchMap(() => this.svc.getContainersDocker()))
      .subscribe({
        next: (c) => {
          this.containers = c;
        },
      });
  }

  ngOnDestroy(): void {
    this.poll$?.unsubscribe();
  }

  charger(): void {
    this.loading = true;
    this.svc.getContainersDocker().subscribe({
      next: (c) => {
        this.containers = c;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
    this.svc.getVpsMetrics().subscribe({
      next: (v) => {
        this.vps = v;
      },
      error: () => {},
    });
  }

  getStatutClass(s: string): string {
    return (
      {
        running: "badge-ok",
        exited: "badge-critique",
        restarting: "badge-warn",
        paused: "badge-info",
      }[s] ?? ""
    );
  }

  getCpuClass(val: number): string {
    if (val > 80) return "alerte";
    if (val > 60) return "warn";
    return "";
  }

  getMemClass(val: number, max: number): string {
    const pct = (val / max) * 100;
    if (pct > 85) return "alerte";
    if (pct > 65) return "warn";
    return "";
  }

  get containersOk(): number {
    return this.containers.filter((c) => c.statut === "running").length;
  }
  get containersDown(): number {
    return this.containers.filter((c) => c.statut === "exited").length;
  }

  memPct(c: ContainerDocker): number {
    return Math.round((c.memoire / c.memoireMax) * 100);
  }
}

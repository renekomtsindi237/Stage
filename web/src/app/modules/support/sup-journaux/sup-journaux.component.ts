import { Component, OnInit } from "@angular/core";
import { SupportService, LogEntry } from "../support.service";

@Component({
  selector: "imf-sup-journaux",
  templateUrl: "./sup-journaux.component.html",
  styleUrls: ["./sup-journaux.component.scss"],
})
export class SupJournauxComponent implements OnInit {
  entries: LogEntry[] = [];
  total = 0;
  loading = false;
  search = "";
  niveauFiltre = "";
  sourceFiltree = "";
  page = 0;
  size = 100;
  autoRefresh = false;
  private timer: any;

  readonly NIVEAUX = ["", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL"];
  readonly SOURCES = [
    "",
    "backend",
    "ml-api",
    "airflow",
    "nginx",
    "postgres",
    "redis",
  ];
  readonly cols = ["timestamp", "niveau", "source", "message"];

  constructor(private svc: SupportService) {}

  ngOnInit(): void {
    this.charger();
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  charger(): void {
    this.loading = true;
    this.svc
      .getJournaux(
        this.page,
        this.size,
        this.niveauFiltre,
        this.sourceFiltree,
        this.search,
      )
      .subscribe({
        next: (r) => {
          this.entries = r.content;
          this.total = r.totalElements;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  onPage(e: any): void {
    this.page = e.pageIndex;
    this.size = e.pageSize;
    this.charger();
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh) {
      this.timer = setInterval(() => this.charger(), 5000);
    } else {
      clearInterval(this.timer);
    }
  }

  getNiveauClass(n: string): string {
    return (
      {
        DEBUG: "lvl-debug",
        INFO: "lvl-info",
        WARN: "lvl-warn",
        ERROR: "lvl-error",
        CRITICAL: "lvl-critical",
      }[n] ?? ""
    );
  }

  isAlerte(n: string): boolean {
    return n === "ERROR" || n === "CRITICAL";
  }
}

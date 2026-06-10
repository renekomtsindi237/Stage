import { Component, OnInit } from "@angular/core";
import { PlatformService, PlatformConfig } from "../platform.service";

@Component({
  selector: "imf-platform-config",
  templateUrl: "./platform-config.component.html",
  styleUrls: ["./platform-config.component.scss"],
})
export class PlatformConfigComponent implements OnInit {
  config: PlatformConfig | null = null;
  loading = true;
  error = false;

  constructor(private service: PlatformService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = false;
    this.service.getConfig().subscribe({
      next: (c) => { this.config = c; this.loading = false; },
      error: () => { this.loading = false; this.error = true; },
    });
  }

  formatMs(minutes: number): string {
    if (minutes >= 60) return `${minutes / 60} heure${minutes / 60 > 1 ? "s" : ""}`;
    return `${minutes} minute${minutes > 1 ? "s" : ""}`;
  }

  formatDays(days: number): string {
    return `${days} jour${days > 1 ? "s" : ""}`;
  }
}

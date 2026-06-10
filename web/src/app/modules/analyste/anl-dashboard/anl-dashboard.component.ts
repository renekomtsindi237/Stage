import { Component, OnInit } from "@angular/core";
import { KpiService } from "../../dashboard/kpi.service";

@Component({
  selector: "imf-anl-dashboard",
  templateUrl: "./anl-dashboard.component.html",
  styleUrls: ["./anl-dashboard.component.scss"],
})
export class AnlDashboardComponent implements OnInit {
  summary: any = null;
  loading = true;

  constructor(private kpiService: KpiService) {}

  ngOnInit(): void {
    this.kpiService.getDashboardDirecteur().subscribe({
      next: (s) => { this.summary = s; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }
}

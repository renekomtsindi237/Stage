import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { forkJoin } from "rxjs";
import { ApiService } from "../../../core/http/api.service";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";
import {
  PlatformActualStats,
  ImfDetail,
} from "../../../core/models/platform.model";

@Component({
  selector: "app-platform-dashboard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent],
  templateUrl: "./platform-dashboard.component.html",
  styleUrls: ["./platform-dashboard.component.scss"],
})
export class PlatformDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  stats = signal<PlatformActualStats | null>(null);
  imfs = signal<ImfDetail[]>([]);

  ngOnInit() {
    forkJoin({
      stats: this.api.get<PlatformActualStats>("/api/v1/platform/stats"),
      imfs: this.api.get<ImfDetail[]>("/api/v1/platform/imf"),
    }).subscribe({
      next: ({ stats, imfs }) => {
        this.stats.set(stats);
        this.imfs.set((imfs ?? []).slice(0, 8));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}

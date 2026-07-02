import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink, RouterLinkActive } from "@angular/router";
import { ApiService } from "../../../core/http/api.service";
import { AgentDashboard } from "../../../core/models/client.model";
import { AuthService } from "../../../core/auth/auth.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { AlertBadgeComponent } from "../../../shared/components/alert-badge/alert-badge.component";
import { TranslatePipe } from "@ngx-translate/core";

@Component({
  selector: "app-agent-home",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    FcfaPipe,
    AlertBadgeComponent,
    TranslatePipe,
  ],
  templateUrl: "./agent-home.component.html",
  styleUrls: ["./agent-home.component.scss"],
})
export class AgentHomeComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);

  loading = signal(true);
  data = signal<AgentDashboard | null>(null);
  syncing = signal(false);

  ngOnInit() {
    this.load();
  }

  load() {
    this.api.get<AgentDashboard>("/api/v1/agent/dashboard").subscribe({
      next: (d: AgentDashboard) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  sync() {
    this.syncing.set(true);
    setTimeout(() => {
      this.syncing.set(false);
      this.load();
    }, 1500);
  }

  get progressPct(): number {
    const d = this.data();
    if (!d || !d.objectifJour) return 0;
    return Math.min(100, Math.round((d.collecteJour / d.objectifJour) * 100));
  }
}

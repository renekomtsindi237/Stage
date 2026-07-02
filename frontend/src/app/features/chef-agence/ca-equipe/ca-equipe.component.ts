import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";

interface TeamMember {
  uid: string;
  username: string;
  email: string;
  role: string;
  zoneId: string | null;
  avatarUrl: string | null;
  actif: boolean;
  lastLogin: string | null;
  createdAt: string;
}

interface TeamPage {
  content: TeamMember[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const ROLE_LABELS: Record<string, string> = {
  AGENT:                  "Agent terrain",
  AGENT_CREDIT:           "Chargé de clientèle",
  CHEF_AGENCE:            "Chef d'agence",
  CAISSIER:               "Caissier",
  AGENT_SAISIE:           "Agent de saisie",
  ANALYSTE_ENGAGEMENTS:   "Analyste engagements",
};

@Component({
  selector: "app-ca-equipe",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: "./ca-equipe.component.html",
  styleUrls: ["./ca-equipe.component.scss"],
})
export class CaEquipeComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly roleLabels = ROLE_LABELS;

  loading     = signal(true);
  pageData    = signal<TeamPage | null>(null);
  currentPage = signal(0);
  searchQuery = signal("");

  readonly members = computed(() => {
    const q = this.searchQuery().toLowerCase();
    if (!q) return this.pageData()?.content ?? [];
    return (this.pageData()?.content ?? []).filter(
      (m) =>
        m.username.toLowerCase().includes(q) ||
        (m.email?.toLowerCase().includes(q) ?? false) ||
        (m.zoneId?.toLowerCase().includes(q) ?? false),
    );
  });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api
      .get<TeamPage>("/api/v1/chef-agence/equipe", {
        page: this.currentPage(),
        size: 50,
      })
      .subscribe({
        next: (p) => {
          this.pageData.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  roleLabel(role: string): string {
    return ROLE_LABELS[role] ?? role;
  }

  roleClass(role: string): string {
    return {
      AGENT:                "badge-basse",
      AGENT_CREDIT:         "badge-primary",
      CHEF_AGENCE:          "badge-dark",
      CAISSIER:             "badge-moyenne",
      AGENT_SAISIE:         "badge-moyenne",
      ANALYSTE_ENGAGEMENTS: "badge-haute",
    }[role] ?? "badge-moyenne";
  }

  initials(username: string): string {
    return username
      .split(/[._-]/)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? "")
      .join("");
  }
}

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
import { ActivatedRoute } from "@angular/router";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";

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

interface MembrePerf {
  uid: string;
  username: string;
  role: string;
  zoneId: string | null;
  actif: boolean;
  collectesCount: number;
  collectesMontant: number;
  collectesCountPrec: number;
  collectesMontantPrec: number;
  evolutionPct: number;
  tendance: "HAUSSE" | "BAISSE" | "STABLE";
  dossiersSoumis: number;
  dossiersValides: number;
  dossiersRejetes: number;
  tauxValidation: number;
  clientsTouches: number;
}

interface EquipePerf {
  jours: number;
  debut: string;
  fin: string;
  membres: MembrePerf[];
}

const ROLE_LABELS: Record<string, string> = {
  AGENT: "ca_equipe.role_agent",
  AGENT_CREDIT: "ca_equipe.role_agent_credit",
  CHEF_AGENCE: "ca_equipe.role_chef_agence",
  CAISSIER: "ca_equipe.role_caissier",
  AGENT_SAISIE: "ca_equipe.role_agent_saisie",
  ANALYSTE_ENGAGEMENTS: "ca_equipe.role_analyste_engagements",
};

@Component({
  selector: "app-ca-equipe",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, TranslatePipe, FcfaPipe],
  templateUrl: "./ca-equipe.component.html",
  styleUrls: ["./ca-equipe.component.scss"],
})
export class CaEquipeComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly roleLabels = ROLE_LABELS;

  vue = signal<"annuaire" | "performances">("annuaire");
  jours = signal(30);

  loading = signal(true);
  pageData = signal<TeamPage | null>(null);
  currentPage = signal(0);
  searchQuery = signal("");

  perfLoading = signal(false);
  perf = signal<EquipePerf | null>(null);

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

  readonly perfFiltered = computed(() => {
    const q = this.searchQuery().toLowerCase();
    const list = this.perf()?.membres ?? [];
    if (!q) return list;
    return list.filter(
      (m) =>
        m.username.toLowerCase().includes(q) ||
        (m.zoneId?.toLowerCase().includes(q) ?? false),
    );
  });

  readonly maxMontant = computed(() => {
    const vals = this.perfFiltered().map(
      (m) => Number(m.collectesMontant) || 0,
    );
    return Math.max(1, ...vals, 0);
  });

  ngOnInit() {
    if (this.route.snapshot.queryParamMap.get("vue") === "performances") {
      this.vue.set("performances");
    }
    this.load();
    this.loadPerf();
  }

  setVue(v: "annuaire" | "performances") {
    this.vue.set(v);
    if (v === "performances" && !this.perf()) this.loadPerf();
  }

  setJours(n: number) {
    this.jours.set(n);
    this.loadPerf();
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

  loadPerf() {
    this.perfLoading.set(true);
    this.api
      .get<EquipePerf>("/api/v1/chef-agence/equipe/performances", {
        jours: this.jours(),
      })
      .subscribe({
        next: (p) => {
          this.perf.set(p);
          this.perfLoading.set(false);
        },
        error: () => this.perfLoading.set(false),
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
    return (
      {
        AGENT: "badge-basse",
        AGENT_CREDIT: "badge-primary",
        CHEF_AGENCE: "badge-dark",
        CAISSIER: "badge-moyenne",
        AGENT_SAISIE: "badge-moyenne",
        ANALYSTE_ENGAGEMENTS: "badge-haute",
      }[role] ?? "badge-moyenne"
    );
  }

  initials(username: string): string {
    return username
      .split(/[._-]/)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? "")
      .join("");
  }

  barPct(montant: number): number {
    return Math.round((Number(montant) / this.maxMontant()) * 100);
  }

  evoClass(t: string): string {
    if (t === "HAUSSE") return "evo-up";
    if (t === "BAISSE") return "evo-down";
    return "evo-flat";
  }
}

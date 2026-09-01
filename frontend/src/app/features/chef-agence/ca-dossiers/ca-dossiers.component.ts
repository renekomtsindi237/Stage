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
import { ToastService } from "../../../core/services/toast.service";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { EscCloseDirective } from "../../../shared/directives/esc-close.directive";

interface DossierRow {
  uid: string;
  clientNom: string;
  clientId: string;
  montantDemande: number;
  dureeMois: number;
  objetFinancement: string;
  secteurActivite: string;
  revenuEstime: number | null;
  chargesMensuelles: number | null;
  capaciteRemboursement: number | null;
  statut: string;
  noteAnalyse: string | null;
  dateSoumission: string | null;
  dateDecision: string | null;
  agentCreditId: number;
  agentNom?: string;
  createdAt: string;
}

interface DossierPage {
  content: DossierRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface AgentItem {
  uid: string;
  username: string;
  role: string;
  email: string;
  actif: boolean;
}

interface AgentPage {
  content: AgentItem[];
  totalElements: number;
  totalPages: number;
}

const STATUT_LABELS: Record<string, string> = {
  INSTRUCTION: "ca_dossiers.statut_instruction",
  EN_COMITE: "ca_dossiers.statut_en_comite",
  VALIDE: "ca_dossiers.statut_valide",
  APPROUVE: "ca_dossiers.statut_approuve",
  REJETE: "ca_dossiers.statut_rejete",
  AJOURNE: "ca_dossiers.statut_ajourne",
  DEBLOQUE: "ca_dossiers.statut_debloque",
};

const STATUT_TABS = [
  { label: "ca_dossiers.tab_tous", value: "" },
  { label: "ca_dossiers.tab_en_comite", value: "EN_COMITE" },
  { label: "ca_dossiers.tab_valide", value: "VALIDE" },
  { label: "ca_dossiers.tab_instruction", value: "INSTRUCTION" },
  { label: "ca_dossiers.tab_rejete", value: "REJETE" },
  { label: "ca_dossiers.tab_debloque", value: "DEBLOQUE" },
];

@Component({
  selector: "app-ca-dossiers",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    FcfaPipe,
    TranslatePipe,
    EscCloseDirective,
  ],
  templateUrl: "./ca-dossiers.component.html",
  styleUrls: ["./ca-dossiers.component.scss"],
})
export class CaDossiersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);

  readonly tabs = STATUT_TABS;
  readonly statutLabels = STATUT_LABELS;

  // ── List state ─────────────────────────────────────────────────────────────
  loading = signal(true);
  pageData = signal<DossierPage | null>(null);
  activeTab = signal("");
  currentPage = signal(0);
  searchQuery = signal("");

  readonly rows = computed(() => this.pageData()?.content ?? []);

  // ── Detail panel ───────────────────────────────────────────────────────────
  selected = signal<DossierRow | null>(null);
  validating = signal(false);
  motifRejeter = signal("");
  showMotif = signal(false);

  // ── Réassignation modal ────────────────────────────────────────────────────
  showReassign = signal(false);
  agents = signal<AgentItem[]>([]);
  agentsLoading = signal(false);
  selectedAgentUid = signal("");
  reassignMotif = signal("");
  reassigning = signal(false);

  ngOnInit() {
    // Lire le paramètre ?statut= de l'URL (lien depuis dashboard)
    this.route.queryParams.subscribe((p) => {
      if (p["statut"]) this.activeTab.set(p["statut"]);
      this.load();
    });
  }

  load() {
    this.loading.set(true);
    const params: Record<string, string | number | boolean | null | undefined> =
      {
        page: this.currentPage(),
        size: 20,
        statut: this.activeTab() || undefined,
      };

    this.api.get<DossierPage>("/api/v1/dossiers-credit", params).subscribe({
      next: (p) => {
        this.pageData.set(p);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  switchTab(value: string) {
    this.activeTab.set(value);
    this.currentPage.set(0);
    this.selected.set(null);
    this.load();
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  select(d: DossierRow) {
    this.selected.set(d);
    this.showMotif.set(false);
    this.motifRejeter.set("");
    this.showReassign.set(false);
  }

  closePanel() {
    this.selected.set(null);
  }

  // ── Valider / Rejeter ──────────────────────────────────────────────────────
  valider(decision: "VALIDE" | "REJETE") {
    const d = this.selected();
    if (!d) return;

    if (decision === "REJETE" && !this.motifRejeter()) {
      this.showMotif.set(true);
      return;
    }

    this.validating.set(true);
    this.api
      .patch<void>(`/api/v1/dossiers-credit/${d.uid}/valider-chef`, {
        action: decision,
        motif: this.motifRejeter() || "",
      })
      .subscribe({
        next: () => {
          this.toast.showI18nSuccess(
            "ca_dossiers.toast_validate_title",
            decision === "VALIDE"
              ? "ca_dossiers.toast_validate_body"
              : "ca_dossiers.toast_reject_body",
          );
          this.validating.set(false);
          this.showMotif.set(false);
          this.selected.set(null);
          this.load();
        },
        error: (err: unknown) => {
          this.toast.showApiError(err, "ca_dossiers.toast_error_body");
          this.validating.set(false);
        },
      });
  }

  // ── Réassignation ──────────────────────────────────────────────────────────
  openReassign() {
    this.showReassign.set(true);
    if (this.agents().length === 0) this.loadAgents();
  }

  loadAgents() {
    this.agentsLoading.set(true);
    this.api
      .get<AgentPage>("/api/v1/chef-agence/equipe", { size: 100 })
      .subscribe({
        next: (p) => {
          this.agents.set(
            p.content.filter((a) => a.role === "AGENT_CREDIT" && a.actif),
          );
          this.agentsLoading.set(false);
        },
        error: () => this.agentsLoading.set(false),
      });
  }

  submitReassign() {
    const d = this.selected();
    if (!d || !this.selectedAgentUid()) return;

    this.reassigning.set(true);
    this.api
      .patch<void>(`/api/v1/dossiers-credit/${d.uid}/reassigner`, {
        nouvelAgentUid: this.selectedAgentUid(),
        motif: this.reassignMotif() || null,
      })
      .subscribe({
        next: () => {
          this.toast.showI18nSuccess(
            "ca_dossiers.toast_reassign_title",
            "ca_dossiers.toast_reassign_body",
          );
          this.reassigning.set(false);
          this.showReassign.set(false);
          this.selected.set(null);
          this.load();
        },
        error: (err: unknown) => {
          this.toast.showApiError(err, "ca_dossiers.toast_reassign_error");
          this.reassigning.set(false);
        },
      });
  }

  closeReassign() {
    this.showReassign.set(false);
    this.selectedAgentUid.set("");
    this.reassignMotif.set("");
  }

  // ── Helpers ────────────────────────────────────────────────────────────────
  statutLabel(s: string): string {
    return STATUT_LABELS[s] ?? s;
  }

  statutClass(s: string): string {
    return (
      {
        INSTRUCTION: "badge-moyenne",
        EN_COMITE: "badge-primary",
        VALIDE: "badge-basse",
        APPROUVE: "badge-basse",
        REJETE: "badge-critique",
        AJOURNE: "badge-haute",
        DEBLOQUE: "badge-dark",
      }[s] ?? "badge-moyenne"
    );
  }

  canValidate(d: DossierRow): boolean {
    return d.statut === "EN_COMITE";
  }
}

import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { TranslatePipe, TranslateService } from "@ngx-translate/core";
import { ApiService } from "../../core/http/api.service";
import { AuthService } from "../../core/auth/auth.service";
import { ToastService } from "../../core/services/toast.service";
import { EscCloseDirective } from "../../shared/directives/esc-close.directive";
import { StatutLabelPipe } from "../../shared/pipes/statut-label.pipe";

// ── Types ────────────────────────────────────────────────────────────────────

interface Delegation {
  uid: string;
  typeDelegation: "REASSIGNATION_DOSSIER" | "DELEGATION_AUTORITE";
  delegantId: number;
  delegataireId: number;
  objetId: number | null;
  objetType: string | null;
  motif: string | null;
  roleDelegue: string | null;
  montantSeuil: number | null;
  dateDebut: string;
  dateFin: string | null;
  actif: boolean;
  createdAt: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

interface Dossier {
  uid: string;
  clientNom: string;
  clientId: string;
  montantDemande: number;
  dureeMois: number;
  objetFinancement: string;
  secteurActivite: string;
  statut: string;
  dateSoumission: string;
  agentCreditId: number | null;
}

interface AgentUser {
  uid: string;
  username: string;
  role: string;
  email: string;
  actif: boolean;
}

// ── Component ─────────────────────────────────────────────────────────────────

@Component({
  selector: "app-delegations",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslatePipe,
    EscCloseDirective,
    StatutLabelPipe,
  ],
  templateUrl: "./delegations.component.html",
  styleUrls: ["./delegations.component.scss"],
})
export class DelegationsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  private readonly i18n = inject(TranslateService);

  // ── Tabs ──────────────────────────────────────────────────────────────────
  activeTab = signal<"dossiers" | "delegations">("delegations");

  // ── Loading ───────────────────────────────────────────────────────────────
  loadingDelegations = signal(true);
  loadingDossiers = signal(true);
  loadingAgents = signal(false);
  submitting = signal(false);

  // ── Data ──────────────────────────────────────────────────────────────────
  delegations = signal<Delegation[]>([]);
  dossiers = signal<Dossier[]>([]);
  agents = signal<AgentUser[]>([]);

  totalDelegations = signal(0);
  totalDossiers = signal(0);

  // ── Modals ────────────────────────────────────────────────────────────────
  showReassignModal = signal(false);
  showAutoriteModal = signal(false);
  selectedDossier = signal<Dossier | null>(null);

  // ── Auth helpers ──────────────────────────────────────────────────────────
  readonly role = computed(() => this.auth.role() ?? "");
  readonly isDirecteur = computed(() => this.role() === "DIRECTEUR");
  readonly isDsi = computed(() => this.role() === "DSI");

  // ── Forms ─────────────────────────────────────────────────────────────────
  reassignForm = this.fb.group({
    nouvelAgentUid: ["", Validators.required],
    motif: [""],
  });

  autoriteForm = this.fb.group({
    delegataireUid: ["", Validators.required],
    roleDelegue: ["AGENT", Validators.required],
    montantSeuil: [null as number | null],
    dateFin: [""],
    motif: ["", Validators.required],
  });

  readonly rolesAutorites = [
    "AGENT",
    "ANALYSTE",
    "RESPONSABLE_RECOUVREMENT",
    "AGENT_CREDIT",
    "CAISSIER",
    "AGENT_SAISIE",
  ];

  // ── Statut colors ─────────────────────────────────────────────────────────
  statutColor(s: string): string {
    const map: Record<string, string> = {
      INSTRUCTION: "badge--info",
      EN_ANALYSE: "badge--warning",
      COMITE: "badge--primary",
      ACCORDE: "badge--success",
      DECAISSE: "badge--success",
      REFUSE: "badge--danger",
      SOLDE: "badge--neutral",
    };
    return map[s] ?? "badge--neutral";
  }

  typeLabel(t: string): string {
    return t === "REASSIGNATION_DOSSIER"
      ? this.i18n.instant("del.type_reassign")
      : this.i18n.instant("del.type_autorite");
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit() {
    this.loadDelegations();
    this.loadDossiers();
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadDelegations() {
    this.loadingDelegations.set(true);
    this.api
      .get<PageResponse<Delegation>>("/api/v1/delegations", {
        page: 0,
        size: 50,
      })
      .subscribe({
        next: (p) => {
          this.delegations.set(p?.content ?? []);
          this.totalDelegations.set(p?.totalElements ?? 0);
          this.loadingDelegations.set(false);
        },
        error: () => this.loadingDelegations.set(false),
      });
  }

  loadDossiers() {
    this.loadingDossiers.set(true);
    this.api
      .get<PageResponse<Dossier>>("/api/v1/dossiers-credit", {
        page: 0,
        size: 50,
      })
      .subscribe({
        next: (p) => {
          this.dossiers.set(p?.content ?? []);
          this.totalDossiers.set(p?.totalElements ?? 0);
          this.loadingDossiers.set(false);
        },
        error: () => this.loadingDossiers.set(false),
      });
  }

  private loadAgents() {
    if (this.agents().length > 0) return;
    this.loadingAgents.set(true);
    this.api.get<AgentUser[]>("/api/v1/delegations/agents-credit").subscribe({
      next: (list) => {
        this.agents.set(list ?? []);
        this.loadingAgents.set(false);
      },
      error: () => this.loadingAgents.set(false),
    });
  }

  private loadAllUsers() {
    if (this.agents().length > 0) return;
    this.loadingAgents.set(true);
    this.api
      .get<{ content: AgentUser[] }>("/api/v1/admin/users", {
        page: 0,
        size: 100,
      })
      .subscribe({
        next: (r) => {
          this.agents.set(r?.content ?? []);
          this.loadingAgents.set(false);
        },
        error: () => this.loadingAgents.set(false),
      });
  }

  // ── Réassignation ─────────────────────────────────────────────────────────

  openReassignModal(dossier: Dossier) {
    this.selectedDossier.set(dossier);
    this.reassignForm.reset();
    this.showReassignModal.set(true);
    this.loadAgents();
  }

  closeReassignModal() {
    this.showReassignModal.set(false);
    this.selectedDossier.set(null);
  }

  submitReassign() {
    if (this.reassignForm.invalid) return;
    const dossier = this.selectedDossier();
    if (!dossier) return;

    this.submitting.set(true);
    const body = {
      nouvelAgentUid: this.reassignForm.value.nouvelAgentUid,
      motif: this.reassignForm.value.motif || null,
    };

    this.api
      .post<{
        data: Delegation;
      }>(`/api/v1/delegations/reassigner-dossier/${dossier.uid}`, body)
      .subscribe({
        next: () => {
          this.toast.showI18nSuccess(
            "del.toast_reassign_title",
            "del.toast_reassign_body",
            { client: dossier.clientNom },
          );
          this.submitting.set(false);
          this.closeReassignModal();
          this.loadDelegations();
          this.loadDossiers();
        },
        error: (err: unknown) => {
          this.toast.showApiError(err, "del.toast_reassign_error");
          this.submitting.set(false);
        },
      });
  }

  // ── Délégation d'autorité ─────────────────────────────────────────────────

  openAutoriteModal() {
    this.autoriteForm.reset({ roleDelegue: "AGENT" });
    this.showAutoriteModal.set(true);
    this.loadAllUsers();
  }

  closeAutoriteModal() {
    this.showAutoriteModal.set(false);
  }

  submitAutorite() {
    if (this.autoriteForm.invalid) return;
    this.submitting.set(true);

    const v = this.autoriteForm.value;
    const body = {
      delegataireUid: v.delegataireUid,
      roleDelegue: v.roleDelegue,
      montantSeuil: v.montantSeuil ?? null,
      dateFin: v.dateFin || null,
      motif: v.motif,
    };

    this.api
      .post<{ data: Delegation }>("/api/v1/delegations/deleguer-autorite", body)
      .subscribe({
        next: () => {
          this.toast.showI18nSuccess(
            "del.toast_create_title",
            "del.toast_create_body",
          );
          this.submitting.set(false);
          this.closeAutoriteModal();
          this.loadDelegations();
        },
        error: (err: unknown) => {
          this.toast.showApiError(err, "del.toast_create_error");
          this.submitting.set(false);
        },
      });
  }

  // ── Révocation ────────────────────────────────────────────────────────────

  revoquer(delegation: Delegation) {
    if (
      !confirm(
        this.i18n.instant("del.confirm_revoke", {
          type: this.typeLabel(delegation.typeDelegation),
          motif: delegation.motif ?? "—",
        }),
      )
    )
      return;

    this.api
      .delete<{ data: null }>(`/api/v1/delegations/${delegation.uid}/revoquer`)
      .subscribe({
        next: () => {
          this.toast.showI18nSuccess(
            "del.toast_revoke_title",
            "del.toast_revoke_body",
          );
          this.loadDelegations();
        },
        error: (err: unknown) => {
          this.toast.showApiError(err, "del.toast_revoke_error");
        },
      });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  formatMontant(m: number | null): string {
    if (m == null) return "—";
    return new Intl.NumberFormat("fr-CM", {
      style: "currency",
      currency: "XAF",
      maximumFractionDigits: 0,
    }).format(m);
  }

  formatDate(d: string | null): string {
    if (!d) return "—";
    return new Date(d).toLocaleDateString("fr-FR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  }
}

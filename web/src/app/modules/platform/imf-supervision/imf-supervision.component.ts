import { Component, OnInit } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import {
  PlatformService,
  ImfRecord,
  ImfSummary,
  AgenceSupervision,
  AuditEntry,
} from "../platform.service";
import { UserResponse } from "@core/models/user.model";
import { fadeInUp, reveal } from "../../../shared/animations";

type Tab = "resume" | "users" | "agences" | "audit";

@Component({
  selector: "imf-supervision",
  templateUrl: "./imf-supervision.component.html",
  styleUrls: ["./imf-supervision.component.scss"],
  animations: [fadeInUp, reveal],
})
export class ImfSupervisionComponent implements OnInit {
  imfId = 0;
  imf: ImfRecord | null = null;
  summary: ImfSummary | null = null;
  loadingHeader = true;
  errorHeader = false;

  activeTab: Tab = "resume";

  // ── Utilisateurs ──────────────────────────────────────────────────────────
  users: UserResponse[] = [];
  usersTotalElements = 0;
  usersPage = 0;
  usersSize = 20;
  usersLoading = false;

  // ── Agences ───────────────────────────────────────────────────────────────
  agences: AgenceSupervision[] = [];
  agencesLoading = false;

  // ── Audit ─────────────────────────────────────────────────────────────────
  auditEntries: AuditEntry[] = [];
  auditTotalElements = 0;
  auditPage = 0;
  auditSize = 50;
  auditLoading = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private platformService: PlatformService,
  ) {}

  ngOnInit(): void {
    this.imfId = Number(this.route.snapshot.paramMap.get("id"));
    this.loadHeader();
  }

  private loadHeader(): void {
    this.loadingHeader = true;
    this.platformService.listImfs().subscribe({
      next: (imfs) => {
        this.imf = imfs.find((i) => i.id === this.imfId) ?? null;
        this.loadingHeader = false;
        this.loadSummary();
      },
      error: () => {
        this.errorHeader = true;
        this.loadingHeader = false;
      },
    });
  }

  private loadSummary(): void {
    this.platformService.getImfSummary(this.imfId).subscribe({
      next: (s) => (this.summary = s),
      error: () => {},
    });
  }

  selectTab(tab: Tab): void {
    this.activeTab = tab;
    if (tab === "users" && !this.users.length) this.loadUsers();
    if (tab === "agences" && !this.agences.length) this.loadAgences();
    if (tab === "audit" && !this.auditEntries.length) this.loadAudit();
  }

  // ── Users ─────────────────────────────────────────────────────────────────

  loadUsers(page = 0): void {
    this.usersLoading = true;
    this.usersPage = page;
    this.platformService
      .getImfUsers(this.imfId, page, this.usersSize)
      .subscribe({
        next: (p) => {
          this.users = p.content;
          this.usersTotalElements = p.totalElements;
          this.usersLoading = false;
        },
        error: () => {
          this.usersLoading = false;
        },
      });
  }

  usersNextPage(): void {
    if ((this.usersPage + 1) * this.usersSize < this.usersTotalElements)
      this.loadUsers(this.usersPage + 1);
  }
  usersPrevPage(): void {
    if (this.usersPage > 0) this.loadUsers(this.usersPage - 1);
  }

  // ── Agences ───────────────────────────────────────────────────────────────

  loadAgences(): void {
    this.agencesLoading = true;
    this.platformService.getImfAgences(this.imfId).subscribe({
      next: (a) => {
        this.agences = a;
        this.agencesLoading = false;
      },
      error: () => {
        this.agencesLoading = false;
      },
    });
  }

  // ── Audit ─────────────────────────────────────────────────────────────────

  loadAudit(page = 0): void {
    this.auditLoading = true;
    this.auditPage = page;
    this.platformService
      .getImfAudit(this.imfId, page, this.auditSize)
      .subscribe({
        next: (p) => {
          this.auditEntries = p.content;
          this.auditTotalElements = p.totalElements;
          this.auditLoading = false;
        },
        error: () => {
          this.auditLoading = false;
        },
      });
  }

  auditNextPage(): void {
    if ((this.auditPage + 1) * this.auditSize < this.auditTotalElements)
      this.loadAudit(this.auditPage + 1);
  }
  auditPrevPage(): void {
    if (this.auditPage > 0) this.loadAudit(this.auditPage - 1);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  back(): void {
    this.router.navigate(["/platform/imf"]);
  }

  formatDate(iso?: string | null): string {
    if (!iso) return "—";
    return new Date(iso).toLocaleDateString("fr-FR", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  roleLabel(role: string): string {
    const labels: Record<string, string> = {
      DSI: "DSI",
      DIRECTEUR: "Directeur",
      RESPONSABLE_RECOUVREMENT: "Resp. Recouvrement",
      ANALYSTE: "Analyste",
      AGENT: "Agent terrain",
    };
    return labels[role] ?? role;
  }

  statutClass(statut: string): string {
    if (statut === "SUCCES" || statut === "SUCCESS") return "ok";
    if (statut === "ECHEC" || statut === "FAILURE") return "ko";
    return "neutral";
  }

  get usersTotalPages(): number {
    return Math.ceil(this.usersTotalElements / this.usersSize);
  }
  get auditTotalPages(): number {
    return Math.ceil(this.auditTotalElements / this.auditSize);
  }
}

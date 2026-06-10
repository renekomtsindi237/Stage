import { Component, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import {
  PlatformService,
  ImfRecord,
  ImfSummary,
  AgenceSupervision,
  AuditEntry,
  UpdateUserPayload,
} from "../platform.service";
import { UserResponse } from "@core/models/user.model";
import { fadeInUp, reveal } from "../../../shared/animations";

type Tab         = "resume" | "users" | "agences" | "audit";
type ModalAction = "edit" | "delete" | "suspend" | "delegate" | null;

const IMF_ROLES = [
  { value: "DSI",                      label: "DSI" },
  { value: "DIRECTEUR",                label: "Directeur" },
  { value: "RESPONSABLE_RECOUVREMENT", label: "Resp. Recouvrement" },
  { value: "ANALYSTE",                 label: "Analyste" },
  { value: "AGENT",                    label: "Agent terrain" },
];

@Component({
  selector: "imf-supervision",
  templateUrl: "./imf-supervision.component.html",
  styleUrls: ["./imf-supervision.component.scss"],
  animations: [fadeInUp, reveal],
})
export class ImfSupervisionComponent implements OnInit {
  imfUid = "";
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

  // ── Modals actions utilisateurs ───────────────────────────────────────────
  activeModal: ModalAction = null;
  selectedUser: UserResponse | null = null;
  modalLoading = false;
  modalError = "";
  modalSuccess = "";

  editForm!: FormGroup;
  delegateTargetUid = "";
  readonly imfRoles = IMF_ROLES;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private platformService: PlatformService,
  ) {}

  ngOnInit(): void {
    this.imfUid = this.route.snapshot.paramMap.get("id") ?? "";
    this.editForm = this.fb.group({
      username: ["", [Validators.required, Validators.minLength(3)]],
      email:    ["", [Validators.required, Validators.email]],
      role:     ["", Validators.required],
      zoneId:   [""],
    });
    this.loadHeader();
  }

  private loadHeader(): void {
    this.loadingHeader = true;
    this.platformService.listImfs().subscribe({
      next: (imfs) => {
        this.imf = imfs.find((i) => i.uid === this.imfUid) ?? null;
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
    this.platformService.getImfSummary(this.imfUid).subscribe({
      next: (s) => (this.summary = s),
      error: () => {},
    });
  }

  selectTab(tab: Tab): void {
    this.activeTab = tab;
    if (tab === "users"   && !this.users.length)        this.loadUsers();
    if (tab === "agences" && !this.agences.length)      this.loadAgences();
    if (tab === "audit"   && !this.auditEntries.length) this.loadAudit();
  }

  // ── Users ─────────────────────────────────────────────────────────────────

  loadUsers(page = 0): void {
    this.usersLoading = true;
    this.usersPage = page;
    this.platformService.getImfUsers(this.imfUid, page, this.usersSize).subscribe({
      next: (p) => {
        this.users = p.content;
        this.usersTotalElements = p.totalElements;
        this.usersLoading = false;
      },
      error: () => { this.usersLoading = false; },
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
    this.platformService.getImfAgences(this.imfUid).subscribe({
      next: (a) => { this.agences = a; this.agencesLoading = false; },
      error: () => { this.agencesLoading = false; },
    });
  }

  // ── Audit ─────────────────────────────────────────────────────────────────

  loadAudit(page = 0): void {
    this.auditLoading = true;
    this.auditPage = page;
    this.platformService.getImfAudit(this.imfUid, page, this.auditSize).subscribe({
      next: (p) => {
        this.auditEntries = p.content;
        this.auditTotalElements = p.totalElements;
        this.auditLoading = false;
      },
      error: () => { this.auditLoading = false; },
    });
  }

  auditNextPage(): void {
    if ((this.auditPage + 1) * this.auditSize < this.auditTotalElements)
      this.loadAudit(this.auditPage + 1);
  }
  auditPrevPage(): void {
    if (this.auditPage > 0) this.loadAudit(this.auditPage - 1);
  }

  // ── Modals ────────────────────────────────────────────────────────────────

  openEditModal(user: UserResponse): void {
    this.selectedUser = user;
    this.editForm.setValue({
      username: user.username,
      email:    user.email ?? "",
      role:     user.role,
      zoneId:   user.zoneId ?? "",
    });
    this.modalError = "";
    this.modalSuccess = "";
    this.activeModal = "edit";
  }

  openDeleteModal(user: UserResponse): void {
    this.selectedUser = user;
    this.modalError = "";
    this.modalSuccess = "";
    this.activeModal = "delete";
  }

  openSuspendModal(user: UserResponse): void {
    this.selectedUser = user;
    this.modalError = "";
    this.modalSuccess = "";
    this.activeModal = "suspend";
  }

  openDelegateModal(user: UserResponse): void {
    this.selectedUser = user;
    this.delegateTargetUid = "";
    this.modalError = "";
    this.modalSuccess = "";
    this.activeModal = "delegate";
  }

  closeModal(): void {
    this.activeModal = null;
    this.selectedUser = null;
    this.modalLoading = false;
    this.modalError = "";
    this.modalSuccess = "";
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  submitEdit(): void {
    if (this.editForm.invalid || !this.selectedUser?.uid || this.modalLoading) return;
    this.modalLoading = true;
    this.modalError = "";
    const payload: UpdateUserPayload = this.editForm.value;
    this.platformService.updateImfUser(this.imfUid, this.selectedUser.uid, payload).subscribe({
      next: (updated) => {
        this.users = this.users.map((u) => u.uid === updated.uid ? updated : u);
        this.modalLoading = false;
        this.modalSuccess = `Utilisateur « ${updated.username} » mis à jour.`;
        setTimeout(() => this.closeModal(), 1400);
      },
      error: (err) => {
        this.modalLoading = false;
        this.modalError = err?.error?.message ?? "Une erreur est survenue.";
      },
    });
  }

  submitDelete(): void {
    if (!this.selectedUser?.uid || this.modalLoading) return;
    this.modalLoading = true;
    this.modalError = "";
    this.platformService.deleteImfUser(this.imfUid, this.selectedUser.uid).subscribe({
      next: () => {
        this.users = this.users.filter((u) => u.uid !== this.selectedUser!.uid);
        this.usersTotalElements--;
        this.modalLoading = false;
        this.modalSuccess = `Utilisateur « ${this.selectedUser!.username} » supprimé.`;
        setTimeout(() => this.closeModal(), 1400);
      },
      error: (err) => {
        this.modalLoading = false;
        this.modalError = err?.error?.message ?? "Une erreur est survenue.";
      },
    });
  }

  submitSuspend(): void {
    if (!this.selectedUser?.uid || this.modalLoading) return;
    this.modalLoading = true;
    this.modalError = "";
    const isSuspended = !this.selectedUser.actif;
    const obs = isSuspended
      ? this.platformService.reactivateImfUser(this.imfUid, this.selectedUser.uid)
      : this.platformService.suspendImfUser(this.imfUid, this.selectedUser.uid);
    obs.subscribe({
      next: (updated) => {
        this.users = this.users.map((u) => u.uid === updated.uid ? updated : u);
        this.modalLoading = false;
        this.modalSuccess = `Utilisateur « ${updated.username} » ${updated.actif ? "réactivé" : "suspendu"}.`;
        setTimeout(() => this.closeModal(), 1400);
      },
      error: (err) => {
        this.modalLoading = false;
        this.modalError = err?.error?.message ?? "Une erreur est survenue.";
      },
    });
  }

  submitDelegate(): void {
    if (!this.selectedUser?.uid || !this.delegateTargetUid || this.modalLoading) return;
    this.modalLoading = true;
    this.modalError = "";
    this.platformService.delegateImfUser(this.imfUid, this.selectedUser.uid, { toUserUid: this.delegateTargetUid }).subscribe({
      next: (updated) => {
        this.users = this.users.map((u) => u.uid === updated.uid ? updated : u);
        this.modalLoading = false;
        this.modalSuccess = `Relégation effectuée — ${updated.username} suspendu.`;
        // Recharger pour que le destinataire ait son nouveau rôle
        setTimeout(() => { this.closeModal(); this.loadUsers(this.usersPage); }, 1400);
      },
      error: (err) => {
        this.modalLoading = false;
        this.modalError = err?.error?.message ?? "Une erreur est survenue.";
      },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  get delegateCandidates(): UserResponse[] {
    return this.users.filter(
      (u) => u.uid !== this.selectedUser?.uid && u.actif,
    );
  }

  back(): void {
    this.router.navigate(["/platform/imf"]);
  }

  formatDate(iso?: string | null): string {
    if (!iso) return "—";
    return new Date(iso).toLocaleDateString("fr-FR", {
      day: "2-digit", month: "short", year: "numeric",
      hour: "2-digit", minute: "2-digit",
    });
  }

  roleLabel(role: string): string {
    const labels: Record<string, string> = {
      DSI:                      "DSI",
      DIRECTEUR:                "Directeur",
      RESPONSABLE_RECOUVREMENT: "Resp. Recouvrement",
      ANALYSTE:                 "Analyste",
      AGENT:                    "Agent terrain",
    };
    return labels[role] ?? role;
  }

  statutClass(statut: string): string {
    if (statut === "SUCCES" || statut === "SUCCESS") return "ok";
    if (statut === "ECHEC"  || statut === "FAILURE") return "ko";
    return "neutral";
  }

  get usersTotalPages(): number {
    return Math.ceil(this.usersTotalElements / this.usersSize);
  }
  get auditTotalPages(): number {
    return Math.ceil(this.auditTotalElements / this.auditSize);
  }
}

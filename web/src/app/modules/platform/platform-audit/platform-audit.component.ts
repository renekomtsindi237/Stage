import { Component, OnInit } from "@angular/core";
import { FormBuilder, FormGroup } from "@angular/forms";
import { debounceTime } from "rxjs/operators";
import { PlatformService, AuditEntry, PageResponse } from "../platform.service";

@Component({
  selector: "imf-platform-audit",
  templateUrl: "./platform-audit.component.html",
  styleUrls: ["./platform-audit.component.scss"],
})
export class PlatformAuditComponent implements OnInit {
  allEntries: AuditEntry[] = [];
  filtered: AuditEntry[] = [];
  totalElements = 0;
  totalPages = 0;
  page = 0;
  readonly size = 50;
  loading = false;
  error = false;

  filterForm!: FormGroup;

  readonly displayedColumns = [
    "createdAt",
    "username",
    "action",
    "entite",
    "resume",
    "ipClient",
  ];

  readonly ACTIONS = [
    "CONNEXION",
    "DECONNEXION",
    "CREATION",
    "MODIFICATION",
    "SUPPRESSION",
    "CHANGEMENT_STATUT",
    "CONSULTATION",
  ];

  constructor(
    private service: PlatformService,
    private fb: FormBuilder,
  ) {}

  ngOnInit(): void {
    this.filterForm = this.fb.group({
      search: [""],
      action: [""],
      statut: [""],
    });
    this.filterForm.valueChanges
      .pipe(debounceTime(300))
      .subscribe(() => this.applyFilter());
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = false;
    this.service.getGlobalAudit(this.page, this.size).subscribe({
      next: (r: PageResponse<AuditEntry>) => {
        this.allEntries = r.content;
        this.totalElements = r.totalElements;
        this.totalPages = r.totalPages;
        this.loading = false;
        this.applyFilter();
      },
      error: () => {
        this.loading = false;
        this.error = true;
      },
    });
  }

  applyFilter(): void {
    const { search, action, statut } = this.filterForm.value as {
      search: string;
      action: string;
      statut: string;
    };
    const s = (search || "").toLowerCase();
    this.filtered = this.allEntries.filter((e) => {
      const matchSearch =
        !s ||
        e.username?.toLowerCase().includes(s) ||
        e.entite?.toLowerCase().includes(s) ||
        (e.entiteId ?? "").toLowerCase().includes(s) ||
        (e.details ?? "").toLowerCase().includes(s);
      const matchAction = !action || e.action === action;
      const matchStatut = !statut || e.statut === statut;
      return matchSearch && matchAction && matchStatut;
    });
  }

  prevPage(): void {
    if (this.page > 0) {
      this.page--;
      this.load();
    }
  }

  nextPage(): void {
    if (this.page < this.totalPages - 1) {
      this.page++;
      this.load();
    }
  }

  reset(): void {
    this.filterForm.reset({ search: "", action: "", statut: "" });
    this.page = 0;
    this.load();
  }

  getStatutClass(s: string): string {
    return s === "SUCCESS" || s === "SUCCES" ? "badge-ok" : "badge-alert";
  }

  getActionClass(action: string): string {
    const a = (action || "").toUpperCase();
    if (a.includes("CREAT") || a.includes("AJOUT")) return "badge-create";
    if (a.includes("MODIF") || a.includes("UPDATE") || a.includes("CHANGEMENT"))
      return "badge-update";
    if (a.includes("SUPPRES") || a.includes("DELETE")) return "badge-delete";
    if (a.includes("CONNEXION")) return "badge-login";
    if (a.includes("DECONNEXION")) return "badge-logout";
    return "badge-default";
  }

  maskIp(ip: string | null): string {
    if (!ip) return "—";
    if (ip === "localhost" || ip === "::1" || ip.startsWith("0:0:0:0"))
      return "localhost";
    const parts = ip.split(".");
    if (parts.length === 4) return `${parts[0]}.${parts[1]}.${parts[2]}.***`;
    return ip;
  }

  truncateDetails(details: string | null): string {
    if (!details) return "—";
    return details.length > 80 ? details.slice(0, 77) + "…" : details;
  }

  formatDate(iso: string): string {
    if (!iso) return "—";
    return new Date(iso).toLocaleString("fr-FR", {
      day: "2-digit",
      month: "long",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  get pageInfo(): string {
    if (!this.totalElements) return "Aucune entrée";
    const from = this.page * this.size + 1;
    const to = Math.min((this.page + 1) * this.size, this.totalElements);
    return `${from}–${to} sur ${this.totalElements.toLocaleString("fr-FR")}`;
  }
}

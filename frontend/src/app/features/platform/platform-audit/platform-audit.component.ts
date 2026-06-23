import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder } from "@angular/forms";
import { ApiService } from "../../../core/http/api.service";
import { AuditEntry, PagedResult } from "../../../core/models/platform.model";

@Component({
  selector: "app-platform-audit",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./platform-audit.component.html",
  styleUrls: ["./platform-audit.component.scss"],
})
export class PlatformAuditComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  loading = signal(true);
  entries = signal<AuditEntry[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  selected = signal<AuditEntry | null>(null);

  readonly pageSize = 25;

  filterForm = this.fb.group({
    entiteType: [""],
    action: [""],
    username: [""],
    debut: [""],
    fin: [""],
  });

  readonly entiteTypes = [
    "DOSSIER",
    "CLIENT",
    "COLLECTE",
    "ALERTE",
    "UTILISATEUR",
    "CREANCE",
    "ECHEANCE",
    "AUTH",
    "EXPORT",
    "CONSENTEMENT",
    "VIOLATION_DONNEES",
  ];

  readonly actions = [
    "CREATION",
    "MODIFICATION",
    "SUPPRESSION",
    "CONSULTATION",
    "EXPORT",
    "CONNEXION",
    "DECONNEXION",
    "CHANGEMENT_STATUT",
    "ACCES_REFUSE",
    "MASQUAGE_DONNEES",
    "DEMANDE_RGPD",
    "CONSENTEMENT",
  ];

  ngOnInit() {
    this.load();
  }

  load(page = 0) {
    this.loading.set(true);
    this.selected.set(null);
    const f = this.filterForm.value;

    const params: Record<string, string | number | null | undefined> = {
      page,
      size: this.pageSize,
      entiteType: f.entiteType || undefined,
      action: f.action || undefined,
      username: f.username || undefined,
      debut: f.debut ? new Date(f.debut).toISOString() : undefined,
      fin: f.fin ? new Date(f.fin).toISOString() : undefined,
    };

    this.api
      .get<PagedResult<AuditEntry>>("/api/v1/admin/audit/trail", params)
      .subscribe({
        next: (page_) => {
          this.entries.set(page_.content ?? []);
          this.totalElements.set(page_.totalElements ?? 0);
          this.totalPages.set(page_.totalPages ?? 0);
          this.currentPage.set(page_.number ?? 0);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  search() {
    this.load(0);
  }

  reset() {
    this.filterForm.reset();
    this.load(0);
  }

  goPage(p: number) {
    if (p < 0 || p >= this.totalPages()) return;
    this.load(p);
  }

  select(entry: AuditEntry) {
    this.selected.set(this.selected()?.id === entry.id ? null : entry);
  }

  actionBadgeClass(action: string): string {
    switch (action) {
      case "CREATION":
        return "badge-success";
      case "SUPPRESSION":
      case "ACCES_REFUSE":
        return "badge-danger";
      case "MODIFICATION":
      case "CHANGEMENT_STATUT":
        return "badge-warning";
      case "CONNEXION":
      case "DECONNEXION":
        return "badge-info";
      default:
        return "badge-muted";
    }
  }

  jsonPreview(val?: Record<string, unknown> | null): string {
    if (!val) return "—";
    try {
      return JSON.stringify(val, null, 2);
    } catch {
      return String(val);
    }
  }

  pages(): number[] {
    const total = this.totalPages();
    const cur = this.currentPage();
    const range: number[] = [];
    const start = Math.max(0, cur - 2);
    const end = Math.min(total - 1, cur + 2);
    for (let i = start; i <= end; i++) range.push(i);
    return range;
  }
}

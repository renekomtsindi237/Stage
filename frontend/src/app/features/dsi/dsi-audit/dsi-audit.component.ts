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

interface AuditEntry {
  id: string;
  action: string;
  utilisateur: string;
  entite: string;
  entiteId: string;
  ancienneValeur?: string;
  nouvelleValeur?: string;
  createdAt: string;
}

interface AuditPage {
  content: AuditEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-dsi-audit",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./dsi-audit.component.html",
  styleUrls: ["./dsi-audit.component.scss"],
})
export class DsiAuditComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  loading = signal(true);
  page = signal<AuditPage | null>(null);
  currentPage = signal(0);

  filterForm = this.fb.group({ action: [""], utilisateur: [""] });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const { action, utilisateur } = this.filterForm.value;
    this.api
      .get<AuditPage>("/api/v1/admin/audit/trail", {
        page: this.currentPage(),
        size: 20,
        action,
        utilisateur,
      })
      .subscribe({
        next: (p: AuditPage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  exportCsv() {
    window.open("/api/v1/admin/audit/trail/export?format=csv", "_blank");
  }
}

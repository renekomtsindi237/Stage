import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { TranslatePipe } from "@ngx-translate/core";
import { AuthService } from "../../../core/auth/auth.service";
import { environment } from "../../../../environments/environment";

interface Rapport {
  id: string;
  titre: string;
  type: "MENSUEL" | "TRIMESTRIEL" | "COBAC" | "PERSONNALISE";
  dateDernier?: string;
  endpoint: string;
  filename: string;
}

@Component({
  selector: "app-dir-rapports",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./dir-rapports.component.html",
  styleUrls: ["./dir-rapports.component.scss"],
})
export class DirRapportsComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  generating = signal<string | null>(null);

  rapports: Rapport[] = [
    {
      id: "r1",
      titre: "dir_rapports.r1_titre",
      type: "MENSUEL",
      dateDernier: "2026-05-31",
      endpoint: this.buildKpiUrl(this.firstOfMonth(-1), this.lastOfMonth(-1)),
      filename: "rapport_mensuel.pdf",
    },
    {
      id: "r2",
      titre: "dir_rapports.r2_titre",
      type: "TRIMESTRIEL",
      dateDernier: "2026-03-31",
      endpoint: this.buildKpiUrl(
        this.firstOfQuarter(-1),
        this.lastOfQuarter(-1),
      ),
      filename: "rapport_trimestriel.pdf",
    },
    {
      id: "r3",
      titre: "dir_rapports.r3_titre",
      type: "COBAC",
      dateDernier: "2026-05-31",
      endpoint: this.buildCobacUrl(),
      filename: "rapport_cobac.pdf",
    },
    {
      id: "r4",
      titre: "dir_rapports.r4_titre",
      type: "COBAC",
      endpoint: `${environment.apiUrl}/api/v1/reporting/prets-retard/pdf`,
      filename: "prets_en_retard.pdf",
    },
    {
      id: "r5",
      titre: "dir_rapports.r5_titre",
      type: "PERSONNALISE",
      endpoint: this.buildCollectesUrl(30),
      filename: "rapport_collectes.pdf",
    },
  ];

  generate(r: Rapport) {
    if (this.generating() === r.id) return;
    this.generating.set(r.id);
    this.cdr.markForCheck();

    const token = this.auth.getToken();
    const headers: Record<string, string> = token
      ? { Authorization: `Bearer ${token}` }
      : {};

    this.http.get(r.endpoint, { headers, responseType: "blob" }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = r.filename;
        a.click();
        URL.revokeObjectURL(url);
        this.generating.set(null);
        this.cdr.markForCheck();
      },
      error: () => {
        this.generating.set(null);
        this.cdr.markForCheck();
      },
    });
  }

  typeClass(t: string) {
    const m: Record<string, string> = {
      MENSUEL: "badge-basse",
      TRIMESTRIEL: "badge-moyenne",
      COBAC: "badge-haute",
      PERSONNALISE: "badge-muted",
    };
    return m[t] ?? "";
  }

  private buildKpiUrl(debut: string, fin: string): string {
    return `${environment.apiUrl}/api/v1/reporting/kpi/pdf?dateDebut=${debut}&dateFin=${fin}`;
  }

  private buildCobacUrl(): string {
    const today = new Date();
    const fin = today.toISOString().slice(0, 10);
    const debut = `${today.getFullYear()}-01-01`;
    return `${environment.apiUrl}/api/v1/reporting/cobac/pdf?dateDebut=${debut}&dateFin=${fin}`;
  }

  private buildCollectesUrl(daysBack: number): string {
    const today = new Date();
    const fin = today.toISOString().slice(0, 10);
    const past = new Date(today.getTime() - daysBack * 86400000);
    const debut = past.toISOString().slice(0, 10);
    return `${environment.apiUrl}/api/v1/reporting/collectes/pdf?dateDebut=${debut}&dateFin=${fin}`;
  }

  private firstOfMonth(offset: number): string {
    const d = new Date();
    d.setMonth(d.getMonth() + offset, 1);
    return d.toISOString().slice(0, 10);
  }

  private lastOfMonth(offset: number): string {
    const d = new Date();
    d.setMonth(d.getMonth() + offset + 1, 0);
    return d.toISOString().slice(0, 10);
  }

  private firstOfQuarter(offset: number): string {
    const d = new Date();
    const quarter = Math.floor(d.getMonth() / 3) + offset;
    const year = d.getFullYear() + Math.floor(quarter / 4);
    const q = ((quarter % 4) + 4) % 4;
    return `${year}-${String(q * 3 + 1).padStart(2, "0")}-01`;
  }

  private lastOfQuarter(offset: number): string {
    const first = new Date(this.firstOfQuarter(offset));
    const last = new Date(first.getFullYear(), first.getMonth() + 3, 0);
    return last.toISOString().slice(0, 10);
  }
}

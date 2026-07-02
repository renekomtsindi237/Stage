import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { ApiService } from "../../../core/http/api.service";
import { StatCardComponent } from "../../../shared/components/stat-card/stat-card.component";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { TranslatePipe } from "@ngx-translate/core";

interface DossierItem {
  uid: string;
  reference: string;
  clientNom: string;
  montant: number;
  statut: string;
  createdAt: string;
}

interface AcDashboard {
  total: number;
  enInstruction: number;
  approuves: number;
  rejetes: number;
  recents: DossierItem[];
}

@Component({
  selector: "app-ac-dashboard",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent, FcfaPipe, TranslatePipe],
  templateUrl: "./ac-dashboard.component.html",
  styleUrls: ["./ac-dashboard.component.scss"],
})
export class AcDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  loading = signal(true);
  data = signal<AcDashboard | null>(null);

  ngOnInit() {
    this.api.get<AcDashboard>("/api/v1/credit/dashboard").subscribe({
      next: (d: AcDashboard) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  statutClass(s: string): string {
    const m: Record<string, string> = {
      EN_INSTRUCTION: "badge-moyenne",
      VALIDE_CHEF: "badge-basse",
      APPROUVE: "badge-basse",
      REJETE: "badge-critique",
      SOUMIS: "badge-haute",
    };
    return m[s] ?? "";
  }
}

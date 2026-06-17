import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ApiService } from "../../../core/http/api.service";

interface CheckItem {
  libelle: string;
  conforme: boolean;
  echeance?: string;
}

interface ConformiteSection {
  titre: string;
  score: number;
  items: CheckItem[];
}

interface ConformiteData {
  scoreGlobal: number;
  dateRapport: string;
  cobac: ConformiteSection;
  beac: ConformiteSection;
  lcbft: ConformiteSection;
  engagements: {
    titre: string;
    montant: number;
    risque: string;
    dateEcheance: string;
  }[];
}

@Component({
  selector: "app-eng-conformite",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./eng-conformite.component.html",
  styleUrls: ["./eng-conformite.component.scss"],
})
export class EngConformiteComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = signal(true);
  data = signal<ConformiteData | null>(null);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api.get<ConformiteData>("/api/v1/engagements/conformite").subscribe({
      next: (d: ConformiteData) => {
        this.data.set(d);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading.set(false);
        this.cdr.markForCheck();
      },
    });
  }

  scoreClass(score: number) {
    if (score >= 80) return "score--success";
    if (score >= 60) return "score--warning";
    return "score--danger";
  }

  risqueClass(r: string) {
    return (
      {
        FAIBLE: "badge-success",
        MOYEN: "badge-warning",
        ELEVE: "badge-danger",
        CRITIQUE: "badge-danger",
      }[r] ?? ""
    );
  }

  sections(d: ConformiteData) {
    return [d.cobac, d.beac, d.lcbft];
  }
}

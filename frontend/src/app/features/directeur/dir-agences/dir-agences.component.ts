import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ApiService } from "../../../core/http/api.service";

interface Agence {
  id: string;
  code: string;
  nom: string;
  ville: string;
  agentsCount: number;
  clientsCount: number;
  encoursFcfa: number;
  par30: number;
}

@Component({
  selector: "app-dir-agences",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: "./dir-agences.component.html",
  styleUrls: ["./dir-agences.component.scss"],
})
export class DirAgencesComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  agences = signal<Agence[]>([]);

  ngOnInit() {
    this.api.get<Agence[]>("/api/v1/agences").subscribe({
      next: (a: Agence[]) => {
        this.agences.set(a);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}

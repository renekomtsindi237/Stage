import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
  ViewChild,
  ElementRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { TranslatePipe } from "@ngx-translate/core";
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

interface ImportResult {
  totalLignes: number;
  importe: number;
  miseAJour: number;
  erreurs: number;
  lignesErreur: string[];
}

@Component({
  selector: "app-dir-agences",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./dir-agences.component.html",
  styleUrls: ["./dir-agences.component.scss"],
})
export class DirAgencesComponent implements OnInit {
  private readonly api = inject(ApiService);

  @ViewChild("fileInput") fileInputRef!: ElementRef<HTMLInputElement>;

  loading = signal(true);
  agences = signal<Agence[]>([]);
  importing = signal(false);
  importResult = signal<ImportResult | null>(null);

  ngOnInit() {
    this.load();
  }

  load() {
    this.api.get<Agence[]>("/api/v1/agences").subscribe({
      next: (a: Agence[]) => {
        this.agences.set(a);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  templateUrl(): string {
    return this.api.downloadUrl("/api/v1/import/template/agences");
  }

  triggerImport() {
    this.fileInputRef.nativeElement.value = "";
    this.fileInputRef.nativeElement.click();
  }

  onFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.importing.set(true);
    this.importResult.set(null);
    const fd = new FormData();
    fd.append("fichier", file);
    this.api.postFile<ImportResult>("/api/v1/import/agences", fd).subscribe({
      next: (r) => {
        this.importResult.set(r);
        this.importing.set(false);
        this.load();
      },
      error: () => this.importing.set(false),
    });
  }
}

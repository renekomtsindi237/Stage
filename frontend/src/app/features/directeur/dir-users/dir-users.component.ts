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
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";

interface UserRow {
  uid: string;
  username: string;
  email: string;
  role: string;
  actif: boolean;
}
interface UserPage {
  content: UserRow[];
  totalElements: number;
  totalPages: number;
  number: number;
}
interface ImportResult {
  totalLignes: number;
  importe: number;
  miseAJour: number;
  erreurs: number;
  lignesErreur: string[];
}

@Component({
  selector: "app-dir-users",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: "./dir-users.component.html",
  styleUrls: ["./dir-users.component.scss"],
})
export class DirUsersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  @ViewChild("fileAgents") fileAgentsRef!: ElementRef<HTMLInputElement>;
  @ViewChild("fileUsers") fileUsersRef!: ElementRef<HTMLInputElement>;

  loading = signal(true);
  page = signal<UserPage | null>(null);
  currentPage = signal(0);
  showModal = signal(false);
  creating = signal(false);

  importingType = signal<"agents" | "utilisateurs" | null>(null);
  importResult = signal<ImportResult | null>(null);

  createForm = this.fb.group({
    prenom: ["", Validators.required],
    nom: ["", Validators.required],
    email: ["", [Validators.required, Validators.email]],
    role: ["AGENT", Validators.required],
  });

  readonly roles = [
    "AGENT",
    "AGENT_CREDIT",
    "ANALYSTE",
    "CAISSIER",
    "CHEF_AGENCE",
    "AGENT_SAISIE",
    "RESPONSABLE_RECOUVREMENT",
  ];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api
      .get<UserPage>("/api/v1/directeur/users", {
        page: this.currentPage(),
        size: 20,
      })
      .subscribe({
        next: (p: UserPage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  create() {
    if (this.createForm.invalid) return;
    this.creating.set(true);
    this.api.post("/api/v1/directeur/users", this.createForm.value).subscribe({
      next: () => {
        this.creating.set(false);
        this.showModal.set(false);
        this.toast.showSuccess(
          "Utilisateur créé",
          `${this.createForm.value.prenom} ${this.createForm.value.nom}`,
        );
        this.createForm.reset({ role: "AGENT" });
        this.load();
      },
      error: () => {
        this.creating.set(false);
        this.toast.showError("Erreur", "Impossible de créer l'utilisateur.");
      },
    });
  }

  toggle(uid: string, actif: boolean) {
    const url = actif
      ? `/api/v1/directeur/users/${uid}/activate`
      : `/api/v1/directeur/users/${uid}`;
    (actif ? this.api.patch(url, {}) : this.api.delete(url)).subscribe({
      next: () => {
        this.toast.showSuccess(
          "Succès",
          `Utilisateur ${actif ? "réactivé" : "désactivé"}.`,
        );
        this.load();
      },
      error: () => this.toast.showError("Erreur", "Action impossible."),
    });
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  templateAgentsUrl(): string {
    return this.api.downloadUrl("/api/v1/import/template/agents");
  }

  templateUsersUrl(): string {
    return this.api.downloadUrl("/api/v1/import/template/utilisateurs");
  }

  triggerImport(type: "agents" | "utilisateurs") {
    this.importResult.set(null);
    if (type === "agents") {
      this.fileAgentsRef.nativeElement.value = "";
      this.fileAgentsRef.nativeElement.click();
    } else {
      this.fileUsersRef.nativeElement.value = "";
      this.fileUsersRef.nativeElement.click();
    }
  }

  onFileSelected(event: Event, type: "agents" | "utilisateurs") {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.importingType.set(type);
    this.importResult.set(null);
    const fd = new FormData();
    fd.append("fichier", file);
    this.api.postFile<ImportResult>(`/api/v1/import/${type}`, fd).subscribe({
      next: (r) => {
        this.importResult.set(r);
        this.importingType.set(null);
        this.load();
      },
      error: () => this.importingType.set(null),
    });
  }
}

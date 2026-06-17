import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";

interface UserRow {
  uid: string;
  prenom: string;
  nom: string;
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

@Component({
  selector: "app-dir-users",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./dir-users.component.html",
  styleUrls: ["./dir-users.component.scss"],
})
export class DirUsersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  page = signal<UserPage | null>(null);
  currentPage = signal(0);
  showModal = signal(false);
  creating = signal(false);

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
}

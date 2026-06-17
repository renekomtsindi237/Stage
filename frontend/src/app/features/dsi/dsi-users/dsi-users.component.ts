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
  agenceNom?: string;
  actif: boolean;
}

interface UserPage {
  content: UserRow[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-dsi-users",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./dsi-users.component.html",
  styleUrls: ["./dsi-users.component.scss"],
})
export class DsiUsersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  page = signal<UserPage | null>(null);
  currentPage = signal(0);
  showModal = signal(false);
  creating = signal(false);
  roleFilter = signal("");

  createForm = this.fb.group({
    prenom: ["", Validators.required],
    nom: ["", Validators.required],
    email: ["", [Validators.required, Validators.email]],
    role: ["AGENT", Validators.required],
    agenceId: [""],
  });

  readonly roles = [
    "AGENT",
    "AGENT_CREDIT",
    "ANALYSTE",
    "ANALYSTE_ENGAGEMENTS",
    "CAISSIER",
    "CHEF_AGENCE",
    "AGENT_SAISIE",
    "RESPONSABLE_RECOUVREMENT",
    "DSI",
    "DIRECTEUR",
  ];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api
      .get<UserPage>("/api/v1/admin/users", {
        page: this.currentPage(),
        size: 20,
        role: this.roleFilter() || undefined,
      })
      .subscribe({
        next: (p: UserPage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  toggle(uid: string, actif: boolean) {
    const url = actif
      ? `/api/v1/admin/users/${uid}/activate`
      : `/api/v1/admin/users/${uid}`;
    const call = actif ? this.api.patch(url, {}) : this.api.delete(url);
    call.subscribe({
      next: () => {
        this.toast.showSuccess(
          "Succès",
          `Utilisateur ${actif ? "réactivé" : "désactivé"}.`,
        );
        this.load();
      },
      error: () =>
        this.toast.showError("Erreur", "Impossible de modifier l'utilisateur."),
    });
  }

  create() {
    if (this.createForm.invalid) return;
    this.creating.set(true);
    this.api.post("/api/v1/admin/users", this.createForm.value).subscribe({
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

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }
}

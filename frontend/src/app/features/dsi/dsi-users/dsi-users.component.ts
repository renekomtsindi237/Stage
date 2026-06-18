import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { map } from "rxjs";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";

interface UserRow {
  uid: string;
  username: string;
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
  showEditModal = signal(false);
  showDeleteConfirm = signal(false);
  creating = signal(false);
  saving = signal(false);
  deleting = signal(false);
  roleFilter = signal("");
  selectedUser = signal<UserRow | null>(null);

  createForm = this.fb.group({
    username: [
      "",
      [Validators.required, Validators.minLength(3), Validators.maxLength(50)],
    ],
    email: ["", [Validators.required, Validators.email]],
    role: ["AGENT", Validators.required],
  });

  editForm = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
    role: ["AGENT", Validators.required],
    zoneId: [""],
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
    "DIRECTEUR",
  ];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.api
      .get<{ data: UserPage }>("/api/v1/admin/users", {
        page: this.currentPage(),
        size: 20,
        role: this.roleFilter() || undefined,
      })
      .pipe(map((r) => r.data))
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

  openEdit(u: UserRow) {
    this.selectedUser.set(u);
    this.editForm.reset({ email: u.email, role: u.role, zoneId: "" });
    this.showEditModal.set(true);
  }

  saveEdit() {
    if (this.editForm.invalid || !this.selectedUser()) return;
    this.saving.set(true);
    this.api
      .put(`/api/v1/admin/users/${this.selectedUser()!.uid}`, this.editForm.value)
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.showEditModal.set(false);
          this.toast.showSuccess("Succès", "Profil mis à jour.");
          this.load();
        },
        error: () => {
          this.saving.set(false);
          this.toast.showError("Erreur", "Impossible de mettre à jour le profil.");
        },
      });
  }

  openDelete(u: UserRow) {
    this.selectedUser.set(u);
    this.showDeleteConfirm.set(true);
  }

  confirmDelete() {
    if (!this.selectedUser()) return;
    this.deleting.set(true);
    this.api
      .delete(`/api/v1/admin/users/${this.selectedUser()!.uid}/delete`)
      .subscribe({
        next: () => {
          this.deleting.set(false);
          this.showDeleteConfirm.set(false);
          this.toast.showSuccess(
            "Supprimé",
            `${this.selectedUser()!.username} a été supprimé.`,
          );
          this.load();
        },
        error: () => {
          this.deleting.set(false);
          this.toast.showError("Erreur", "Impossible de supprimer l'utilisateur.");
        },
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
          `${this.createForm.value.username} — un OTP lui sera envoyé à sa première connexion.`,
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

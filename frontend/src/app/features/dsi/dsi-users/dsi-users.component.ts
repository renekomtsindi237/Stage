import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { StatutLabelPipe } from "../../../shared/pipes/statut-label.pipe";
import { EmptyStateComponent } from "../../../shared/components/empty-state/empty-state.component";
import { EscCloseDirective } from "../../../shared/directives/esc-close.directive";
import { downloadCsv } from "../../../shared/utils/csv-export";

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
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TranslatePipe,
    StatutLabelPipe,
    EmptyStateComponent,
    EscCloseDirective,
  ],
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
        this.toast.showI18nSuccess(
          "common.success",
          actif ? "dsi_users.toast_toggle_on" : "dsi_users.toast_toggle_off",
        );
        this.load();
      },
      error: (err: unknown) =>
        this.toast.showApiError(err, "dsi_users.toast_modify_error"),
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
      .put(
        `/api/v1/admin/users/${this.selectedUser()!.uid}`,
        this.editForm.value,
      )
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.showEditModal.set(false);
          this.toast.showI18nSuccess(
            "common.success",
            "dsi_users.toast_update",
          );
          this.load();
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.toast.showApiError(err, "dsi_users.toast_update_error");
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
          this.toast.showI18nSuccess(
            "dsi_users.toast_delete_title",
            "dsi_users.toast_delete_body",
            {
              username: this.selectedUser()!.username,
            },
          );
          this.load();
        },
        error: (err: unknown) => {
          this.deleting.set(false);
          this.toast.showApiError(err, "dsi_users.toast_delete_error");
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
        this.toast.showI18nSuccess(
          "dsi_users.toast_create_title",
          "dsi_users.toast_create_body",
          {
            username: this.createForm.value.username,
          },
        );
        this.createForm.reset({ role: "AGENT" });
        this.load();
      },
      error: (err: unknown) => {
        this.creating.set(false);
        this.toast.showApiError(err, "dsi_users.toast_create_error");
      },
    });
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  exportCsv() {
    downloadCsv(
      "utilisateurs",
      (this.page()?.content ?? []).map((u) => ({
        username: u.username,
        email: u.email,
        role: u.role,
        agence: u.agenceNom ?? "",
        actif: u.actif,
      })),
    );
  }
}

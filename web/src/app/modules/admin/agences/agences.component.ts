import { Component, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { MatSnackBar } from "@angular/material/snack-bar";
import { AdminService, AgenceResponse } from "../admin.service";

@Component({
  selector: "imf-agences",
  templateUrl: "./agences.component.html",
  styleUrls: ["./agences.component.scss"],
})
export class AgencesComponent implements OnInit {
  agences: AgenceResponse[] = [];
  loadingList = false;
  loadingCreate = false;
  loadingToggle: Record<number, boolean> = {};
  loadingDelete: Record<number, boolean> = {};
  confirmDeleteId: number | null = null;

  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private snackBar: MatSnackBar,
  ) {
    this.form = this.fb.group({
      nom: ["", [Validators.required, Validators.maxLength(100)]],
      ville: ["", Validators.maxLength(100)],
      responsable: ["", Validators.maxLength(100)],
      telephone: ["", Validators.maxLength(20)],
    });
  }

  ngOnInit(): void {
    this.loadAgences();
  }

  loadAgences(): void {
    this.loadingList = true;
    this.adminService.listAgences().subscribe({
      next: (data) => {
        this.agences = data;
        this.loadingList = false;
      },
      error: () => {
        this.snackBar.open("Erreur lors du chargement des agences", "OK", {
          duration: 3000,
        });
        this.loadingList = false;
      },
    });
  }

  get totalActif(): number {
    return this.agences.filter((a) => a.actif).length;
  }

  onSubmit(): void {
    if (this.form.invalid || this.loadingCreate) return;
    this.loadingCreate = true;
    this.adminService.createAgence(this.form.value).subscribe({
      next: (created) => {
        this.agences = [...this.agences, created].sort((a, b) =>
          a.nom.localeCompare(b.nom),
        );
        this.form.reset();
        this.loadingCreate = false;
        this.snackBar.open(`Agence "${created.nom}" créée avec succès`, "OK", {
          duration: 3000,
        });
      },
      error: (err) => {
        this.loadingCreate = false;
        if (err?.status === 409) {
          this.snackBar.open("Une agence avec ce nom existe déjà.", "OK", {
            duration: 4000,
          });
        } else {
          this.snackBar.open("Erreur lors de la création de l'agence.", "OK", {
            duration: 3000,
          });
        }
      },
    });
  }

  toggle(agence: AgenceResponse): void {
    this.loadingToggle[agence.id] = true;
    this.adminService.toggleAgence(agence.id).subscribe({
      next: (updated) => {
        const idx = this.agences.findIndex((a) => a.id === agence.id);
        if (idx !== -1) this.agences[idx] = updated;
        this.loadingToggle[agence.id] = false;
        const msg = updated.actif ? "Agence activée" : "Agence désactivée";
        this.snackBar.open(msg, "OK", { duration: 2000 });
      },
      error: () => {
        this.loadingToggle[agence.id] = false;
        this.snackBar.open("Erreur lors de la modification.", "OK", {
          duration: 3000,
        });
      },
    });
  }

  requestDelete(id: number): void {
    this.confirmDeleteId = id;
  }

  cancelDelete(): void {
    this.confirmDeleteId = null;
  }

  confirmDelete(id: number): void {
    this.loadingDelete[id] = true;
    this.confirmDeleteId = null;
    this.adminService.deleteAgence(id).subscribe({
      next: () => {
        this.agences = this.agences.filter((a) => a.id !== id);
        this.loadingDelete[id] = false;
        this.snackBar.open("Agence supprimée", "OK", { duration: 2000 });
      },
      error: (err) => {
        this.loadingDelete[id] = false;
        if (err?.status === 409) {
          this.snackBar.open(
            err?.error?.message ??
              "Des utilisateurs sont encore affectés à cette agence.",
            "OK",
            { duration: 5000 },
          );
        } else {
          this.snackBar.open("Erreur lors de la suppression.", "OK", {
            duration: 3000,
          });
        }
      },
    });
  }

  isToggling(id: number): boolean {
    return !!this.loadingToggle[id];
  }
  isDeleting(id: number): boolean {
    return !!this.loadingDelete[id];
  }
}

import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { Client } from "../../../core/models/client.model";

@Component({
  selector: "app-ac-nouveau-dossier",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./ac-nouveau-dossier.component.html",
  styleUrls: ["./ac-nouveau-dossier.component.scss"],
})
export class AcNouveauDossierComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  step = signal(1);
  loading = signal(false);
  searchResults = signal<Client[]>([]);
  selectedClient = signal<Client | null>(null);

  clientForm = this.fb.group({ search: [""] });

  montantForm = this.fb.group({
    montant: [0, [Validators.required, Validators.min(50000)]],
    duree: [12, [Validators.required, Validators.min(1), Validators.max(84)]],
    objectif: ["FONDS_ROULEMENT", Validators.required],
  });

  garantieForm = this.fb.group({
    typeGarantie: ["CAUTIONNEMENT", Validators.required],
    valeur: [0, [Validators.required, Validators.min(1)]],
    description: ["", Validators.required],
  });

  search(q: string) {
    if (q.length < 2) {
      this.searchResults.set([]);
      return;
    }
    this.api
      .get<{ content: Client[] }>("/api/v1/clients", { search: q, size: 5 })
      .subscribe((r: { content: Client[] }) =>
        this.searchResults.set(r.content),
      );
  }

  selectClient(c: Client) {
    this.selectedClient.set(c);
    this.searchResults.set([]);
  }

  nextStep() {
    if (this.step() === 1 && !this.selectedClient()) return;
    if (this.step() === 2 && this.montantForm.invalid) return;
    if (this.step() === 3 && this.garantieForm.invalid) return;
    this.step.update((s: number) => s + 1);
  }

  prevStep() {
    this.step.update((s: number) => Math.max(1, s - 1));
  }

  submit() {
    if (
      !this.selectedClient() ||
      this.montantForm.invalid ||
      this.garantieForm.invalid
    )
      return;
    this.loading.set(true);
    const payload = {
      clientId: this.selectedClient()!.idClient,
      montant: this.montantForm.value.montant,
      duree: this.montantForm.value.duree,
      objectif: this.montantForm.value.objectif,
      typeGarantie: this.garantieForm.value.typeGarantie,
      valeurGarantie: this.garantieForm.value.valeur,
      descriptionGarantie: this.garantieForm.value.description,
    };
    this.api.post("/api/v1/dossiers-credit", payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.showSuccess(
          "Dossier créé",
          "Le dossier de crédit a été enregistré.",
        );
        this.router.navigate(["/credit/dossiers"]);
      },
      error: () => {
        this.loading.set(false);
        this.toast.showError("Erreur", "Impossible de créer le dossier.");
      },
    });
  }

  get objectifLabel() {
    const m: Record<string, string> = {
      CREATION: "Création d'entreprise",
      FONDS_ROULEMENT: "Fonds de roulement",
      INVESTISSEMENT: "Investissement",
    };
    return m[this.montantForm.value.objectif ?? ""] ?? "";
  }
}

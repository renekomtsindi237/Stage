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
  selector: "app-cai-encaissement",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./cai-encaissement.component.html",
  styleUrls: ["./cai-encaissement.component.scss"],
})
export class CaiEncaissementComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  loading = signal(false);
  searchResults = signal<Client[]>([]);
  selectedClient = signal<Client | null>(null);

  form = this.fb.group({
    montant: [0, [Validators.required, Validators.min(1)]],
    typeOperation: ["REMBOURSEMENT", Validators.required],
    canal: ["ESPECES", Validators.required],
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

  select(c: Client) {
    this.selectedClient.set(c);
    this.searchResults.set([]);
  }

  submit() {
    if (!this.selectedClient() || this.form.invalid) return;
    this.loading.set(true);
    this.api
      .post("/api/v1/caisse/encaissements", {
        clientUid: this.selectedClient()!.idClient,
        montant: this.form.value.montant,
        typeOperation: this.form.value.typeOperation,
        canal: this.form.value.canal,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.toast.showSuccess(
            "Encaissement enregistré",
            `${this.form.value.montant?.toLocaleString("fr-FR")} FCFA reçus.`,
          );
          this.router.navigate(["/caisse/dashboard"]);
        },
        error: () => {
          this.loading.set(false);
          this.toast.showError(
            "Erreur",
            "Impossible d'enregistrer l'encaissement.",
          );
        },
      });
  }
}

import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { Client } from "../../../core/models/client.model";
import { TranslatePipe } from "@ngx-translate/core";

@Component({
  selector: "app-cai-encaissement",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, RouterLink],
  templateUrl: "./cai-encaissement.component.html",
  styleUrls: ["./cai-encaissement.component.scss"],
})
export class CaiEncaissementComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading = signal(false);
  confirming = signal(false);
  receipt = signal(false);
  lastAmount = signal(0);
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
    if (this.loading()) return;
    if (!this.confirming()) {
      this.confirming.set(true);
      return;
    }
    this.loading.set(true);
    const montant = this.form.value.montant ?? 0;
    this.api
      .post("/api/v1/caisse/encaissements", {
        clientUid: this.selectedClient()!.idClient,
        montant,
        typeOperation: this.form.value.typeOperation,
        canal: this.form.value.canal,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.confirming.set(false);
          this.lastAmount.set(montant);
          this.receipt.set(true);
          this.toast.showI18nSuccess(
            "caisse.toast_enc_title",
            "caisse.toast_enc_body",
            { amount: montant.toLocaleString("fr-FR") },
          );
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.toast.showApiError(err, "caisse.toast_enc_error");
        },
      });
  }

  newEncaissement() {
    this.receipt.set(false);
    this.confirming.set(false);
    this.selectedClient.set(null);
    this.form.reset({
      montant: 0,
      typeOperation: "REMBOURSEMENT",
      canal: "ESPECES",
    });
  }
}

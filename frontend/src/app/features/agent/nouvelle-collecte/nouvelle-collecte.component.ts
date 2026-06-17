import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
} from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { CommonModule } from "@angular/common";
import { ApiService } from "../../../core/http/api.service";
import { Client } from "../../../core/models/client.model";

@Component({
  selector: "app-nouvelle-collecte",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./nouvelle-collecte.component.html",
  styleUrls: ["./nouvelle-collecte.component.scss"],
})
export class NouvelleCollecteComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  loading = signal(false);
  saved = signal(false);
  error = signal("");
  searchQuery = signal("");
  searchResults = signal<Client[]>([]);
  selectedClient = signal<Client | null>(null);
  gpsEnabled = signal(true);

  form = this.fb.group({
    montant: [0, [Validators.required, Validators.min(1)]],
    typeOperation: ["EPARGNE", Validators.required],
  });

  search(q: string) {
    this.searchQuery.set(q);
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

  setType(t: "EPARGNE" | "REMBOURSEMENT") {
    this.form.controls.typeOperation.setValue(t);
  }

  submit() {
    if (!this.selectedClient() || this.form.invalid) return;
    this.loading.set(true);
    this.error.set("");
    const payload = {
      clientId: this.selectedClient()!.id,
      montant: this.form.value.montant!,
      typeOperation: this.form.value.typeOperation!,
      positionGps: this.gpsEnabled() ? { lat: 3.848, lng: 11.502 } : undefined,
    };
    this.api.post("/api/v1/collectes", payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.saved.set(true);
        setTimeout(() => this.router.navigate(["/agent"]), 1500);
      },
      error: () => {
        this.loading.set(false);
        this.error.set("Erreur lors de l'enregistrement.");
      },
    });
  }

  toggleGps() {
    this.gpsEnabled.update((v: boolean) => !v);
  }

  get typeOp() {
    return this.form.controls.typeOperation.value;
  }
}

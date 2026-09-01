import {
  Component,
  inject,
  signal,
  ChangeDetectionStrategy,
  OnInit,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { TranslatePipe } from "@ngx-translate/core";
import { Router } from "@angular/router";
import { ApiService } from "../../../core/http/api.service";
import { ToastService } from "../../../core/services/toast.service";
import { Client } from "../../../core/models/client.model";

const DRAFT_KEY = "ac_nouveau_draft_v1";

@Component({
  selector: "app-ac-nouveau-dossier",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: "./ac-nouveau-dossier.component.html",
  styleUrls: ["./ac-nouveau-dossier.component.scss"],
})
export class AcNouveauDossierComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  step = signal(1);
  loading = signal(false);
  searchResults = signal<Client[]>([]);
  selectedClient = signal<Client | null>(null);
  draftRestored = signal(false);

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

  ngOnInit() {
    this.restoreDraft();
    this.montantForm.valueChanges.subscribe(() => this.saveDraft());
    this.garantieForm.valueChanges.subscribe(() => this.saveDraft());
  }

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
    this.saveDraft();
  }

  nextStep() {
    if (this.step() === 1 && !this.selectedClient()) return;
    if (this.step() === 2 && this.montantForm.invalid) return;
    if (this.step() === 3 && this.garantieForm.invalid) return;
    this.step.update((s: number) => s + 1);
    this.saveDraft();
  }

  prevStep() {
    this.step.update((s: number) => Math.max(1, s - 1));
    this.saveDraft();
  }

  submit() {
    if (
      !this.selectedClient() ||
      this.montantForm.invalid ||
      this.garantieForm.invalid
    )
      return;
    if (this.loading()) return;
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
        this.clearDraft();
        this.toast.showI18nSuccess(
          "ac_nouveau.toast_ok_title",
          "ac_nouveau.toast_ok_body",
        );
        this.router.navigate(["/credit/dossiers"]);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.toast.showApiError(err, "ac_nouveau.toast_error");
      },
    });
  }

  private saveDraft() {
    try {
      localStorage.setItem(
        DRAFT_KEY,
        JSON.stringify({
          step: this.step(),
          client: this.selectedClient(),
          montant: this.montantForm.getRawValue(),
          garantie: this.garantieForm.getRawValue(),
        }),
      );
    } catch {
      /* quota */
    }
  }

  private restoreDraft() {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (!raw) return;
      const d = JSON.parse(raw) as {
        step?: number;
        client?: Client | null;
        montant?: Record<string, unknown>;
        garantie?: Record<string, unknown>;
      };
      if (d.client) this.selectedClient.set(d.client);
      if (d.montant)
        this.montantForm.patchValue(d.montant as never, { emitEvent: false });
      if (d.garantie)
        this.garantieForm.patchValue(d.garantie as never, { emitEvent: false });
      if (d.step && d.step >= 1 && d.step <= 4) this.step.set(d.step);
      this.draftRestored.set(true);
    } catch {
      /* ignore */
    }
  }

  private clearDraft() {
    try {
      localStorage.removeItem(DRAFT_KEY);
    } catch {
      /* ignore */
    }
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

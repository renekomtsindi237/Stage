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
import { TranslatePipe } from "@ngx-translate/core";

export interface Ticket {
  id: number;
  titre: string;
  description: string;
  priorite: "BASSE" | "NORMALE" | "HAUTE" | "CRITIQUE";
  statut: "OUVERT" | "EN_COURS" | "RESOLU" | "FERME";
  categorie: string;
  auteurUsername?: string;
  auteurRole?: string;
  resolution?: string;
  traiteParUsername?: string;
  dateTraitement?: string;
  createdAt: string;
  updatedAt: string;
}

interface TicketPage {
  content: Ticket[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-sup-tickets",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe],
  templateUrl: "./sup-tickets.component.html",
  styleUrls: ["./sup-tickets.component.scss"],
})
export class SupTicketsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  loading = signal(true);
  page = signal<TicketPage | null>(null);
  currentPage = signal(0);
  activeTab = signal<string>("OUVERT");
  selected = signal<Ticket | null>(null);
  submittingResponse = signal(false);

  responseForm = this.fb.group({
    resolution: ["", [Validators.required, Validators.minLength(10)]],
  });

  readonly tabs = ["OUVERT", "EN_COURS", "RESOLU", "FERME", "TOUS"];

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const statut = this.activeTab() === "TOUS" ? undefined : this.activeTab();
    this.api
      .get<TicketPage>("/api/v1/support/tickets", {
        page: this.currentPage(),
        size: 20,
        statut,
      })
      .subscribe({
        next: (p: TicketPage) => {
          // Normalize accentuated key from Java field name to ASCII for template safety
          p.content = p.content.map((t) => ({
            ...t,
            traiteParUsername: (t as unknown as Record<string, unknown>)[
              "traitéParUsername"
            ] as string | undefined,
          }));
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  selectTicket(t: Ticket) {
    this.selected.set(t);
    this.responseForm.reset();
    if (t.resolution) {
      this.responseForm.patchValue({ resolution: t.resolution });
    }
  }

  prendreEnCharge(id: number) {
    this.api
      .patch(`/api/v1/support/tickets/${id}`, { statut: "EN_COURS" })
      .subscribe({
        next: () => {
          this.toast.showSuccess("Ticket pris en charge", "Statut mis à jour.");
          this.load();
          this.selected.set(null);
        },
        error: () => this.toast.showError("Erreur", "Mise à jour impossible."),
      });
  }

  soumettrReponse(id: number) {
    if (this.responseForm.invalid) {
      this.responseForm.markAllAsTouched();
      return;
    }
    this.submittingResponse.set(true);
    const resolution = this.responseForm.value.resolution!;
    this.api
      .patch(`/api/v1/support/tickets/${id}`, { statut: "RESOLU", resolution })
      .subscribe({
        next: () => {
          this.submittingResponse.set(false);
          this.toast.showSuccess(
            "Réponse envoyée",
            "L'utilisateur a été notifié.",
          );
          this.responseForm.reset();
          this.selected.set(null);
          this.load();
        },
        error: () => {
          this.submittingResponse.set(false);
          this.toast.showError("Erreur", "Impossible d'envoyer la réponse.");
        },
      });
  }

  fermer(id: number) {
    this.api
      .patch(`/api/v1/support/tickets/${id}`, { statut: "FERME" })
      .subscribe({
        next: () => {
          this.toast.showSuccess("Ticket fermé", "");
          this.selected.set(null);
          this.load();
        },
        error: () => this.toast.showError("Erreur", "Mise à jour impossible."),
      });
  }

  switchTab(tab: string) {
    this.activeTab.set(tab);
    this.currentPage.set(0);
    this.selected.set(null);
    this.load();
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }

  prioriteClass(p: string) {
    return (
      {
        BASSE: "badge-secondary",
        NORMALE: "badge-info",
        HAUTE: "badge-warning",
        CRITIQUE: "badge-danger",
      }[p] ?? ""
    );
  }

  statutClass(s: string) {
    return (
      {
        OUVERT: "badge-danger",
        EN_COURS: "badge-warning",
        RESOLU: "badge-success",
        FERME: "badge-secondary",
      }[s] ?? ""
    );
  }
}

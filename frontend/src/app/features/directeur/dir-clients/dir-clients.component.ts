import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule, FormBuilder } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { ApiService } from "../../../core/http/api.service";
import { Client } from "../../../core/models/client.model";
import { FcfaPipe } from "../../../shared/pipes/fcfa.pipe";
import { AlertBadgeComponent } from "../../../shared/components/alert-badge/alert-badge.component";

interface ClientPage {
  content: Client[];
  totalElements: number;
  totalPages: number;
  number: number;
}

@Component({
  selector: "app-dir-clients",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    FcfaPipe,
    AlertBadgeComponent,
  ],
  templateUrl: "./dir-clients.component.html",
  styleUrls: ["./dir-clients.component.scss"],
})
export class DirClientsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  loading = signal(true);
  page = signal<ClientPage | null>(null);
  currentPage = signal(0);

  filterForm = this.fb.group({ search: [""], statut: [""], agence: [""] });

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    const { search, statut, agence } = this.filterForm.value;
    this.api
      .get<ClientPage>("/api/v1/clients", {
        page: this.currentPage(),
        size: 20,
        search,
        statut,
        agence,
      })
      .subscribe({
        next: (p: ClientPage) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  goPage(n: number) {
    this.currentPage.set(n);
    this.load();
  }
}

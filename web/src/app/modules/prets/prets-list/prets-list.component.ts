import { Component, OnInit } from "@angular/core";
import { PageEvent } from "@angular/material/paginator";
import { PretService } from "../pret.service";
import { PretResponse, StatutPret } from "@core/models/pret.model";

@Component({
  selector: "imf-prets-list",
  templateUrl: "./prets-list.component.html",
  styleUrls: ["./prets-list.component.scss"],
})
export class PretsListComponent implements OnInit {
  prets: PretResponse[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  error = "";
  statutFiltre: StatutPret | "" = "";
  searchTerm = "";

  readonly statutOptions: Array<{ value: StatutPret | ""; label: string }> = [
    { value: "", label: "Tous" },
    { value: "ACTIF", label: "Actifs" },
    { value: "EN_RETARD", label: "En retard" },
    { value: "EN_RECOUVREMENT", label: "En recouvrement" },
    { value: "SOLDE", label: "Soldés" },
    { value: "PERTE", label: "Perte" },
  ];

  readonly displayedColumns = [
    "idPret",
    "nomClient",
    "nomAgence",
    "montantPret",
    "joursRetard",
    "statutPret",
    "actions",
  ];

  constructor(private pretService: PretService) {}

  ngOnInit(): void {
    this.loadPrets();
  }

  loadPrets(): void {
    this.loading = true;
    this.error = "";
    this.pretService
      .listPrets(this.statutFiltre || undefined, this.page, this.pageSize)
      .subscribe({
        next: (data) => {
          this.prets = data.content;
          this.total = data.totalElements;
          this.loading = false;
        },
        error: () => {
          this.error = "error";
          this.loading = false;
        },
      });
  }

  onPageChange(event: PageEvent): void {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPrets();
  }

  onFiltreChange(): void {
    this.page = 0;
    this.loadPrets();
  }

  onSearchChange(): void {
    // TODO: Implémenter la recherche côté serveur
    this.page = 0;
  }

  getStatutClass(statut: string): string {
    const map: Record<string, string> = {
      ACTIF: "actif",
      EN_RETARD: "en_retard",
      EN_RECOUVREMENT: "en_retard",
      SOLDE: "solde",
      PERTE: "perte",
    };
    return map[statut] ?? "";
  }

  getStatutLabel(statut: string): string {
    const map: Record<string, string> = {
      ACTIF: "Actif",
      EN_RETARD: "En retard",
      EN_RECOUVREMENT: "En recouvrement",
      SOLDE: "Soldé",
      PERTE: "Perte",
    };
    return map[statut] ?? statut;
  }

  getStatCount(statut: string): number {
    if (!statut) return this.total;
    return this.prets.filter((p) => p.statutPret === statut).length;
  }

  getInitials(name: string): string {
    if (!name) return "?";
    const parts = name.split(" ");
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }
}

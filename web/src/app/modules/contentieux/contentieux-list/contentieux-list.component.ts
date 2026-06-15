import { Component, OnInit } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { RecouvrementService } from "../../recouvrement/recouvrement.service";
import { DossierRecouvrementResponse } from "@core/models/recouvrement.model";

@Component({
  selector: "app-contentieux-list",
  templateUrl: "./contentieux-list.component.html",
})
export class ContentieuxListComponent implements OnInit {
  dossiers: DossierRecouvrementResponse[] = [];
  loading = true;
  error = "";
  page = 0;
  size = 20;
  total = 0;

  expandedId: number | null = null;
  procedures: any[] = [];
  loadingProc = false;

  showAddProc = false;
  savingProc = false;
  newProc = {
    typeProcedure: "",
    juridiction: "",
    montantReclame: null as number | null,
    motif: "",
  };

  readonly dossiersColumns = [
    "idPret",
    "nomClient",
    "montantImpaye",
    "joursRetard",
    "cobac",
    "actions",
  ];
  readonly procColumns = [
    "typeProcedure",
    "juridiction",
    "statut",
    "montantReclame",
    "createdAt",
  ];

  readonly typeProcedureOptions = [
    { value: "INJONCTION_PAYER", label: "Injonction de payer (OHADA art. 1)" },
    { value: "SAISIE_CONSERVATOIRE", label: "Saisie conservatoire" },
    { value: "SAISIE_ATTRIBUTION", label: "Saisie-attribution" },
    { value: "SAISIE_VENTE", label: "Saisie-vente" },
    { value: "REALISATION_HYPOTHEQUE", label: "Réalisation hypothèque" },
  ];

  constructor(
    private http: HttpClient,
    private recouvrementSvc: RecouvrementService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = "";
    this.recouvrementSvc
      .listDossiers("CONTENTIEUX", false, this.page, this.size)
      .subscribe({
        next: (r) => {
          this.dossiers = r.content;
          this.total = r.totalElements;
          this.loading = false;
        },
        error: () => {
          this.error = "Impossible de charger les dossiers contentieux.";
          this.loading = false;
        },
      });
  }

  toggleDossier(d: DossierRecouvrementResponse): void {
    if (this.expandedId === d.id) {
      this.expandedId = null;
      return;
    }
    this.expandedId = d.id;
    this.procedures = [];
    this.showAddProc = false;
    if (d.uid) {
      this.loadProcedures(d.uid);
    }
  }

  loadProcedures(dossierUid: string): void {
    this.loadingProc = true;
    this.http
      .get<any>(`/api/v1/contentieux/dossier/${dossierUid}/procedures`)
      .subscribe({
        next: (r) => {
          this.procedures = r.data ?? [];
          this.loadingProc = false;
        },
        error: () => {
          this.loadingProc = false;
        },
      });
  }

  ajouterProcedure(dossierUid: string): void {
    if (!this.newProc.typeProcedure || !this.newProc.juridiction) return;
    this.savingProc = true;
    this.http
      .post<any>(
        `/api/v1/contentieux/dossier/${dossierUid}/procedures`,
        this.newProc,
      )
      .subscribe({
        next: (r) => {
          if (r.data) this.procedures.unshift(r.data);
          this.showAddProc = false;
          this.newProc = {
            typeProcedure: "",
            juridiction: "",
            montantReclame: null,
            motif: "",
          };
          this.savingProc = false;
        },
        error: () => {
          this.savingProc = false;
        },
      });
  }

  onPageChange(p: number): void {
    this.page = p;
    this.load();
  }

  cobacLabel(cat: string): string {
    const map: Record<string, string> = {
      EN_SURVEILLANCE: "Surveillance 5%",
      DOUTEUSE: "Douteuse 25%",
      LITIGIEUSE: "Litigieuse 50%",
      CONTENTIEUSE: "Contentieuse 100%",
    };
    return map[cat] ?? cat;
  }
}

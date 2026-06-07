import { Component, OnInit } from "@angular/core";
import { PageEvent } from "@angular/material/paginator";
import { trigger, style, transition, animate } from "@angular/animations";
import { RecouvrementService } from "../recouvrement.service";
import {
  DossierRecouvrementResponse,
  RecouvrementPhase,
  CategorieCobtac,
  OuvrirDossierRequest,
  TypeGarantie,
} from "@core/models/recouvrement.model";
import {
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators,
} from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";

@Component({
  selector: "imf-dossiers-list",
  templateUrl: "./dossiers-list.component.html",
  styleUrls: ["./dossiers-list.component.scss"],
  animations: [
    trigger("slideIn", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(-12px)" }),
        animate(
          "250ms ease-out",
          style({ opacity: 1, transform: "translateY(0)" }),
        ),
      ]),
    ]),
  ],
})
export class DossiersListComponent implements OnInit {
  dossiers: DossierRecouvrementResponse[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  error = "";

  phaseFiltre: RecouvrementPhase | "" = "";
  closFiltre: boolean | null = null;

  showOuvrirForm = false;
  saving = false;
  ouvrirForm!: UntypedFormGroup;

  readonly phaseOptions: Array<{
    value: RecouvrementPhase | "";
    label: string;
  }> = [
    { value: "", label: "Toutes les phases" },
    { value: "RELANCE_AMIABLE", label: "Relance amiable" },
    { value: "MEDIATION_AMIABLE", label: "Médiation amiable" },
    { value: "MISE_EN_DEMEURE", label: "Mise en demeure" },
    { value: "CONTENTIEUX", label: "Contentieux" },
    { value: "REECHELONNEMENT", label: "Rééchelonnement" },
    { value: "PERTE", label: "Perte" },
  ];

  readonly closOptions = [
    { value: null, label: "Ouverts + Clos" },
    { value: false, label: "Ouverts seulement" },
    { value: true, label: "Clos seulement" },
  ];

  readonly garantieOptions: TypeGarantie[] = [
    "CAUTION_SOLIDAIRE",
    "CAUTIONNAIRE_PERSONNEL",
    "NANTISSEMENT",
    "HYPOTHEQUE",
    "DEPOT_GARANTIE",
  ];

  readonly displayedColumns = [
    "idPret",
    "montantImpaye",
    "joursRetard",
    "categorieCobtac",
    "phase",
    "frais",
    "clos",
    "actions",
  ];

  constructor(
    private recouvrementService: RecouvrementService,
    private fb: UntypedFormBuilder,
    public dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.loadDossiers();
  }

  private initForm(): void {
    this.ouvrirForm = this.fb.group({
      idPret: ["", Validators.required],
      nomClient: [""],
      montantImpaye: [null, [Validators.required, Validators.min(1)]],
      joursRetard: [null, [Validators.required, Validators.min(1)]],
      datePremiereEcheanceImpayee: [""],
      agentResponsableId: [null],
      nomCaution: [""],
      telephoneCaution: [""],
      typeGarantie: [""],
    });
  }

  loadDossiers(): void {
    this.loading = true;
    this.error = "";
    this.recouvrementService
      .listDossiers(
        this.phaseFiltre || undefined,
        this.closFiltre ?? undefined,
        this.page,
        this.pageSize,
      )
      .subscribe({
        next: (data) => {
          this.dossiers = data.content;
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
    this.loadDossiers();
  }

  onFiltreChange(): void {
    this.page = 0;
    this.loadDossiers();
  }

  ouvrirDossier(): void {
    if (this.ouvrirForm.invalid) return;
    this.saving = true;
    const val = this.ouvrirForm.value;
    const req: OuvrirDossierRequest = {
      idPret: val.idPret,
      nomClient: val.nomClient || undefined,
      montantImpaye: val.montantImpaye,
      joursRetard: val.joursRetard,
      datePremiereEcheanceImpayee: val.datePremiereEcheanceImpayee || undefined,
      agentResponsableId: val.agentResponsableId || undefined,
      nomCaution: val.nomCaution || undefined,
      telephoneCaution: val.telephoneCaution || undefined,
      typeGarantie: val.typeGarantie || undefined,
    };
    this.recouvrementService.ouvrirDossier(req).subscribe({
      next: () => {
        this.saving = false;
        this.showOuvrirForm = false;
        this.ouvrirForm.reset();
        this.loadDossiers();
      },
      error: () => {
        this.saving = false;
      },
    });
  }

  getPhaseLabel(phase: RecouvrementPhase): string {
    const map: Record<RecouvrementPhase, string> = {
      RELANCE_AMIABLE: "Relance amiable",
      MEDIATION_AMIABLE: "Médiation amiable",
      MISE_EN_DEMEURE: "Mise en demeure",
      CONTENTIEUX: "Contentieux",
      REECHELONNEMENT: "Rééchelonnement",
      PERTE: "Perte",
    };
    return map[phase] ?? phase;
  }

  getPhaseClass(phase: RecouvrementPhase): string {
    const map: Record<RecouvrementPhase, string> = {
      RELANCE_AMIABLE: "phase-relance",
      MEDIATION_AMIABLE: "phase-mediation",
      MISE_EN_DEMEURE: "phase-demeure",
      CONTENTIEUX: "phase-contentieux",
      REECHELONNEMENT: "phase-reechelon",
      PERTE: "phase-perte",
    };
    return map[phase] ?? "";
  }

  getCobacLabel(cat: CategorieCobtac): string {
    const map: Record<CategorieCobtac, string> = {
      EN_SURVEILLANCE: "Surveillance 5%",
      DOUTEUSE: "Douteuse 25%",
      LITIGIEUSE: "Litigieuse 50%",
      CONTENTIEUSE: "Contentieuse 100%",
    };
    return map[cat] ?? cat;
  }

  getCobacClass(cat: CategorieCobtac): string {
    const map: Record<CategorieCobtac, string> = {
      EN_SURVEILLANCE: "cobac-surveillance",
      DOUTEUSE: "cobac-douteuse",
      LITIGIEUSE: "cobac-litigieuse",
      CONTENTIEUSE: "cobac-contentieuse",
    };
    return map[cat] ?? "";
  }

  getGarantieLabel(g: TypeGarantie): string {
    const map: Record<TypeGarantie, string> = {
      CAUTION_SOLIDAIRE: "Caution solidaire",
      CAUTIONNAIRE_PERSONNEL: "Cautionnaire personnel",
      NANTISSEMENT: "Nantissement",
      HYPOTHEQUE: "Hypothèque",
      DEPOT_GARANTIE: "Dépôt de garantie",
    };
    return map[g] ?? g;
  }
}

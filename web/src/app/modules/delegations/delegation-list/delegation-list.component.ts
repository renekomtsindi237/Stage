import { Component, OnInit } from "@angular/core";
import { DelegationService } from "../../../core/services/delegation.service";
import { AuthService } from "../../../core/services/auth.service";
import { Delegation } from "../../../core/models/delegation.model";

@Component({
  selector: "app-delegation-list",
  templateUrl: "./delegation-list.component.html",
})
export class DelegationListComponent implements OnInit {
  delegations: Delegation[] = [];
  mesDelegations: Delegation[] = [];
  loading = true;
  loadingMes = true;
  page = 0;
  size = 20;
  total = 0;
  activeTab: "all" | "mes" = "mes";

  readonly role = this.auth.getRole();
  readonly isManager = ["DIRECTEUR", "DSI", "SUPER_ADMIN"].includes(
    this.role ?? "",
  );

  readonly columns = [
    "typeDelegation",
    "delegantId",
    "delegataireId",
    "motif",
    "dateDebut",
    "dateFin",
    "actif",
    "actions",
  ];
  readonly columnsMes = [
    "typeDelegation",
    "roleDelegue",
    "montantSeuil",
    "dateDebut",
    "dateFin",
  ];

  revoqueUid: string | null = null;
  revoqueLoading = false;

  constructor(
    private delegationSvc: DelegationService,
    private auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.loadMes();
    if (this.isManager) {
      this.activeTab = "all";
      this.loadAll();
    }
  }

  loadAll(): void {
    this.loading = true;
    this.delegationSvc.listDelegations(this.page, this.size).subscribe({
      next: (r) => {
        this.delegations = r.content ?? [];
        this.total = r.totalElements ?? 0;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  loadMes(): void {
    this.loadingMes = true;
    this.delegationSvc.mesDelegations().subscribe({
      next: (list) => {
        this.mesDelegations = list;
        this.loadingMes = false;
      },
      error: () => {
        this.loadingMes = false;
      },
    });
  }

  onPageChange(p: number): void {
    this.page = p;
    this.loadAll();
  }

  confirmerRevocation(uid: string): void {
    this.revoqueUid = uid;
  }

  annulerRevocation(): void {
    this.revoqueUid = null;
  }

  revoquer(): void {
    if (!this.revoqueUid) return;
    this.revoqueLoading = true;
    this.delegationSvc.revoquer(this.revoqueUid).subscribe({
      next: () => {
        this.delegations = this.delegations.map((d) =>
          d.uid === this.revoqueUid ? { ...d, actif: false } : d,
        );
        this.revoqueUid = null;
        this.revoqueLoading = false;
      },
      error: () => {
        this.revoqueLoading = false;
      },
    });
  }

  typeLabel(type: string): string {
    return type === "REASSIGNATION_DOSSIER"
      ? "Réassignation dossier"
      : "Délégation d'autorité";
  }
}

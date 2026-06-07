import { Component, OnInit } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { PretService } from "../pret.service";
import { PretResponse } from "@core/models/pret.model";

@Component({
  selector: "imf-pret-detail",
  templateUrl: "./pret-detail.component.html",
  styleUrls: ["./pret-detail.component.scss"],
})
export class PretDetailComponent implements OnInit {
  pret: PretResponse | null = null;
  loading = false;
  error = "";

  constructor(
    private route: ActivatedRoute,
    private pretService: PretService,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get("id")!;
    this.loading = true;
    this.pretService.getById(id).subscribe({
      next: (p) => {
        this.pret = p;
        this.loading = false;
      },
      error: () => {
        this.error = "Prêt introuvable.";
        this.loading = false;
      },
    });
  }
}

import { Component, OnInit } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { ClientService } from "../client.service";
import { ClientResponse } from "@core/models/client.model";

@Component({
  selector: "imf-client-detail",
  templateUrl: "./client-detail.component.html",
  styleUrls: ["./client-detail.component.scss"],
})
export class ClientDetailComponent implements OnInit {
  client: ClientResponse | null = null;
  loading = false;
  error = "";

  constructor(
    private route: ActivatedRoute,
    private clientService: ClientService,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get("id")!;
    this.loading = true;
    this.clientService.getById(id).subscribe({
      next: (c) => {
        this.client = c;
        this.loading = false;
      },
      error: () => {
        this.error = "Client introuvable.";
        this.loading = false;
      },
    });
  }
}

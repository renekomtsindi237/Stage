import { Component, inject, OnInit } from "@angular/core";
import { Router } from "@angular/router";
import { AuthService } from "../../core/auth/auth.service";

@Component({
  selector: "app-dashboard",
  standalone: true,
  template: "",
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit() {
    this.router.navigateByUrl(this.auth.defaultRouteForRole(), {
      replaceUrl: true,
    });
  }
}

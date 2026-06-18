import {
  Component,
  inject,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { AuthService } from "../../core/auth/auth.service";

@Component({
  selector: "app-landing",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: "./landing.component.html",
  styleUrls: ["./landing.component.scss"],
})
export class LandingComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly features = [
    {
      icon: "people",
      title: "Suivi de vos clients",
      desc: "Retrouvez facilement la situation de chaque client : ses crédits, ses remboursements et ses éventuels retards.",
    },
    {
      icon: "notifications_active",
      title: "Alertes automatiques",
      desc: "La plateforme vous avertit dès qu'un client est en retard. Plus besoin de vérifier manuellement chaque dossier.",
    },
    {
      icon: "bar_chart",
      title: "Tableaux de bord clairs",
      desc: "Visualisez en un coup d'œil l'état de votre portefeuille de crédits, les encours et les performances de vos agents.",
    },
    {
      icon: "map",
      title: "Agents sur le terrain",
      desc: "Vos agents collectent les données directement chez les clients. Vous suivez leur activité depuis votre bureau.",
    },
    {
      icon: "lock",
      title: "Données sécurisées",
      desc: "Chaque utilisateur n'accède qu'à ce qui le concerne. Vos données clients sont protégées et confidentielles.",
    },
    {
      icon: "apartment",
      title: "Multi-agences",
      desc: "Gérez toutes vos agences depuis une seule plateforme. Chaque directeur d'agence voit les chiffres de son périmètre.",
    },
  ];

  ngOnInit() {
    if (this.auth.isLoggedIn()) {
      this.router.navigate([this.auth.defaultRouteForRole()]);
    }
  }
}

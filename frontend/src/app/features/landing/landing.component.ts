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
      color: "#4f46e5",
      title: "Suivi des clients",
      desc: "Retrouvez facilement la situation de chaque client : ses crédits, ses remboursements et ses éventuels retards.",
    },
    {
      icon: "notifications_active",
      color: "#0891b2",
      title: "Alertes automatiques",
      desc: "La plateforme vous avertit dès qu'un client est en retard. Plus besoin de vérifier manuellement chaque dossier.",
    },
    {
      icon: "bar_chart",
      color: "#059669",
      title: "Tableaux de bord",
      desc: "Visualisez en un coup d'œil l'état de votre portefeuille, les encours et les performances de vos agents.",
    },
    {
      icon: "map",
      color: "#d97706",
      title: "Agents terrain",
      desc: "Vos agents collectent les informations directement chez les clients. Vous suivez leur activité depuis votre bureau.",
    },
    {
      icon: "lock",
      color: "#7c3aed",
      title: "Données sécurisées",
      desc: "Chaque utilisateur n'accède qu'à ce qui le concerne. Vos données clients sont protégées et confidentielles.",
    },
    {
      icon: "apartment",
      color: "#dc2626",
      title: "Multi-agences",
      desc: "Gérez toutes vos agences depuis une seule plateforme. Chaque directeur voit les chiffres de son périmètre.",
    },
  ];

  readonly steps = [
    {
      num: "1",
      icon: "login",
      title: "Connectez-vous",
      desc: "Entrez votre adresse email pour recevoir un code de connexion sécurisé. Aucun mot de passe à mémoriser.",
    },
    {
      num: "2",
      icon: "dashboard",
      title: "Accédez à votre tableau de bord",
      desc: "Retrouvez vos dossiers, vos clients et les alertes du jour en un seul écran, adapté à votre rôle.",
    },
    {
      num: "3",
      icon: "task_alt",
      title: "Travaillez efficacement",
      desc: "Enregistrez des collectes, suivez les remboursements et recevez des alertes automatiques en temps réel.",
    },
  ];

  readonly stats = [
    {
      icon: "domain",
      value: "Multi-IMF",
      label: "Plusieurs institutions sur une seule plateforme",
    },
    {
      icon: "security",
      value: "COBAC",
      label: "Conforme aux normes réglementaires",
    },
    {
      icon: "bolt",
      value: "Temps réel",
      label: "Alertes et mises à jour instantanées",
    },
    {
      icon: "devices",
      value: "Web",
      label: "Accessible depuis tout navigateur",
    },
  ];

  ngOnInit() {
    if (this.auth.isLoggedIn()) {
      this.router.navigate([this.auth.defaultRouteForRole()]);
    }
  }
}

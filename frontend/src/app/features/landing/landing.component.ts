import {
  Component,
  inject,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { AuthService } from "../../core/auth/auth.service";
import { TranslatePipe } from "@ngx-translate/core";

@Component({
  selector: "app-landing",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  templateUrl: "./landing.component.html",
  styleUrls: ["./landing.component.scss"],
})
export class LandingComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);

  private readonly docs = {
    guide: {
      titleKey: "landing.guide_title",
      url: "https://pub-8ce5d37bb51240a187e672188a13c136.r2.dev/GUIDE%20D%E2%80%99UTILISATION%20COMPLET%20DE%20MICRORECOUV.pdf",
    },
    api: {
      titleKey: "landing.api_guide_title",
      url: "https://pub-8ce5d37bb51240a187e672188a13c136.r2.dev/Guide%20d'int%C3%A9gration%20d'API.pdf",
    },
  } as const;

  activeDoc: { titleKey: string; url: string; safeUrl: SafeResourceUrl } | null =
    null;

  openDoc(key: keyof typeof this.docs): void {
    const doc = this.docs[key];
    this.activeDoc = {
      ...doc,
      safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(doc.url),
    };
  }

  closeDoc(): void {
    this.activeDoc = null;
  }

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

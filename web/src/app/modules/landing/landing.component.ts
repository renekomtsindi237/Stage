import { Component, OnInit, OnDestroy, HostListener } from "@angular/core";
import { Router } from "@angular/router";
import { fadeInUp, staggerIn, reveal } from "../../shared/animations";

@Component({
  selector: "imf-landing",
  templateUrl: "./landing.component.html",
  styleUrls: ["./landing.component.scss"],
  animations: [fadeInUp, staggerIn, reveal],
})
export class LandingComponent implements OnInit, OnDestroy {
  headerScrolled = false;
  scrollProgress = 0; // 0 – 100
  showScrollTop = false;

  readonly SCROLL_RADIUS = 22;
  get scrollCircumference(): number {
    return 2 * Math.PI * this.SCROLL_RADIUS;
  }
  get strokeDashoffset(): number {
    return this.scrollCircumference * (1 - this.scrollProgress / 100);
  }

  readonly trustBadges = [
    { icon: "lock", label: "Données sécurisées" },
    { icon: "notifications_active", label: "Alertes en temps réel" },
    { icon: "cloud_done", label: "Disponible 24/7" },
    { icon: "devices", label: "Web & mobile" },
  ];

  readonly metrics = [
    {
      icon: "corporate_fare",
      value: "Multi-IMF",
      label: "Plusieurs institutions sur une même plateforme",
    },
    {
      icon: "warning_amber",
      value: "Instantané",
      label: "Alertes dès qu'un prêt est en retard",
    },
    {
      icon: "bar_chart",
      value: "En direct",
      label: "Tableaux de bord actualisés automatiquement",
    },
    {
      icon: "sync",
      value: "Automatisé",
      label: "Import quotidien de vos données de prêts",
    },
  ];

  readonly steps = [
    {
      icon: "login",
      title: "Connexion sécurisée",
      desc: "Chaque agent, directeur ou administrateur accède à son espace avec ses propres droits. Rien de plus, rien de moins.",
      color: "#2563EB",
    },
    {
      icon: "sync_alt",
      title: "Vos données toujours à jour",
      desc: "Les prêts, les clients et les remboursements sont automatiquement importés chaque jour. Aucune saisie manuelle nécessaire.",
      color: "#0D9488",
    },
    {
      icon: "insights",
      title: "Pilotage et action",
      desc: "Consultez vos tableaux de bord, gérez les impayés et générez vos rapports — tout depuis une seule interface.",
      color: "#C8923A",
    },
  ];

  activeStepIndex: number | null = null;
  stepOverlayVisible = false;
  private stepOverlayTimer?: ReturnType<typeof setTimeout>;

  readonly features = [
    {
      icon: "dashboard",
      title: "Tableau de bord",
      desc: "Visualisez en temps réel les taux de remboursement, les collectes du mois et l'état de votre portefeuille.",
      color: "#2563EB",
    },
    {
      icon: "warning_amber",
      title: "Alertes sur les impayés",
      desc: "Recevez une notification par e-mail ou sur mobile dès qu'un prêt dépasse sa date d'échéance.",
      color: "#F59E0B",
    },
    {
      icon: "people",
      title: "Gestion des clients",
      desc: "Consultez le profil complet de chaque client, son historique de remboursements et l'état de ses prêts.",
      color: "#0D9488",
    },
    {
      icon: "bar_chart",
      title: "Rapports & exports",
      desc: "Générez des rapports PDF ou Excel en quelques secondes : collectes, prêts en retard, performance mensuelle.",
      color: "#8B5CF6",
    },
    {
      icon: "smartphone",
      title: "Application mobile",
      desc: "Les agents terrain enregistrent les paiements même sans connexion internet. La sync se fait automatiquement.",
      color: "#EC4899",
    },
    {
      icon: "admin_panel_settings",
      title: "Gestion des accès",
      desc: "Chaque utilisateur a un rôle précis : Directeur, Analyste, Agent de terrain. Les droits sont cloisonnés par rôle.",
      color: "#10B981",
    },
  ];

  readonly benefits = [
    {
      icon: "speed",
      title: "Agissez plus vite",
      desc: "Les dossiers urgents remontent automatiquement en haut de votre liste, classés par niveau de risque.",
      color: "#8BD1FF",
    },
    {
      icon: "shield",
      title: "Tout est tracé",
      desc: "Chaque action — alerte envoyée, paiement enregistré, décision prise — est conservée dans l'historique.",
      color: "#B2F5EA",
    },
    {
      icon: "auto_graph",
      title: "Suivi en temps réel",
      desc: "Des chiffres clairs pour piloter votre institution : taux de retard, encours, performance de recouvrement.",
      color: "#A5B4FC",
    },
    {
      icon: "groups",
      title: "Travail en équipe",
      desc: "Assignez des dossiers, laissez des commentaires et suivez l'avancement des relances avec votre équipe.",
      color: "#FDE68A",
    },
  ];

  readonly integrations = [
    {
      name: "CBS / Core Banking",
      desc: "Import planifie et mapping automatise des portefeuilles.",
    },
    {
      name: "Mobile Collect",
      desc: "Synchronisation des collectes terrain en quelques minutes.",
    },
    {
      name: "Messaging",
      desc: "Envoi SMS, e-mail et WhatsApp selon vos regles.",
    },
    {
      name: "Export Data",
      desc: "CSV, PDF et API pour vos reporting internes.",
    },
    {
      name: "Data Pipeline",
      desc: "ETL supervise pour garder la qualite des donnees.",
    },
    { name: "SSO", desc: "Connexion simplifiee pour vos equipes." },
  ];

  readonly faqs = [
    {
      q: "En combien de temps la plateforme peut-elle etre operationnelle?",
      a: "Apres configuration et import initial, la mise en route peut se faire en quelques jours.",
    },
    {
      q: "Nos donnees sont-elles securisees?",
      a: "Oui. Chiffrement, journaux d'audit et segregation logique par IMF sont inclus.",
    },
    {
      q: "Peut-on garder nos outils existants?",
      a: "Oui. L'API permet d'integrer vos flux actuels sans changer vos processus.",
    },
    {
      q: "Que se passe-t-il si la connexion internet est instable?",
      a: "Le mobile peut travailler hors ligne et synchroniser automatiquement au retour reseau.",
    },
  ];

  constructor(private router: Router) {}

  ngOnInit(): void {}
  ngOnDestroy(): void {}

  @HostListener("window:scroll")
  onScroll(): void {
    const scrollY = window.scrollY;
    const docH = document.documentElement.scrollHeight - window.innerHeight;
    this.headerScrolled = scrollY > 60;
    this.showScrollTop = scrollY > 300;
    this.scrollProgress = docH > 0 ? Math.round((scrollY / docH) * 100) : 0;
  }

  goToLogin(): void {
    this.router.navigate(["/login"]);
  }

  goToImfLogin(): void {
    this.router.navigate(["/login"]);
  }

  goToAdminLogin(): void {
    this.router.navigate(["/login"], { queryParams: { mode: "admin" } });
  }

  scrollToTop(): void {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  toggleStep(index: number): void {
    this.activeStepIndex = this.activeStepIndex === index ? null : index;
  }

  playStepAnimation(index: number): void {
    this.activeStepIndex = index;
    this.stepOverlayVisible = true;
    if (this.stepOverlayTimer) clearTimeout(this.stepOverlayTimer);
    this.stepOverlayTimer = setTimeout(() => {
      this.stepOverlayVisible = false;
    }, 1400);
  }

  scrollTo(id: string, event?: Event): void {
    event?.preventDefault();
    document
      .getElementById(id)
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

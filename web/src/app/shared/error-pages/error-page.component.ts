import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import {
  trigger,
  transition,
  style,
  animate,
  query,
  stagger,
} from "@angular/animations";
import { Subscription } from "rxjs";
import { NetworkService } from "@core/services/network.service";
import { Location } from "@angular/common";

interface ErrorConfig {
  code: string;
  screenText: string;
  title: string;
  message: string;
  icon: string;
  isOffline: boolean;
}

const ERROR_CONFIGS: Record<string, Omit<ErrorConfig, "isOffline">> = {
  "404": {
    code: "404",
    screenText: "NOT FOUND",
    title: "Page introuvable",
    message:
      "La page que vous recherchez n'existe pas ou a été déplacée. Vérifiez l'URL ou retournez à l'accueil.",
    icon: "search_off",
  },
  "403": {
    code: "403",
    screenText: "FORBIDDEN",
    title: "Accès refusé",
    message:
      "Vous n'avez pas les droits nécessaires pour accéder à cette ressource. Contactez votre administrateur.",
    icon: "lock",
  },
  "500": {
    code: "500",
    screenText: "SERVER ERR",
    title: "Erreur serveur",
    message:
      "Une erreur interne est survenue sur nos serveurs. Nos équipes ont été notifiées. Réessayez dans quelques instants.",
    icon: "dns",
  },
  offline: {
    code: "NET",
    screenText: "NO SIGNAL",
    title: "Connexion perdue",
    message:
      "Impossible de contacter le serveur. Vérifiez votre connexion internet et réessayez.",
    icon: "wifi_off",
  },
};

@Component({
  selector: "imf-error-page",
  templateUrl: "./error-page.component.html",
  styleUrls: ["./error-page.component.scss"],
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger("fadeUp", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(20px)" }),
        animate("480ms 200ms cubic-bezier(0.22,1,0.36,1)", style({ opacity: 1, transform: "translateY(0)" })),
      ]),
    ]),
    trigger("staggerDigits", [
      transition(":enter", [
        query(".ep-digit", [
          style({ opacity: 0, transform: "scale(0.6) translateY(20px)" }),
          stagger(80, [
            animate("400ms cubic-bezier(0.34,1.56,0.64,1)", style({ opacity: 1, transform: "scale(1) translateY(0)" })),
          ]),
        ], { optional: true }),
      ]),
    ]),
  ],
})
export class ErrorPageComponent implements OnInit, OnDestroy {
  config: ErrorConfig = {
    ...ERROR_CONFIGS["404"],
    isOffline: false,
  };

  private subs = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private location: Location,
    private network: NetworkService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.subs.add(
      this.route.data.subscribe((data) => {
        const code = data["code"] as string ?? "404";
        const base = ERROR_CONFIGS[code] ?? ERROR_CONFIGS["404"];
        this.config = { ...base, isOffline: code === "offline" };
        this.cdr.markForCheck();
      }),
    );

    this.subs.add(
      this.network.isOnline$.subscribe((online) => {
        if (!online && this.config.code !== "NET") {
          this.config = { ...ERROR_CONFIGS["offline"], isOffline: true };
          this.cdr.markForCheck();
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  get codeDigits(): string[] {
    return this.config.code.split("");
  }

  goHome(): void {
    this.router.navigate(["/"]);
  }

  goBack(): void {
    this.location.back();
  }

  retry(): void {
    window.location.reload();
  }
}

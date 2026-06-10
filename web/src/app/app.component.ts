import { Component, OnInit, OnDestroy, NgZone } from "@angular/core";
import { Router, NavigationEnd } from "@angular/router";
import { filter, Subscription } from "rxjs";
import { AuthService } from "./core/services/auth.service";
import { NotificationService } from "./core/services/notification.service";

@Component({
  selector: "imf-root",
  templateUrl: "./app.component.html",
  styleUrls: ["./app.component.scss"],
})
export class AppComponent implements OnInit, OnDestroy {
  /** Pages sans shell (sidebar + navbar) */
  isShellless = true;
  sidenavOpen = true;
  showSplash = true;

  private sub?: Subscription;

  /** Routes qui n'affichent pas le shell de navigation */
  private readonly SHELLLESS_ROUTES = ["/", "/login"];
  private readonly SHELLLESS_PREFIXES = ["/error/"];

  constructor(
    public auth: AuthService,
    public notifService: NotificationService,
    private router: Router,
    private ngZone: NgZone,
  ) {}

  ngOnInit(): void {
    this.sub = this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe((e) => {
        const nav = e as NavigationEnd;
        const url = nav.urlAfterRedirects;
        this.isShellless =
          this.SHELLLESS_ROUTES.includes(url) ||
          url === "/" ||
          this.SHELLLESS_PREFIXES.some((p) => url.startsWith(p)) ||
          this.isUnmatchedWildcard(url);
      });

    if (this.auth.isLoggedIn()) {
      this.notifService.init();
    }

    this.ngZone.runOutsideAngular(() => {
      document.addEventListener("mousemove", (e: MouseEvent) => {
        document.documentElement.style.setProperty(
          "--mouse-x",
          `${e.clientX}px`,
        );
        document.documentElement.style.setProperty(
          "--mouse-y",
          `${e.clientY}px`,
        );
      });
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.notifService.reset();
  }

  onSplashDone(): void {
    this.showSplash = false;
  }

  /** Détecte les URLs non matchées qui aboutissent sur la page d'erreur 404 */
  private isUnmatchedWildcard(url: string): boolean {
    const knownPrefixes = [
      "/",
      "/login",
      "/platform",
      "/dashboard",
      "/admin",
      "/analyste",
      "/dsi",
      "/support",
      "/profile",
      "/error",
    ];
    return !knownPrefixes.some((p) => url === p || url.startsWith(p + "/") || url.startsWith(p + "?"));
  }
}

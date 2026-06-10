import { Injectable, NgZone } from "@angular/core";
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse,
} from "@angular/common/http";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { Router } from "@angular/router";

const NAVIGATION_ERRORS: Record<number, string> = {
  403: "/error/403",
  500: "/error/500",
  502: "/error/500",
  503: "/error/500",
  504: "/error/500",
};

/** URLs pour lesquelles on ne redirige jamais vers une page d'erreur */
const NO_REDIRECT_URLS = [
  "/api/v1/auth/",
  "/api/v1/sse",
  "/api/users/me",
];

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  constructor(private router: Router, private ngZone: NgZone) {}

  intercept(
    req: HttpRequest<unknown>,
    next: HttpHandler,
  ): Observable<HttpEvent<unknown>> {
    return next.handle(req).pipe(
      catchError((err: HttpErrorResponse) => {
        let message = "Une erreur inattendue est survenue.";

        if (err.status === 0) {
          message = "Impossible de contacter le serveur. Vérifiez votre connexion.";
        } else if (err.status === 403) {
          message = "Accès refusé. Vous n'avez pas les droits nécessaires.";
        } else if (err.status === 404) {
          message = "Ressource introuvable.";
        } else if (err.status === 422) {
          message = err.error?.message || "Données invalides.";
        } else if (err.status >= 500) {
          message = "Erreur serveur. Veuillez réessayer plus tard.";
        } else if (err.error?.message) {
          message = err.error.message;
        }

        console.error(`[HTTP ${err.status}] ${req.url} — ${message}`);

        // Navigation vers la page d'erreur pour les erreurs critiques
        const errorRoute = NAVIGATION_ERRORS[err.status];
        const isExcluded = NO_REDIRECT_URLS.some((u) => req.url.includes(u));

        if (errorRoute && !isExcluded) {
          this.ngZone.run(() => this.router.navigate([errorRoute]));
        }

        return throwError(() => ({ ...err, userMessage: message }));
      }),
    );
  }
}

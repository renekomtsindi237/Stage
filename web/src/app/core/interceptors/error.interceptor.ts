import { Injectable } from "@angular/core";
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse,
} from "@angular/common/http";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  intercept(
    req: HttpRequest<unknown>,
    next: HttpHandler,
  ): Observable<HttpEvent<unknown>> {
    return next.handle(req).pipe(
      catchError((err: HttpErrorResponse) => {
        let message = "Une erreur inattendue est survenue.";

        if (err.status === 0) {
          message =
            "Impossible de contacter le serveur. Vérifiez votre connexion.";
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

        // Émission vers un service de notification global possible ici
        console.error(`[HTTP ${err.status}] ${req.url} — ${message}`);

        return throwError(() => ({ ...err, userMessage: message }));
      }),
    );
  }
}

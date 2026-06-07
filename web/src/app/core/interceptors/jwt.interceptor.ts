import { Injectable } from "@angular/core";
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse,
} from "@angular/common/http";
import { Observable, throwError, BehaviorSubject } from "rxjs";
import { catchError, filter, switchMap, take } from "rxjs/operators";
import { AuthService } from "../services/auth.service";

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  // null = refresh en cours, 'done' = succès, 'failed' = échec
  private refreshDone$ = new BehaviorSubject<string | null>(null);

  constructor(private authService: AuthService) {}

  intercept(
    req: HttpRequest<unknown>,
    next: HttpHandler,
  ): Observable<HttpEvent<unknown>> {
    const skipRefresh = req.headers.has("X-Skip-Auth-Refresh");
    const cleaned = skipRefresh
      ? req.clone({
          withCredentials: true,
          headers: req.headers.delete("X-Skip-Auth-Refresh"),
        })
      : req.clone({ withCredentials: true });

    return next.handle(cleaned).pipe(
      catchError((err: HttpErrorResponse) => {
        if (
          err.status === 401 &&
          !skipRefresh &&
          !req.url.includes("/api/auth/")
        ) {
          return this.handle401(req, next);
        }
        return throwError(() => err);
      }),
    );
  }

  private handle401(
    req: HttpRequest<unknown>,
    next: HttpHandler,
  ): Observable<HttpEvent<unknown>> {
    if (this.isRefreshing) {
      // Attendre la fin du refresh en cours, puis retenter ou propager l'échec.
      return this.refreshDone$.pipe(
        filter((v) => v !== null),
        take(1),
        switchMap((v) => {
          if (v === "failed")
            return throwError(() => new Error("Session expirée"));
          return next.handle(req.clone({ withCredentials: true }));
        }),
      );
    }

    this.isRefreshing = true;
    this.refreshDone$.next(null);

    return this.authService.refresh().pipe(
      switchMap(() => {
        this.isRefreshing = false;
        this.refreshDone$.next("done");
        return next.handle(req.clone({ withCredentials: true }));
      }),
      catchError((err) => {
        this.isRefreshing = false;
        // Notifier les requêtes en attente que le refresh a échoué avant de déconnecter.
        this.refreshDone$.next("failed");
        this.authService.logout();
        return throwError(() => err);
      }),
    );
  }
}

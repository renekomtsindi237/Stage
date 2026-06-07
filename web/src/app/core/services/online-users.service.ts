import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Subscription, interval } from 'rxjs';
import { switchMap, catchError, startWith, distinctUntilChanged, filter } from 'rxjs/operators';
import { of } from 'rxjs';
import { ApiResponse } from '@core/models/api-response.model';
import { AuthService } from './auth.service';

/**
 * Interroge /api/users/online-count toutes les 30 secondes pour tenir
 * à jour le nombre d'utilisateurs actuellement en ligne.
 *
 * Le comptage est scopé par rôle côté backend :
 *  - SUPER_ADMIN → tous les utilisateurs de la plateforme
 *  - DSI / autres → uniquement les utilisateurs de leur IMF
 *
 * Commence à interroger dès la connexion, s'arrête à la déconnexion.
 */
@Injectable({ providedIn: 'root' })
export class OnlineUsersService implements OnDestroy {

  private readonly POLL_INTERVAL_MS = 30_000;
  private readonly API = '/api/users/online-count';

  private readonly _count$ = new BehaviorSubject<number>(0);
  readonly count$ = this._count$.asObservable();

  private pollSub?: Subscription;

  constructor(
    private http: HttpClient,
    private auth: AuthService,
  ) {
    // Démarrer/arrêter le polling selon l'état de connexion.
    // distinctUntilChanged évite de relancer le poll à chaque refresh de token
    // (storeTokens appelle loggedIn$.next(true) ce qui re-émettrait sans cela).
    this.auth.isLoggedIn$.pipe(distinctUntilChanged()).subscribe(loggedIn => {
      if (loggedIn) this.startPolling();
      else          this.stopPolling();
    });
  }

  private startPolling(): void {
    this.stopPolling();
    this.pollSub = interval(this.POLL_INTERVAL_MS).pipe(
      startWith(0),
      switchMap(() =>
        this.http.get<ApiResponse<number>>(this.API, {
          headers: { 'X-Skip-Auth-Refresh': 'true' },
        }).pipe(
          catchError(() => of(null))
        )
      ),
    ).subscribe(resp => {
      if (resp != null) this._count$.next(resp.data ?? 0);
    });
  }

  private stopPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
    this._count$.next(0);
  }

  get snapshot(): number { return this._count$.value; }

  ngOnDestroy(): void { this.stopPolling(); }
}

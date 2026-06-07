import { Injectable } from '@angular/core';
import { BehaviorSubject, EMPTY, Observable, filter, switchMap } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { ProfileService } from './profile.service';
import { ThemeService } from './theme.service';
import { UserPreferences, UserResponse } from '../models/user.model';

const DEFAULTS: UserPreferences = {
  prefTheme:            'auto',
  prefLangue:           'fr',
  notificationsActives: true,
  notifAlertes:         true,
  notifCollectes:       false,
  notifSync:            false,
  notifPipeline:        false,
  elementsParPage:      20,
};

/**
 * Service centralisé des préférences utilisateur.
 *
 * Cycle de vie :
 *   - Connexion  → charge GET /api/users/me, applique le thème, expose via preferences$
 *   - PATCH /api/users/me/preferences → met à jour la base et l'état local en un seul appel
 *   - Déconnexion → reset aux valeurs par défaut
 *
 * Usage dans un composant :
 *   prefs$ = this.userPrefs.preferences$;
 *
 * Usage pour la mise à jour :
 *   this.userPrefs.patch({ prefTheme: 'dark', elementsParPage: 50 }).subscribe();
 *
 * Usage synchrone (ex. page size dans un service) :
 *   const size = this.userPrefs.snapshot.elementsParPage;
 */
@Injectable({ providedIn: 'root' })
export class UserPreferencesService {

  private readonly prefs$ = new BehaviorSubject<UserPreferences>(DEFAULTS);
  readonly preferences$: Observable<UserPreferences> = this.prefs$.asObservable();

  constructor(
    private authService: AuthService,
    private profileService: ProfileService,
    private themeService: ThemeService,
  ) {
    // Charger et appliquer les préférences à chaque connexion (y compris après refresh page).
    // catchError à l'intérieur du switchMap pour que l'observable externe reste vivant
    // même en cas d'échec réseau ou d'expiration de session, afin de recharger les préférences
    // après une reconnexion sans avoir à recréer le service.
    this.authService.isLoggedIn$.pipe(
      filter(loggedIn => loggedIn),
      switchMap(() => this.profileService.getProfile().pipe(
        catchError(() => EMPTY)
      )),
    ).subscribe({
      next: profile => {
        const prefs = this.fromProfile(profile);
        this.prefs$.next(prefs);
        this.themeService.applyFromPreference(prefs.prefTheme);
      },
    });

    // Remettre à zéro à la déconnexion (thème suit la préférence système)
    this.authService.isLoggedIn$.pipe(
      filter(loggedIn => !loggedIn),
    ).subscribe({
      next: () => {
        this.prefs$.next(DEFAULTS);
        this.themeService.applyFromPreference(DEFAULTS.prefTheme);
      },
      error: () => {}
    });
  }

  /** Valeur instantanée sans abonnement (usage synchrone dans les services). */
  get snapshot(): UserPreferences {
    return this.prefs$.value;
  }

  /**
   * Patch partiel : seuls les champs fournis sont envoyés et mis à jour.
   * Retourne le profil mis à jour pour feedback immédiat dans les composants.
   */
  patch(partial: Partial<UserPreferences>): Observable<UserResponse> {
    return this.profileService.updatePreferences(partial).pipe(
      tap(updated => {
        const prefs = this.fromProfile(updated);
        this.prefs$.next(prefs);
        this.themeService.applyFromPreference(prefs.prefTheme);
      }),
    );
  }

  private fromProfile(profile: UserResponse): UserPreferences {
    return {
      prefTheme:            profile.prefTheme            ?? DEFAULTS.prefTheme,
      prefLangue:           profile.prefLangue           ?? DEFAULTS.prefLangue,
      notificationsActives: profile.notificationsActives ?? DEFAULTS.notificationsActives,
      notifAlertes:         profile.notifAlertes         ?? DEFAULTS.notifAlertes,
      notifCollectes:       profile.notifCollectes       ?? DEFAULTS.notifCollectes,
      notifSync:            profile.notifSync             ?? DEFAULTS.notifSync,
      notifPipeline:        profile.notifPipeline        ?? DEFAULTS.notifPipeline,
      elementsParPage:      profile.elementsParPage       ?? DEFAULTS.elementsParPage,
    };
  }
}

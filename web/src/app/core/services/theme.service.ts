import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {

  private readonly STORAGE_KEY = 'imf_theme';
  private readonly _theme$ = new BehaviorSubject<Theme>(this.loadTheme());

  readonly theme$ = this._theme$.asObservable();

  get isDark(): boolean {
    return this._theme$.value === 'dark';
  }

  get current(): Theme {
    return this._theme$.value;
  }

  constructor() {
    this.applyTheme(this._theme$.value);
  }

  toggle(): void {
    const next: Theme = this._theme$.value === 'light' ? 'dark' : 'light';
    this.setTheme(next);
  }

  setTheme(theme: Theme): void {
    this._theme$.next(theme);
    this.applyTheme(theme);
    localStorage.setItem(this.STORAGE_KEY, theme);
  }

  /**
   * Applique la préférence stockée en base après connexion.
   * 'auto' résout en dark ou light selon la préférence système.
   * Persisté en localStorage pour un affichage immédiat au prochain rechargement.
   */
  applyFromPreference(pref: 'light' | 'dark' | 'auto'): void {
    const resolved: Theme = pref === 'auto'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : pref;
    this.setTheme(resolved);
  }

  private loadTheme(): Theme {
    const stored = localStorage.getItem(this.STORAGE_KEY) as Theme | null;
    if (stored === 'dark' || stored === 'light') return stored;
    // Préférence système
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private applyTheme(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}

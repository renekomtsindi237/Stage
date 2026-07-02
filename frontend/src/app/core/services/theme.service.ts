import { Injectable, signal, effect } from "@angular/core";

@Injectable({ providedIn: "root" })
export class ThemeService {
  private readonly STORAGE_KEY = "microrecouv-theme";

  isDark = signal<boolean>(
    typeof localStorage !== "undefined"
      ? localStorage.getItem(this.STORAGE_KEY) === "dark"
      : false,
  );

  constructor() {
    // Apply stored theme immediately on construction (before first render)
    this.applyTheme(this.isDark());

    // Sync to DOM + localStorage whenever signal changes
    effect(() => {
      const dark = this.isDark();
      this.applyTheme(dark);
      localStorage.setItem(this.STORAGE_KEY, dark ? "dark" : "light");
    });
  }

  toggle() {
    this.isDark.update((v) => !v);
  }

  private applyTheme(dark: boolean) {
    document.documentElement.setAttribute("data-theme", dark ? "dark" : "light");
  }
}

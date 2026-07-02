import { Injectable, signal, inject } from "@angular/core";
import { TranslateService } from "@ngx-translate/core";

@Injectable({ providedIn: "root" })
export class LanguageService {
  private readonly STORAGE_KEY = "microrecouv-lang";
  private readonly translate = inject(TranslateService);

  readonly lang = signal<"fr" | "en">(this._saved());

  constructor() {
    this.translate.use(this.lang());
  }

  toggle() {
    this.setLang(this.lang() === "fr" ? "en" : "fr");
  }

  setLang(lang: "fr" | "en") {
    this.lang.set(lang);
    this.translate.use(lang);
    localStorage.setItem(this.STORAGE_KEY, lang);
    document.documentElement.setAttribute("lang", lang);
  }

  get isFrench() {
    return this.lang() === "fr";
  }

  private _saved(): "fr" | "en" {
    const v = localStorage.getItem(this.STORAGE_KEY);
    return v === "en" ? "en" : "fr";
  }
}

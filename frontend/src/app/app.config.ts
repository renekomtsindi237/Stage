import { ApplicationConfig, provideZoneChangeDetection } from "@angular/core";
import { provideRouter, withComponentInputBinding } from "@angular/router";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { provideAnimations } from "@angular/platform-browser/animations";
import { provideTranslateService } from "@ngx-translate/core";
import { provideTranslateHttpLoader } from "@ngx-translate/http-loader";
import { Chart, registerables } from "chart.js";
import { routes } from "./app.routes";
import { jwtInterceptor } from "./core/http/jwt.interceptor";
import { errorInterceptor } from "./core/http/error.interceptor";

Chart.register(...registerables);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
    provideAnimations(),
    provideTranslateService({ lang: "fr" }),
    ...provideTranslateHttpLoader({ prefix: "/assets/i18n/", suffix: ".json" }),
  ],
};

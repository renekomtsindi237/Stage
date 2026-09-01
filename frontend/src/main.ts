import { registerLocaleData } from "@angular/common";
import localeFr from "@angular/common/locales/fr";
import { bootstrapApplication } from "@angular/platform-browser";
import { appConfig } from "./app/app.config";
import { AppComponent } from "./app/app.component";
import { installCloudflareBeaconGuard } from "./app/core/cloudflare-beacon-guard";

registerLocaleData(localeFr);
installCloudflareBeaconGuard();

bootstrapApplication(AppComponent, appConfig).catch((err) =>
  console.error(err),
);

import { bootstrapApplication } from "@angular/platform-browser";
import { appConfig } from "./app/app.config";
import { AppComponent } from "./app/app.component";
import { installCloudflareBeaconGuard } from "./app/core/cloudflare-beacon-guard";

installCloudflareBeaconGuard();

bootstrapApplication(AppComponent, appConfig).catch((err) =>
  console.error(err),
);

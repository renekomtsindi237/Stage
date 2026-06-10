import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterModule } from "@angular/router";
import { ReactiveFormsModule } from "@angular/forms";

// Angular Material
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatIconModule } from "@angular/material/icon";
import { MatButtonModule } from "@angular/material/button";
import { MatBadgeModule } from "@angular/material/badge";
import { MatListModule } from "@angular/material/list";
import { MatDividerModule } from "@angular/material/divider";
import { MatTooltipModule } from "@angular/material/tooltip";
import { MatMenuModule } from "@angular/material/menu";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatDialogModule } from "@angular/material/dialog";
import { MatInputModule } from "@angular/material/input";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatSelectModule } from "@angular/material/select";
import { MatSnackBarModule } from "@angular/material/snack-bar";

// Components partagés
import { NavbarComponent } from "./navbar/navbar.component";
import { SidebarComponent } from "./sidebar/sidebar.component";
import { ThemeToggleComponent } from "./theme-toggle/theme-toggle.component";
import { SkeletonComponent } from "./skeleton/skeleton.component";
import { SplashComponent } from "./splash/splash.component";
import { FloatingLinesComponent } from "./floating-lines/floating-lines.component";
import { FullscreenToastComponent } from "./fullscreen-toast/fullscreen-toast.component";
import { ConfirmationDialogComponent } from "./confirmation-dialog/confirmation-dialog.component";
import { NotificationPanelComponent } from "./notification-panel/notification-panel.component";
import { ChangePasswordComponent } from "./change-password/change-password.component";
import {
  ContactSupportComponent,
  ContactSupportDialogComponent,
} from "./contact-support/contact-support.component";
import { IosSpinnerComponent } from "./ios-spinner/ios-spinner.component";
import { LoadingOverlayComponent } from "./loading-overlay/loading-overlay.component";
import { ErrorPageComponent } from "./error-pages/error-page.component";
import { OfflineBannerComponent } from "./offline-banner/offline-banner.component";

const MATERIAL_MODULES = [
  MatToolbarModule,
  MatIconModule,
  MatButtonModule,
  MatBadgeModule,
  MatListModule,
  MatDividerModule,
  MatTooltipModule,
  MatMenuModule,
  MatProgressSpinnerModule,
  MatDialogModule,
  MatInputModule,
  MatFormFieldModule,
  MatSelectModule,
  MatSnackBarModule,
];

const SHARED_COMPONENTS = [
  NavbarComponent,
  SidebarComponent,
  ThemeToggleComponent,
  SkeletonComponent,
  SplashComponent,
  FloatingLinesComponent,
  FullscreenToastComponent,
  ConfirmationDialogComponent,
  NotificationPanelComponent,
  ChangePasswordComponent,
  ContactSupportComponent,
  ContactSupportDialogComponent,
  IosSpinnerComponent,
  LoadingOverlayComponent,
  ErrorPageComponent,
  OfflineBannerComponent,
];

@NgModule({
  declarations: SHARED_COMPONENTS,
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    ...MATERIAL_MODULES,
  ],
  exports: [
    ...SHARED_COMPONENTS,
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    ...MATERIAL_MODULES,
  ],
})
export class SharedModule {}

import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { RouterModule, Routes } from "@angular/router";
import { SharedModule } from "../../shared/shared.module";
import { MatCardModule } from "@angular/material/card";
import { MatTableModule } from "@angular/material/table";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatSelectModule } from "@angular/material/select";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatChipsModule } from "@angular/material/chips";
import { MatTooltipModule } from "@angular/material/tooltip";
import { MatTabsModule } from "@angular/material/tabs";
import { MatDividerModule } from "@angular/material/divider";
import { MatInputModule } from "@angular/material/input";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatRadioModule } from "@angular/material/radio";
import { MatDialogModule } from "@angular/material/dialog";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatButtonToggleModule } from "@angular/material/button-toggle";

import { DsiRgpdComponent } from "./dsi-rgpd/dsi-rgpd.component";
import { DsiAuditComponent } from "./dsi-audit/dsi-audit.component";
import { DsiConsentementsComponent } from "./dsi-consentements/dsi-consentements.component";
import { DsiMonitoringComponent } from "./dsi-monitoring/dsi-monitoring.component";
import { DsiConfigurationComponent } from "./dsi-configuration/dsi-configuration.component";
import { DsiViolationDialogComponent } from "./dsi-rgpd/dsi-violation-dialog.component";

const routes: Routes = [
  { path: "", redirectTo: "rgpd", pathMatch: "full" },
  { path: "rgpd", component: DsiRgpdComponent },
  { path: "audit", component: DsiAuditComponent },
  { path: "consentements", component: DsiConsentementsComponent },
  { path: "monitoring", component: DsiMonitoringComponent },
  { path: "configuration", component: DsiConfigurationComponent },
];

@NgModule({
  declarations: [
    DsiRgpdComponent, DsiAuditComponent, DsiConsentementsComponent,
    DsiMonitoringComponent, DsiConfigurationComponent, DsiViolationDialogComponent,
  ],
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, SharedModule,
    RouterModule.forChild(routes),
    MatCardModule, MatTableModule, MatPaginatorModule, MatProgressBarModule,
    MatSelectModule, MatFormFieldModule, MatButtonModule, MatIconModule,
    MatChipsModule, MatTooltipModule, MatTabsModule, MatDividerModule,
    MatInputModule, MatSlideToggleModule, MatCheckboxModule, MatRadioModule,
    MatDialogModule, MatSnackBarModule, MatButtonToggleModule,
  ],
})
export class DsiModule {}

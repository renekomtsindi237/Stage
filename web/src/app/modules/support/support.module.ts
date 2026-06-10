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
import { MatBadgeModule } from "@angular/material/badge";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatButtonToggleModule } from "@angular/material/button-toggle";
import { MatSnackBarModule } from "@angular/material/snack-bar";

import { SupOverviewComponent } from "./sup-overview/sup-overview.component";
import { SupInfrastructureComponent } from "./sup-infrastructure/sup-infrastructure.component";
import { SupTraitementsComponent } from "./sup-traitements/sup-traitements.component";
import { SupJournauxComponent } from "./sup-journaux/sup-journaux.component";
import { SupAlertesComponent } from "./sup-alertes/sup-alertes.component";

const routes: Routes = [
  { path: "", redirectTo: "overview", pathMatch: "full" },
  { path: "overview", component: SupOverviewComponent },
  { path: "infrastructure", component: SupInfrastructureComponent },
  { path: "traitements", component: SupTraitementsComponent },
  { path: "journaux", component: SupJournauxComponent },
  { path: "alertes", component: SupAlertesComponent },
];

@NgModule({
  declarations: [
    SupOverviewComponent,
    SupInfrastructureComponent,
    SupTraitementsComponent,
    SupJournauxComponent,
    SupAlertesComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    SharedModule,
    RouterModule.forChild(routes),
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatFormFieldModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatTabsModule,
    MatDividerModule,
    MatInputModule,
    MatBadgeModule,
    MatExpansionModule,
    MatButtonToggleModule,
    MatSnackBarModule,
  ],
})
export class SupportModule {}

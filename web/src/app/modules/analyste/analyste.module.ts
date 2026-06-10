import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
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
import {
  BaseChartDirective,
  provideCharts,
  withDefaultRegisterables,
} from "ng2-charts";

import { AnlDashboardComponent } from "./anl-dashboard/anl-dashboard.component";
import { AnlScoringComponent } from "./anl-scoring/anl-scoring.component";
import { AnlTraitementsComponent } from "./anl-traitements/anl-traitements.component";
import { AnlModeleComponent } from "./anl-modele/anl-modele.component";

const routes: Routes = [
  { path: "", redirectTo: "scoring", pathMatch: "full" },
  { path: "dashboard", component: AnlDashboardComponent },
  { path: "scoring", component: AnlScoringComponent },
  { path: "traitements", component: AnlTraitementsComponent },
  { path: "modele", component: AnlModeleComponent },
];

@NgModule({
  declarations: [
    AnlDashboardComponent,
    AnlScoringComponent,
    AnlTraitementsComponent,
    AnlModeleComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
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
    BaseChartDirective,
  ],
  providers: [provideCharts(withDefaultRegisterables())],
})
export class AnalysteModule {}

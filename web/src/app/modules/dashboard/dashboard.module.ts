import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { CommonModule } from "@angular/common";
import { SharedModule } from "../../shared/shared.module";

import { MatCardModule } from "@angular/material/card";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatBadgeModule } from "@angular/material/badge";
import { MatCheckboxModule } from "@angular/material/checkbox";

import { DashboardComponent } from "./dashboard.component";
import { ParChartComponent } from "./widgets/par-chart/par-chart.component";
import { CollecteChartComponent } from "./widgets/collecte-chart/collecte-chart.component";
import { PieChartComponent } from "./widgets/pie-chart/pie-chart.component";
import { DoughnutChartComponent } from "./widgets/doughnut-chart/doughnut-chart.component";
import { StackedBarChartComponent } from "./widgets/stacked-bar-chart/stacked-bar-chart.component";

const routes: Routes = [{ path: "", component: DashboardComponent }];

@NgModule({
  declarations: [
    DashboardComponent,
    ParChartComponent,
    CollecteChartComponent,
    PieChartComponent,
    DoughnutChartComponent,
    StackedBarChartComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    RouterModule.forChild(routes),
    MatCardModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatButtonModule,
    MatIconModule,
    MatBadgeModule,
    MatCheckboxModule,
  ],
})
export class DashboardModule {}

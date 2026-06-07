import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReactiveFormsModule } from "@angular/forms";
import { RouterModule, Routes } from "@angular/router";
import { SharedModule } from "../../shared/shared.module";

import { MatCardModule } from "@angular/material/card";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatTooltipModule } from "@angular/material/tooltip";
import { MatTableModule } from "@angular/material/table";
import { MatSortModule } from "@angular/material/sort";
import { MatPaginatorModule } from "@angular/material/paginator";

import { PlatformOverviewComponent } from "./platform-overview/platform-overview.component";
import { PlatformImfComponent } from "./platform-imf/platform-imf.component";
import { ImfSupervisionComponent } from "./imf-supervision/imf-supervision.component";
import { PlatformBarChartComponent } from "./platform-overview/imf-bar-chart.component";
import { PlatformDonutChartComponent } from "./platform-overview/imf-donut-chart.component";

const routes: Routes = [
  { path: "", component: PlatformOverviewComponent },
  { path: "imf", component: PlatformImfComponent },
  { path: "imf/:id/supervision", component: ImfSupervisionComponent },
];

@NgModule({
  declarations: [
    PlatformOverviewComponent,
    PlatformImfComponent,
    ImfSupervisionComponent,
    PlatformBarChartComponent,
    PlatformDonutChartComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    SharedModule,
    RouterModule.forChild(routes),
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
  ],
})
export class PlatformModule {}

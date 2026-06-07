import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';

import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PretsListComponent } from './prets-list/prets-list.component';
import { PretDetailComponent } from './pret-detail/pret-detail.component';

const routes: Routes = [
  { path: '', component: PretsListComponent },
  { path: ':id', component: PretDetailComponent },
];

@NgModule({
  declarations: [PretsListComponent, PretDetailComponent],
  imports: [
    CommonModule, FormsModule, SharedModule,
    RouterModule.forChild(routes),
    MatCardModule, MatTableModule, MatPaginatorModule,
    MatProgressBarModule, MatProgressSpinnerModule,
    MatSelectModule, MatFormFieldModule, MatButtonModule, MatIconModule,
  ]
})
export class PretsModule {}

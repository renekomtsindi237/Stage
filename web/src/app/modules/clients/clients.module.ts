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
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ClientsListComponent } from './clients-list/clients-list.component';
import { ClientDetailComponent } from './client-detail/client-detail.component';

const routes: Routes = [
  { path: '', component: ClientsListComponent },
  { path: ':id', component: ClientDetailComponent },
];

@NgModule({
  declarations: [ClientsListComponent, ClientDetailComponent],
  imports: [
    CommonModule, FormsModule, SharedModule,
    RouterModule.forChild(routes),
    MatCardModule, MatTableModule, MatPaginatorModule,
    MatProgressBarModule, MatProgressSpinnerModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
  ]
})
export class ClientsModule {}

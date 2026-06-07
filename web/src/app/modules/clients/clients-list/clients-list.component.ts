import { Component, OnInit } from '@angular/core';
import { debounceTime, distinctUntilChanged, Subject, switchMap } from 'rxjs';
import { PageEvent } from '@angular/material/paginator';
import { ClientService } from '../client.service';
import { ClientResponse } from '@core/models/client.model';

@Component({
  selector: 'imf-clients-list',
  templateUrl: './clients-list.component.html',
  styleUrls: ['./clients-list.component.scss']
})
export class ClientsListComponent implements OnInit {

  clients: ClientResponse[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  error = '';
  searchQuery = '';
  isSearchMode = false;

  private search$ = new Subject<string>();

  readonly displayedColumns = ['idClient', 'nomClient', 'telephoneClient', 'agence', 'actions'];

  constructor(private clientService: ClientService) {}

  ngOnInit(): void {
    this.loadClients();
    this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        this.loading = true;
        return this.clientService.search(q);
      })
    ).subscribe({
      next: (data) => { this.clients = data; this.total = data.length; this.loading = false; },
      error: () => { this.error = 'Recherche échouée.'; this.loading = false; }
    });
  }

  loadClients(): void {
    this.loading = true;
    this.clientService.list(this.page, this.pageSize).subscribe({
      next: (data) => {
        this.clients = data.content;
        this.total = data.total;
        this.loading = false;
      },
      error: () => { this.error = 'Impossible de charger les clients.'; this.loading = false; }
    });
  }

  onSearch(): void {
    if (this.searchQuery.trim().length >= 2) {
      this.isSearchMode = true;
      this.search$.next(this.searchQuery.trim());
    } else if (!this.searchQuery.trim()) {
      this.isSearchMode = false;
      this.loadClients();
    }
  }

  onPageChange(event: PageEvent): void {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    if (!this.isSearchMode) this.loadClients();
  }
}

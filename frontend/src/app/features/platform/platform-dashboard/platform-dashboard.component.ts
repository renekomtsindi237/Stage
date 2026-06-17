import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/http/api.service';
import { PlatformStats } from '../../../core/models/platform.model';
import { StatCardComponent } from '../../../shared/components/stat-card/stat-card.component';
import { FcfaPipe } from '../../../shared/pipes/fcfa.pipe';

@Component({
  selector: 'app-platform-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, StatCardComponent, FcfaPipe],
  templateUrl: './platform-dashboard.component.html',
  styleUrls: ['./platform-dashboard.component.scss']
})
export class PlatformDashboardComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = signal(true);
  data    = signal<PlatformStats | null>(null);

  ngOnInit() {
    this.api.get<PlatformStats>('/api/v1/platform/stats').subscribe({
      next: (d: PlatformStats) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }
}

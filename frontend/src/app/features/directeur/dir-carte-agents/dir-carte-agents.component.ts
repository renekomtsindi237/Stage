import { Component, inject, signal, OnInit, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../../core/http/api.service';

interface AgentPosition {
  agentUid: string;
  prenom: string;
  nom: string;
  latitude: number;
  longitude: number;
  lastSeen: string;
  statut: 'EN_COLLECTE' | 'DISPONIBLE' | 'HORS_LIGNE';
}

@Component({
  selector: 'app-dir-carte-agents',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './dir-carte-agents.component.html',
  styleUrls: ['./dir-carte-agents.component.scss']
})
export class DirCarteAgentsComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private interval: ReturnType<typeof setInterval> | null = null;

  loading  = signal(true);
  agents   = signal<AgentPosition[]>([]);
  selected = signal<AgentPosition | null>(null);

  ngOnInit() { this.load(); this.interval = setInterval(() => this.load(), 30_000); }
  ngOnDestroy() { if (this.interval) clearInterval(this.interval); }

  load() {
    this.api.get<AgentPosition[]>('/api/v1/agents/positions').subscribe({
      next: (list: AgentPosition[]) => { this.agents.set(list); this.loading.set(false); this.cdr.markForCheck(); },
      error: () => { this.loading.set(false); this.cdr.markForCheck(); }
    });
  }

  select(a: AgentPosition) { this.selected.set(a); }

  statusLabel(s: string) {
    return { 'EN_COLLECTE': 'En collecte', 'DISPONIBLE': 'Disponible', 'HORS_LIGNE': 'Hors ligne' }[s] ?? s;
  }
  statusClass(s: string) {
    return { 'EN_COLLECTE': 'badge-primary', 'DISPONIBLE': 'badge-success', 'HORS_LIGNE': 'badge-secondary' }[s] ?? '';
  }
}

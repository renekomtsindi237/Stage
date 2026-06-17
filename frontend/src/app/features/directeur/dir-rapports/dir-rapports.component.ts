import { Component, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

interface Rapport {
  id: string;
  titre: string;
  type: 'MENSUEL' | 'TRIMESTRIEL' | 'COBAC' | 'PERSONNALISE';
  dateDernier?: string;
}

@Component({
  selector: 'app-dir-rapports',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './dir-rapports.component.html',
  styleUrls: ['./dir-rapports.component.scss']
})
export class DirRapportsComponent {
  generating = signal<string | null>(null);

  rapports: Rapport[] = [
    { id: 'r1', titre: 'Rapport mensuel de performance',    type: 'MENSUEL',      dateDernier: '2026-05-31' },
    { id: 'r2', titre: 'Rapport trimestriel portefeuille', type: 'TRIMESTRIEL',  dateDernier: '2026-03-31' },
    { id: 'r3', titre: 'Rapport COBAC réglementaire',      type: 'COBAC',        dateDernier: '2026-05-31' },
    { id: 'r4', titre: 'Rapport KYC et conformité',        type: 'COBAC'         },
    { id: 'r5', titre: 'Rapport agents terrain',           type: 'PERSONNALISE'  },
  ];

  generate(r: Rapport) {
    this.generating.set(r.id);
    setTimeout(() => this.generating.set(null), 2000);
  }

  typeClass(t: string) {
    const m: Record<string, string> = {
      MENSUEL: 'badge-basse', TRIMESTRIEL: 'badge-moyenne',
      COBAC: 'badge-haute', PERSONNALISE: 'badge-muted'
    };
    return m[t] ?? '';
  }
}

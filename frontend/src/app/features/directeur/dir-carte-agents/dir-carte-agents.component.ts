import {
  Component,
  inject,
  signal,
  AfterViewInit,
  OnDestroy,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  ElementRef,
  ViewChild,
  effect,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { TranslatePipe } from "@ngx-translate/core";
import * as L from "leaflet";
import { Subscription } from "rxjs";
import { filter } from "rxjs/operators";
import { ApiService } from "../../../core/http/api.service";
import { ThemeService } from "../../../core/services/theme.service";
import { SseService } from "../../../core/services/sse.service";

interface AgentPositionResponse {
  agentUid: string;
  username: string;
  nomComplet: string;
  nomAgence: string | null;
  villeAgence: string | null;
  latitude: number;
  longitude: number;
  precisionMetres: number | null;
  altitudeMetres: number | null;
  vitesseKmh: number | null;
  enDeplacement: boolean;
  source: string;
  capturedAt: string | null;
}

// ── Tiles OpenStreetMap (light) et CartoDB Dark Matter ───────────────────────
const TILES_LIGHT = {
  url: "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
  attribution:
    '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
};
const TILES_DARK = {
  url: "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
  attribution:
    '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors © <a href="https://carto.com/attributions">CARTO</a>',
};

// ── Cameroun — bounds et centre ──────────────────────────────────────────────
const CAMEROUN_CENTER: L.LatLngExpression = [5.5, 12.3];
const CAMEROUN_BOUNDS: L.LatLngBoundsExpression = [
  [1.65, 8.3],   // SW — Kribi / Ebolowa
  [13.1, 16.2],  // NE — Maroua / Ngaoundéré
];

// ── Icônes agents ─────────────────────────────────────────────────────────────
function makeIcon(color: string, size: number): L.DivIcon {
  return L.divIcon({
    className: "",
    html: `<div style="
      width:${size}px;height:${size}px;border-radius:50%;
      background:${color};border:3px solid #fff;
      box-shadow:0 2px 8px rgba(0,0,0,.4);
      transition:transform .15s;"></div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -(size / 2) - 4],
  });
}
const ICON_ACTIVE   = makeIcon("#22c55e", 18);  // vert — en déplacement
const ICON_INACTIVE = makeIcon("#94a3b8", 14);  // gris — dernière position connue

@Component({
  selector: "app-dir-carte-agents",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, TranslatePipe],
  templateUrl: "./dir-carte-agents.component.html",
  styleUrls: ["./dir-carte-agents.component.scss"],
})
export class DirCarteAgentsComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly sse = inject(SseService);
  readonly theme = inject(ThemeService);

  @ViewChild("mapContainer") mapContainer!: ElementRef<HTMLDivElement>;

  private map: L.Map | null = null;
  private tileLayer: L.TileLayer | null = null;
  private markers = new Map<string, L.Marker>();
  private refreshInterval: ReturnType<typeof setInterval> | null = null;
  private sseSub?: Subscription;

  loading = signal(true);
  activeAgents   = signal<AgentPositionResponse[]>([]);
  allAgents      = signal<AgentPositionResponse[]>([]);
  selected       = signal<AgentPositionResponse | null>(null);
  error          = signal("");
  showAllPositions = signal(true);

  get displayedAgents(): AgentPositionResponse[] {
    return this.showAllPositions() ? this.allAgents() : this.activeAgents();
  }

  ngOnInit() {
    // Réagir aux changements de thème (swap des tiles)
    effect(() => {
      const dark = this.theme.isDark();
      this.swapTiles(dark);
    });
  }

  ngAfterViewInit() {
    this.initMap();
    this.loadBoth();
    this.refreshInterval = setInterval(() => this.loadBoth(), 30_000);

    // Rafraîchissement immédiat via SSE (temps réel)
    this.sseSub = this.sse.events$
      .pipe(filter((e) => e.type === "AGENT_POSITION_UPDATED"))
      .subscribe(() => this.loadBoth());
  }

  ngOnDestroy() {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
    this.sseSub?.unsubscribe();
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  // ── Initialisation carte ──────────────────────────────────────────────────

  private initMap() {
    if (!this.mapContainer?.nativeElement) return;

    this.map = L.map(this.mapContainer.nativeElement, {
      center: CAMEROUN_CENTER,
      zoom: 6,
      zoomControl: true,
      maxBounds: L.latLngBounds(
        [0.5, 7.0],
        [14.0, 17.5],
      ),
      maxBoundsViscosity: 0.7,
    });

    // Tile initial selon thème courant
    const tiles = this.theme.isDark() ? TILES_DARK : TILES_LIGHT;
    this.tileLayer = L.tileLayer(tiles.url, {
      attribution: tiles.attribution,
      maxZoom: 19,
      subdomains: ["a", "b", "c"],
    }).addTo(this.map);

    // Zoom sur le Cameroun en entier
    this.map.fitBounds(CAMEROUN_BOUNDS, { padding: [20, 20] });
  }

  private swapTiles(dark: boolean) {
    if (!this.map) return;
    const cfg = dark ? TILES_DARK : TILES_LIGHT;
    if (this.tileLayer) {
      this.tileLayer.remove();
    }
    this.tileLayer = L.tileLayer(cfg.url, {
      attribution: cfg.attribution,
      maxZoom: 19,
      subdomains: ["a", "b", "c"],
    }).addTo(this.map);
  }

  // ── Chargement données ─────────────────────────────────────────────────────

  loadBoth() {
    // 1) Agents actifs (derniers 15 min)
    this.api
      .get<AgentPositionResponse[]>("/api/v1/agents/positions")
      .subscribe({
        next: (list) => {
          this.activeAgents.set(list ?? []);
          this.loading.set(false);
          this.updateMarkers(this.allAgents(), list ?? []);
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading.set(false);
          this.error.set("Impossible de charger les positions actives.");
          this.cdr.markForCheck();
        },
      });

    // 2) Tous les agents ayant une position connue
    this.api
      .get<AgentPositionResponse[]>("/api/v1/agents/positions/toutes")
      .subscribe({
        next: (list) => {
          this.allAgents.set(list ?? []);
          this.updateMarkers(list ?? [], this.activeAgents());
          this.cdr.markForCheck();
        },
        error: () => {},
      });
  }

  load() {
    this.loadBoth();
  }

  // ── Gestion des marqueurs ─────────────────────────────────────────────────

  private updateMarkers(
    all: AgentPositionResponse[],
    active: AgentPositionResponse[],
  ) {
    if (!this.map) return;

    const activeUids = new Set(active.map((a) => a.agentUid));
    const seen = new Set<string>();

    const list = this.showAllPositions() ? all : active;

    for (const agent of list) {
      if (!agent.latitude || !agent.longitude) continue;
      seen.add(agent.agentUid);

      const isActive = activeUids.has(agent.agentUid);
      const icon = isActive ? ICON_ACTIVE : ICON_INACTIVE;
      const latLng: L.LatLngExpression = [agent.latitude, agent.longitude];

      const popup = this.buildPopup(agent, isActive);

      const existing = this.markers.get(agent.agentUid);
      if (existing) {
        existing.setLatLng(latLng).setIcon(icon).setPopupContent(popup);
      } else {
        const marker = L.marker(latLng, { icon })
          .addTo(this.map!)
          .bindPopup(popup, { maxWidth: 260 });

        marker.on("click", () => {
          this.selected.set(agent);
          this.cdr.markForCheck();
        });
        this.markers.set(agent.agentUid, marker);
      }
    }

    // Retirer les marqueurs disparus
    for (const [uid, marker] of this.markers) {
      if (!seen.has(uid)) {
        marker.remove();
        this.markers.delete(uid);
      }
    }
  }

  private buildPopup(agent: AgentPositionResponse, isActive: boolean): string {
    const agenceInfo = agent.nomAgence
      ? `<div style="font-size:11px;color:#64748b;margin-top:2px">${agent.nomAgence}${agent.villeAgence ? " — " + agent.villeAgence : ""}</div>`
      : "";
    const lastSeen = agent.capturedAt
      ? new Date(agent.capturedAt).toLocaleString("fr-CM", {
          day: "2-digit",
          month: "short",
          hour: "2-digit",
          minute: "2-digit",
        })
      : "—";
    const statusDot = isActive
      ? `<span style="color:#22c55e">● En déplacement</span>`
      : `<span style="color:#94a3b8">● Arrêté</span>`;
    const vitesse =
      agent.vitesseKmh != null
        ? `<div style="font-size:11px;color:#64748b">${agent.vitesseKmh.toFixed(1)} km/h</div>`
        : "";
    const precision =
      agent.precisionMetres != null
        ? `<div style="font-size:11px;color:#64748b">Précision : ±${Math.round(agent.precisionMetres)} m</div>`
        : "";

    return `
      <div style="min-width:180px;font-family:Inter,sans-serif;padding:4px 0">
        <strong style="font-size:13px">${agent.nomComplet || agent.username}</strong>
        ${agenceInfo}
        <div style="margin-top:6px;font-size:12px">${statusDot}</div>
        ${vitesse}${precision}
        <div style="font-size:11px;color:#94a3b8;margin-top:4px">
          Dernière position : ${lastSeen}
        </div>
        <div style="margin-top:8px">
          <a href="https://maps.google.com/?q=${agent.latitude},${agent.longitude}"
             target="_blank" rel="noopener"
             style="font-size:11px;color:#3b82f6;text-decoration:none">
            ↗ Ouvrir dans Google Maps
          </a>
        </div>
      </div>`;
  }

  // ── Actions UI ────────────────────────────────────────────────────────────

  select(agent: AgentPositionResponse) {
    this.selected.set(agent);
    const marker = this.markers.get(agent.agentUid);
    if (marker && this.map) {
      this.map.setView([agent.latitude, agent.longitude], 14, { animate: true });
      marker.openPopup();
    }
  }

  fitCameroun() {
    this.map?.fitBounds(CAMEROUN_BOUNDS, { padding: [20, 20], animate: true });
  }

  fitAll() {
    const agents = this.displayedAgents.filter(
      (a) => a.latitude && a.longitude,
    );
    if (!this.map || agents.length === 0) {
      this.fitCameroun();
      return;
    }
    const bounds = L.latLngBounds(
      agents.map((a) => [a.latitude, a.longitude] as L.LatLngExpression),
    );
    this.map.fitBounds(bounds, { padding: [40, 40], animate: true });
  }

  toggleShowAll() {
    this.showAllPositions.update((v) => !v);
    this.updateMarkers(this.allAgents(), this.activeAgents());
  }

  statusLabel(a: AgentPositionResponse): string {
    return a.enDeplacement ? "En déplacement" : "Dernière position";
  }

  statusClass(a: AgentPositionResponse): string {
    return a.enDeplacement ? "badge-success" : "badge-secondary";
  }

  initials(a: AgentPositionResponse): string {
    const name = a.nomComplet || a.username || "?";
    return name
      .split(" ")
      .slice(0, 2)
      .map((p) => p[0])
      .join("")
      .toUpperCase();
  }

  formatTime(dateStr: string | null): string {
    if (!dateStr) return "—";
    return new Date(dateStr).toLocaleString("fr-CM", {
      day: "2-digit",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    });
  }
}

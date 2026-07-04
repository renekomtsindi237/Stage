import {
  Component,
  inject,
  signal,
  OnInit,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule, AsyncPipe } from "@angular/common";
import { RouterLink } from "@angular/router";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { AuthService } from "../../../core/auth/auth.service";
import { NotificationService } from "../../../core/services/notification.service";
import { ThemeService } from "../../../core/services/theme.service";
import { NotificationPanelComponent } from "../notification-panel/notification-panel.component";
import { ToastService } from "../../../core/services/toast.service";
import { ApiService } from "../../../core/http/api.service";
import { Observable } from "rxjs";
import { environment } from "../../../../environments/environment";
import { TranslatePipe } from "@ngx-translate/core";
import { LanguageService } from "../../../core/services/language.service";
import { LanguageSelectorComponent } from "../language-selector/language-selector.component";

@Component({
  selector: "app-topbar",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    AsyncPipe,
    RouterLink,
    NotificationPanelComponent,
    ReactiveFormsModule,
    TranslatePipe,
    LanguageSelectorComponent,
  ],
  templateUrl: "./topbar.component.html",
  styleUrls: ["./topbar.component.scss"],
})
export class TopbarComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  readonly langSvc = inject(LanguageService);
  private readonly toast = inject(ToastService);
  private readonly notifSvc = inject(NotificationService);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  showUserMenu = signal(false);
  showNotifPanel = signal(false);
  showSupportModal = signal(false);
  sendingTicket = signal(false);

  get avatarSrc(): string {
    const url = this.auth.avatarUrl();
    if (!url) return "assets/profile.png";
    if (url.startsWith("http")) return url;
    return `${environment.apiUrl}${url}`;
  }

  unreadCount$!: Observable<number>;

  supportForm = this.fb.group({
    titre: [
      "",
      [Validators.required, Validators.minLength(5), Validators.maxLength(120)],
    ],
    categorie: ["TECHNIQUE", Validators.required],
    priorite: ["NORMALE", Validators.required],
    description: [
      "",
      [
        Validators.required,
        Validators.minLength(20),
        Validators.maxLength(2000),
      ],
    ],
  });

  readonly categorieOptions = [
    { value: "TECHNIQUE", label: "topbar.support_modal.cat_technique" },
    { value: "FACTURATION", label: "topbar.support_modal.cat_facturation" },
    { value: "AUTRE", label: "topbar.support_modal.cat_autre" },
  ];

  readonly prioriteOptions = [
    { value: "NORMALE", label: "topbar.support_modal.prio_normale" },
    { value: "HAUTE", label: "topbar.support_modal.prio_haute" },
    { value: "URGENTE", label: "topbar.support_modal.prio_urgente" },
  ];

  ngOnInit() {
    this.unreadCount$ = this.notifSvc.unreadCount$;
  }

  get today(): string {
    return new Date().toLocaleDateString("fr-FR", {
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric",
    });
  }

  get roleLabel(): string {
    const map: Record<string, string> = {
      AGENT: "Agent Terrain",
      AGENT_CREDIT: "Agent Crédit",
      AGENT_SAISIE: "Agent Saisie",
      ANALYSTE: "Analyste ML",
      ANALYSTE_ENGAGEMENTS: "Analyste Engagements",
      CHEF_AGENCE: "Chef d'Agence",
      CAISSIER: "Caissier",
      DIRECTEUR: "Directeur",
      DSI: "DSI",
      RESPONSABLE_RECOUVREMENT: "Responsable Recouvrement",
      SUPER_ADMIN: "Super Admin",
      SUPPORT: "Support",
    };
    return map[this.auth.role() ?? ""] ?? this.auth.role() ?? "";
  }

  toggleMenu() {
    this.showUserMenu.update((v: boolean) => !v);
  }

  toggleNotif() {
    this.showNotifPanel.update((v: boolean) => !v);
    this.showUserMenu.set(false);
  }

  closeNotif() {
    this.showNotifPanel.set(false);
  }

  openSupportModal() {
    this.supportForm.reset({
      categorie: "TECHNIQUE",
      priorite: "NORMALE",
    });
    this.showSupportModal.set(true);
    this.showUserMenu.set(false);
  }

  closeSupportModal() {
    this.showSupportModal.set(false);
  }

  submitSupport() {
    if (this.supportForm.invalid) {
      this.supportForm.markAllAsTouched();
      return;
    }
    this.sendingTicket.set(true);
    this.api
      .post<{
        success: boolean;
        data: unknown;
      }>("/api/v1/tickets", this.supportForm.value)
      .subscribe({
        next: () => {
          this.sendingTicket.set(false);
          this.showSupportModal.set(false);
          this.toast.showSuccess(
            "Ticket envoyé",
            "Votre demande a été transmise à l'équipe support.",
          );
        },
        error: () => {
          this.sendingTicket.set(false);
          this.toast.showError(
            "Erreur",
            "Impossible d'envoyer le ticket. Réessayez.",
          );
        },
      });
  }

  logout() {
    this.showUserMenu.set(false);
    const name = this.auth.fullName();
    this.auth.logout();
    this.toast.showLogout(name);
  }
}

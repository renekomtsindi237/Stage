import {
  Component,
  inject,
  signal,
  ElementRef,
  ViewChild,
  AfterViewChecked,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../../../core/http/api.service";
import { AuthService } from "../../../core/auth/auth.service";
import { ChatMarkdownPipe } from "../../pipes/chat-markdown.pipe";
import { TranslatePipe } from "@ngx-translate/core";

interface Message {
  role: "user" | "assistant";
  content: string;
  ts: Date;
  error?: boolean;
  isWelcome?: boolean;
}

const SUGGESTIONS: Record<string, string[]> = {
  default: [
    "Expliquez le ratio PAR30 en microfinance",
    "Comment interpréter un score MCRS ?",
    "Quelles sont les classes COBAC A à E ?",
    "Quels indicateurs surveiller pour le recouvrement ?",
  ],
  SUPPORT: [
    "Combien de tickets sont ouverts en ce moment ?",
    "Comment diagnostiquer un container Docker en échec ?",
    "Que faire si un DAG Airflow échoue répétitivement ?",
    "Comment analyser les logs d'erreur Spring Boot ?",
  ],
  DIRECTEUR: [
    "Analysez mes KPIs de recouvrement actuels",
    "Comment améliorer mon taux de recouvrement ?",
    "Quel est mon PAR30 et que signifie-t-il ?",
    "Seuils réglementaires BEAC à respecter",
  ],
  ANALYSTE: [
    "Comment fonctionne le scoring MCRS de mes clients ?",
    "Qu'est-ce que le PSI et pourquoi surveiller la dérive ?",
    "Interpréter la distribution de risque de mon portefeuille",
    "Quels clients sont en classe de risque élevé ?",
  ],
  RESPONSABLE_RECOUVREMENT: [
    "Quelles alertes sont actives en ce moment ?",
    "Quels clients ont les PAR les plus élevés ?",
    "Comment prioriser les actions de recouvrement ?",
    "Interpréter mes indicateurs de performance terrain",
  ],
};

@Component({
  selector: "app-chatbot",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ChatMarkdownPipe, TranslatePipe],
  templateUrl: "./chatbot.component.html",
  styleUrls: ["./chatbot.component.scss"],
})
export class ChatbotComponent implements AfterViewChecked {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild("messagesEnd") private messagesEnd!: ElementRef;

  open = signal(false);
  messages = signal<Message[]>([]);
  input = signal("");
  loading = signal(false);
  showSuggestions = signal(true);

  get suggestions(): string[] {
    const role = this.auth.role() ?? "default";
    return SUGGESTIONS[role] ?? SUGGESTIONS["default"];
  }

  get welcomeContent(): string {
    const role = this.auth.role();
    if (role === "SUPPORT") {
      return "Bonjour ! Je suis l'assistant IA de MicroRecouv.\n\nEn tant que **support technique**, je peux vous aider avec :\n- **Monitoring** (containers, DAGs, alertes système)\n- **Diagnostic** des erreurs et logs Spring Boot\n- **Tickets** et gestion des demandes utilisateurs\n- **Infrastructure** PostgreSQL, Docker, Airflow\n\nLes données de votre plateforme sont chargées en contexte. Comment puis-je vous aider ?";
    }
    if (role === "DIRECTEUR") {
      return "Bonjour ! Je suis l'assistant IA de MicroRecouv.\n\nJe consulte en temps réel vos **données réelles** pour vous aider avec :\n- **KPIs** (PAR30/PAR60/PAR90, taux de recouvrement)\n- **Scores MCRS** de votre portefeuille clients\n- **Réglementation** COBAC/BEAC/CEMAC\n- **Recommandations** basées sur vos chiffres actuels\n\nComment puis-je vous aider ?";
    }
    if (role === "ANALYSTE") {
      return "Bonjour ! Je suis l'assistant IA de MicroRecouv.\n\nJ'accède à vos **données ML en temps réel** pour vous aider avec :\n- **Scoring MCRS** et distribution du risque\n- **Drift PSI** et dérive des modèles\n- **Segmentation** clients par classe de risque\n- **Interprétation** des alertes prédictives\n\nComment puis-je vous aider ?";
    }
    return "Bonjour ! Je suis l'assistant IA de MicroRecouv.\n\nJe peux vous aider avec :\n- **KPIs & indicateurs** (PAR30, taux de recouvrement, MCRS)\n- **Réglementation** COBAC/BEAC en contexte CEMAC\n- **Analyse financière** et gestion du risque\n- **Questions techniques** sur la plateforme\n\nComment puis-je vous aider ?";
  }

  get welcomeMsg(): Message {
    return {
      role: "assistant",
      content: this.welcomeContent,
      ts: new Date(),
      isWelcome: true,
    };
  }

  toggle() {
    this.open.update((v) => !v);
    if (this.open() && this.messages().length === 0) {
      this.messages.set([this.welcomeMsg]);
      this.showSuggestions.set(true);
    }
  }

  useSuggestion(text: string) {
    this.input.set(text);
    this.showSuggestions.set(false);
    this.send();
  }

  send() {
    const text = this.input().trim();
    if (!text || this.loading()) return;

    this.showSuggestions.set(false);
    const userMsg: Message = { role: "user", content: text, ts: new Date() };
    this.messages.update((m) => [...m, userMsg]);
    this.input.set("");
    this.loading.set(true);

    // Send only user/assistant messages, skip welcome and error messages
    const history = this.messages()
      .filter((m) => !m.isWelcome && !m.error)
      .map((m) => ({ role: m.role, content: m.content }));

    this.api.post<string>("/api/v1/ai/chat", { messages: history }).subscribe({
      next: (r) => {
        const content =
          typeof r === "string" && r
            ? r
            : typeof r === "object" && r !== null && "data" in (r as object)
              ? ((r as Record<string, unknown>)["data"] as string)
              : "Je n'ai pas pu obtenir de réponse. Réessayez.";

        const reply: Message = {
          role: "assistant",
          content,
          ts: new Date(),
        };
        this.messages.update((m) => [...m, reply]);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        const errMsg: Message = {
          role: "assistant",
          content:
            "Impossible de contacter l'assistant. Vérifiez votre connexion réseau et réessayez.",
          ts: new Date(),
          error: true,
        };
        this.messages.update((m) => [...m, errMsg]);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
    });
  }

  retry() {
    // Remove last error message and resend last user message
    const msgs = this.messages();
    const lastUser = [...msgs].reverse().find((m) => m.role === "user");
    if (!lastUser) return;
    this.messages.update((m) => m.filter((x) => !x.error));
    this.input.set(lastUser.content);
    this.send();
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  clear() {
    this.messages.set([this.welcomeMsg]);
    this.showSuggestions.set(true);
  }

  ngAfterViewChecked() {
    try {
      this.messagesEnd?.nativeElement?.scrollIntoView({ behavior: "smooth" });
    } catch {}
  }
}

import {
  Component,
  inject,
  signal,
  ElementRef,
  ViewChild,
  AfterViewChecked,
  ChangeDetectionStrategy,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../../../core/http/api.service";
import { ChatMarkdownPipe } from "../../pipes/chat-markdown.pipe";

interface Message {
  role: "user" | "assistant";
  content: string;
  ts: Date;
}

interface AiResponse {
  data: string;
}

@Component({
  selector: "app-chatbot",
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, ChatMarkdownPipe],
  templateUrl: "./chatbot.component.html",
  styleUrls: ["./chatbot.component.scss"],
})
export class ChatbotComponent implements AfterViewChecked {
  private readonly api = inject(ApiService);

  @ViewChild("messagesEnd") private messagesEnd!: ElementRef;

  open      = signal(false);
  messages  = signal<Message[]>([]);
  input     = signal("");
  loading   = signal(false);

  readonly welcomeMsg: Message = {
    role: "assistant",
    content:
      "Bonjour ! Je suis l'assistant IA de MicroRecouv. Je peux vous aider à interpréter vos KPIs, comprendre les ratios COBAC, analyser vos scores MCRS ou répondre à vos questions sur la microfinance. Comment puis-je vous aider ?",
    ts: new Date(),
  };

  toggle() {
    this.open.update((v) => !v);
    if (this.open() && this.messages().length === 0) {
      this.messages.set([this.welcomeMsg]);
    }
  }

  send() {
    const text = this.input().trim();
    if (!text || this.loading()) return;

    const userMsg: Message = { role: "user", content: text, ts: new Date() };
    this.messages.update((m) => [...m, userMsg]);
    this.input.set("");
    this.loading.set(true);

    this.api
      .post<AiResponse>("/api/v1/ai/chat", {
        messages: this.messages()
          .filter((m) => m.role !== "assistant" || m !== this.welcomeMsg)
          .map((m) => ({ role: m.role, content: m.content })),
      })
      .subscribe({
        next: (r) => {
          const reply: Message = {
            role: "assistant",
            content: r.data ?? (r as unknown as string),
            ts: new Date(),
          };
          this.messages.update((m) => [...m, reply]);
          this.loading.set(false);
        },
        error: () => {
          const errMsg: Message = {
            role: "assistant",
            content:
              "Désolé, une erreur est survenue. Vérifiez votre connexion et réessayez.",
            ts: new Date(),
          };
          this.messages.update((m) => [...m, errMsg]);
          this.loading.set(false);
        },
      });
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  clear() {
    this.messages.set([this.welcomeMsg]);
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  private scrollToBottom() {
    try {
      this.messagesEnd?.nativeElement?.scrollIntoView({ behavior: "smooth" });
    } catch {}
  }
}

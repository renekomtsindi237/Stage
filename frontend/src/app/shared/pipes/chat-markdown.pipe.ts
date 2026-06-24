import { Pipe, PipeTransform } from "@angular/core";
import { DomSanitizer, SafeHtml } from "@angular/platform-browser";

@Pipe({ name: "chatMarkdown", standalone: true, pure: true })
export class ChatMarkdownPipe implements PipeTransform {
  constructor(private readonly san: DomSanitizer) {}

  transform(value: unknown): SafeHtml {
    const str = typeof value === "string" ? value : String(value ?? "");
    if (!str) return "";
    let html = str
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
      .replace(/\*(.+?)\*/g, "<em>$1</em>")
      .replace(/`(.+?)`/g, "<code>$1</code>")
      .replace(/^- (.+)$/gm, "<li>$1</li>")
      .replace(/(<li>.*<\/li>)/s, "<ul>$1</ul>")
      .replace(/\n\n/g, "</p><p>")
      .replace(/\n/g, "<br>");
    html = `<p>${html}</p>`;
    return this.san.bypassSecurityTrustHtml(html);
  }
}

import { Pipe, PipeTransform } from "@angular/core";

@Pipe({ name: "timeAgo", standalone: true })
export class TimeAgoPipe implements PipeTransform {
  transform(value: string | Date | null): string {
    if (!value) return "—";
    const date = new Date(value);
    const diff = (Date.now() - date.getTime()) / 1000;
    if (diff < 60) return "À l'instant";
    if (diff < 3600) return `Il y a ${Math.floor(diff / 60)}min`;
    if (diff < 86400) return `Il y a ${Math.floor(diff / 3600)}h`;
    if (diff < 604800) return `Il y a ${Math.floor(diff / 86400)}j`;
    return date.toLocaleDateString("fr-FR");
  }
}

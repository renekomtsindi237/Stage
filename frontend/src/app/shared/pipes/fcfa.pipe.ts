import { Pipe, PipeTransform } from "@angular/core";

@Pipe({ name: "fcfa", standalone: true })
export class FcfaPipe implements PipeTransform {
  transform(value: number | null | undefined, showUnit = true): string {
    if (value == null) return "—";
    const formatted = new Intl.NumberFormat("fr-FR").format(Math.round(value));
    return showUnit ? `${formatted} FCFA` : formatted;
  }
}

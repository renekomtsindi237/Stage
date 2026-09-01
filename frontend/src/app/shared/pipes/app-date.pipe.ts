import { DatePipe } from "@angular/common";
import { Pipe, PipeTransform, inject, LOCALE_ID } from "@angular/core";

@Pipe({ name: "appDate", standalone: true })
export class AppDatePipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);
  private readonly datePipe = new DatePipe(this.locale);

  transform(
    value: string | Date | number | null | undefined,
    format = "dd/MM/yyyy HH:mm",
  ): string {
    if (value == null || value === "") return "—";
    const formatted = this.datePipe.transform(value, format);
    return formatted ?? "—";
  }
}

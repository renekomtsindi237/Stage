import { HttpErrorResponse } from "@angular/common/http";

/** Message métier renvoyé par l'enveloppe ApiResponse, sinon libellé de repli. */
export function apiErrorMessage(
  err: unknown,
  fallback = "Une erreur est survenue.",
): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error;
    if (typeof body === "string" && body.trim()) {
      try {
        const parsed = JSON.parse(body) as { message?: string };
        if (parsed?.message?.trim()) return parsed.message.trim();
      } catch {
        return body.trim();
      }
    }
    if (body && typeof body === "object") {
      const msg = (body as { message?: unknown }).message;
      if (typeof msg === "string" && msg.trim()) return msg.trim();
    }
  }
  if (err instanceof Error && err.message.trim()) {
    return err.message.trim();
  }
  return fallback;
}

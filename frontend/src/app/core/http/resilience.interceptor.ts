import { HttpErrorResponse, HttpInterceptorFn } from "@angular/common/http";
import { retry, throwError, timer, timeout } from "rxjs";

const HTTP_TIMEOUT_MS = 90_000;
const MAX_RETRIES = 2;
const RETRYABLE_STATUS = new Set([0, 408, 429, 502, 503, 504]);
const IDEMPOTENT = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * Timeouts longs + retry borné sur les GET, pour les liaisons intermittentes.
 * Les POST (OTP, collectes) ne sont pas relancés afin d'éviter les doublons.
 */
export const resilienceInterceptor: HttpInterceptorFn = (req, next) => {
  const maxRetries = IDEMPOTENT.has(req.method.toUpperCase()) ? MAX_RETRIES : 0;
  return next(req).pipe(
    timeout(HTTP_TIMEOUT_MS),
    retry({
      count: maxRetries,
      delay: (err: unknown, retryCount: number) => {
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        const timedOut =
          !!err &&
          typeof err === "object" &&
          "name" in err &&
          (err as { name: string }).name === "TimeoutError";
        if (!timedOut && !RETRYABLE_STATUS.has(status)) {
          return throwError(() => err);
        }
        return timer(Math.min(800 * 2 ** (retryCount - 1), 8_000));
      },
    }),
  );
};

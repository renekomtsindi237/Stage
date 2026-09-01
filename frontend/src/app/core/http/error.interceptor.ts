import { HttpInterceptorFn, HttpErrorResponse } from "@angular/common/http";
import { inject } from "@angular/core";
import { catchError, throwError } from "rxjs";
import { AuthService } from "../auth/auth.service";

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const url = req.url ?? "";
      const skipLogout =
        url.includes("/users/me/avatar") ||
        url.includes("/public/") ||
        url.includes("/auth/") ||
        url.includes("/sse/");
      if (err.status === 401 && !skipLogout) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};

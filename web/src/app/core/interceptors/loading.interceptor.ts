import { Injectable } from "@angular/core";
import {
  HttpInterceptor, HttpRequest, HttpHandler, HttpEvent,
} from "@angular/common/http";
import { Observable } from "rxjs";
import { finalize } from "rxjs/operators";
import { LoadingService } from "@core/services/loading.service";

const SKIP_URLS = ["/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/sse"];
const MUTATION_METHODS = ["POST", "PUT", "DELETE", "PATCH"];

@Injectable()
export class LoadingInterceptor implements HttpInterceptor {
  constructor(private loading: LoadingService) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const skip = SKIP_URLS.some((u) => req.url.includes(u));
    const isMutation = MUTATION_METHODS.includes(req.method);

    if (skip || !isMutation) return next.handle(req);

    this.loading.show();
    return next.handle(req).pipe(finalize(() => this.loading.hide()));
  }
}

import { inject } from "@angular/core";
import { CanActivateFn, Router, ActivatedRouteSnapshot } from "@angular/router";
import { AuthService } from "./auth.service";
import { Role } from "../models/user.model";

export const roleGuard =
  (requiredRoles: Role[]): CanActivateFn =>
  (route: ActivatedRouteSnapshot) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const roles: Role[] = route.data["roles"] ?? requiredRoles;
    if (auth.hasRole(...roles)) return true;
    return router.createUrlTree(["/error/403"]);
  };

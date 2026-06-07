import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate {

  constructor(private authService: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): boolean | UrlTree {
    const allowedRoles: Role[] = route.data['roles'] ?? [];
    const userRole = this.authService.getRole();

    if (userRole && allowedRoles.includes(userRole)) return true;

    // SUPER_ADMIN redirigé vers /platform, les autres vers /dashboard
    const fallback = userRole === 'SUPER_ADMIN' ? '/platform' : '/dashboard';
    return this.router.createUrlTree([fallback], {
      queryParams: { error: 'access_denied' }
    });
  }
}

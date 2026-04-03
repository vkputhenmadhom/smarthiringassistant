import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { map, take, tap } from 'rxjs/operators';
import { selectUserRole } from '../../store/auth/auth.selectors';
import { Role } from '../../shared/models';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const store   = inject(Store);
  const router  = inject(Router);
  const allowed = route.data['roles'] as Role[];

  return store.select(selectUserRole).pipe(
    take(1),
    tap(role => {
      console.log(`[roleGuard] route=${route.routeConfig?.path} allowed=${JSON.stringify(allowed)} userRole=${role}`);
    }),
    map(role => {
      const isAllowed = role && allowed.includes(role);
      console.log(`[roleGuard] decision=${isAllowed ? 'ALLOW' : 'DENY'} redirecting to /app/dashboard`);
      return isAllowed || router.createUrlTree(['/app/dashboard']);
    })
  );
};

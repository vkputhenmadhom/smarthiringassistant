import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { map, take } from 'rxjs/operators';
import { selectUserRole } from '../../store/auth/auth.selectors';
import { Role } from '../../shared/models';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const store   = inject(Store);
  const router  = inject(Router);
  const allowed = route.data['roles'] as Role[];
  return store.select(selectUserRole).pipe(
    take(1),
    map(role => (role && allowed.includes(role)) || router.createUrlTree(['/app/dashboard']))
  );
};


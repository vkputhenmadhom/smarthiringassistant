import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { combineLatest } from 'rxjs';
import { map, take, tap } from 'rxjs/operators';
import { selectIsLoggedIn, selectIsTokenExpired } from '../../store/auth/auth.selectors';
import * as AuthActions from '../../store/auth/auth.actions';

export const authGuard: CanActivateFn = () => {
  const store  = inject(Store);
  const router = inject(Router);

  return combineLatest([
    store.select(selectIsLoggedIn),
    store.select(selectIsTokenExpired),
  ]).pipe(
    take(1),
    tap(([loggedIn, expired]) => {
      console.log(`[authGuard] loggedIn=${loggedIn} expired=${expired}`);
    }),
    map(([loggedIn, expired]) => {
      if (loggedIn && expired) {
        console.log(`[authGuard] token expired – dispatching logout`);
        store.dispatch(AuthActions.logout());
        return router.createUrlTree(['/auth/login']);
      }
      const result = loggedIn || router.createUrlTree(['/auth/login']);
      console.log(`[authGuard] result=${loggedIn ? 'ALLOW' : 'DENY'}`);
      return result;
    })
  );
};

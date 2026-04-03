import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { combineLatest } from 'rxjs';
import { map, take } from 'rxjs/operators';
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
    map(([loggedIn, expired]) => {
      if (loggedIn && expired) {
        store.dispatch(AuthActions.logout());
        return router.createUrlTree(['/auth/login']);
      }
      return loggedIn || router.createUrlTree(['/auth/login']);
    })
  );
};

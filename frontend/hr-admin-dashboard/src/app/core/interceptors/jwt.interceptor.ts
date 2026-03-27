import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { switchMap, take } from 'rxjs/operators';
import * as AuthActions from '../../store/auth/auth.actions';
import { selectToken } from '../../store/auth/auth.selectors';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(Store);
  return store.select(selectToken).pipe(
    take(1),
    switchMap(token => {
      const refreshToken = localStorage.getItem('sha_refresh_token');
      const expiresAt = Number(localStorage.getItem('sha_expires_at') ?? '') || 0;
      if (refreshToken && expiresAt && expiresAt <= Date.now() + 30_000) {
        store.dispatch(AuthActions.refreshToken({ refreshToken }));
      }
      const cloned = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;
      return next(cloned);
    })
  );
};


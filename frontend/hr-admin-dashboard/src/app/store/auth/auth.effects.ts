import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType, ROOT_EFFECTS_INIT } from '@ngrx/effects';
import { Router } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { Store } from '@ngrx/store';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { of } from 'rxjs';
import * as AuthActions from './auth.actions';
import { LOGIN_MUTATION, ME_QUERY, REFRESH_TOKEN_MUTATION, REGISTER_MUTATION } from '../../graphql/queries';

@Injectable()
export class AuthEffects {
  private actions$ = inject(Actions);
  private apollo   = inject(Apollo);
  private router   = inject(Router);
  private store    = inject(Store);
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  private scheduleRefresh(refreshToken: string, expiresIn: number): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }
    const refreshInMs = Math.max((expiresIn * 1000) - 60_000, 5_000);
    this.refreshTimer = setTimeout(() => {
      this.store.dispatch(AuthActions.refreshToken({ refreshToken }));
    }, refreshInMs);
  }

  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.login),
      switchMap(({ username, password }) =>
        this.apollo.mutate<any>({ mutation: LOGIN_MUTATION, variables: { username, password } }).pipe(
          map(res  => AuthActions.loginSuccess({ payload: res.data.login })),
          catchError(err => of(AuthActions.loginFailure({ error: err.message })))
        )
      )
    )
  );

  loginSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loginSuccess, AuthActions.registerSuccess),
      tap(({ payload }) => {
        this.scheduleRefresh(payload.refreshToken, payload.expiresIn);
        const route = payload.user.role === 'CANDIDATE' ? '/app/dashboard' : '/app/dashboard';
        this.router.navigate([route]);
      })
    ), { dispatch: false }
  );

  refresh$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.refreshToken),
      switchMap(({ refreshToken }) =>
        this.apollo
          .mutate<any>({ mutation: REFRESH_TOKEN_MUTATION, variables: { refreshToken } })
          .pipe(
            map(res => AuthActions.refreshSuccess({ payload: res.data.refreshToken })),
            catchError(err => of(AuthActions.refreshFailure({ error: err.message })))
          )
      )
    )
  );

  refreshSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.refreshSuccess),
      tap(({ payload }) => this.scheduleRefresh(payload.refreshToken, payload.expiresIn))
    ),
    { dispatch: false }
  );

  initRefresh$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ROOT_EFFECTS_INIT),
      map(() => {
        const token = localStorage.getItem('sha_token');
        const refreshToken = localStorage.getItem('sha_refresh_token');
        const expiresAt = Number(localStorage.getItem('sha_expires_at') ?? '') || 0;

        if (!token || !refreshToken) {
          return AuthActions.loadCurrentUser();
        }

        if (expiresAt <= Date.now() + 30_000) {
          return AuthActions.refreshToken({ refreshToken });
        }

        // Approximate expiresIn from stored absolute expiry to schedule silent refresh.
        this.scheduleRefresh(refreshToken, Math.floor((expiresAt - Date.now()) / 1000));
        return AuthActions.loadCurrentUser();
      })
    )
  );

  refreshFailure$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.refreshFailure),
      map(() => AuthActions.logout())
    )
  );

  register$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.register),
      switchMap(({ username, email, password, role }) =>
        this.apollo.mutate<any>({ mutation: REGISTER_MUTATION, variables: { username, email, password, role } }).pipe(
          map(res  => AuthActions.registerSuccess({ payload: res.data.register })),
          catchError(err => of(AuthActions.registerFailure({ error: err.message })))
        )
      )
    )
  );

  logout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.logout),
      tap(() => {
        if (this.refreshTimer) {
          clearTimeout(this.refreshTimer);
          this.refreshTimer = null;
        }
        this.router.navigate(['/auth/login']);
      })
    ), { dispatch: false }
  );

  loadCurrentUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loadCurrentUser),
      switchMap(() =>
        this.apollo.query<any>({ query: ME_QUERY }).pipe(
          map(res  => AuthActions.loadCurrentUserSuccess({ user: res.data.me })),
          catchError(() => of(AuthActions.logout()))
        )
      )
    )
  );
}


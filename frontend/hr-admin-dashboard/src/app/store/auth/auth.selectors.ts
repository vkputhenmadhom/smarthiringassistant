import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AuthState } from './auth.reducer';

export const selectAuthState = createFeatureSelector<AuthState>('auth');

export const selectCurrentUser  = createSelector(selectAuthState, s => s.user);
export const selectToken        = createSelector(selectAuthState, s => s.token);
export const selectRefreshToken = createSelector(selectAuthState, s => s.refreshToken);
export const selectExpiresAt    = createSelector(selectAuthState, s => s.expiresAt);
export const selectAuthLoading  = createSelector(selectAuthState, s => s.loading);
export const selectAuthError    = createSelector(selectAuthState, s => s.error);
export const selectIsLoggedIn   = createSelector(selectAuthState, s => !!s.token);
export const selectIsTokenExpired = createSelector(selectExpiresAt, expiresAt =>
  !!expiresAt && Date.now() >= expiresAt);
export const selectUserRole     = createSelector(selectAuthState, s => s.user?.role);
export const selectIsHR         = createSelector(selectAuthState, s =>
  s.user?.role === 'HR_ADMIN' || s.user?.role === 'RECRUITER' || s.user?.role === 'SUPER_ADMIN');


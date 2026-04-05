import { createReducer, on } from '@ngrx/store';
import { User } from '../../shared/models';
import * as AuthActions from './auth.actions';

export interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  expiresAt: number | null;
  loading: boolean;
  error: string | null;
}

const computeExpiresAt = (expiresIn: number): number => Date.now() + (expiresIn * 1000);

/** Keys stored in localStorage. */
const STORAGE_KEYS = ['sha_token', 'sha_refresh_token', 'sha_expires_at', 'sha_user'] as const;

/**
 * Build the initial NgRx auth state from localStorage.
 * If the stored access-token is clearly expired (> 30 s in the past) we wipe
 * all four keys immediately so the guard and initRefresh$ effect never see a
 * stale session and the app never stalls waiting for a doomed network call.
 */
function buildInitialState(): AuthState {
  const storedExpiresAt = Number(localStorage.getItem('sha_expires_at') ?? '') || 0;
  const tokenExpired    = storedExpiresAt > 0 && storedExpiresAt < Date.now() - 30_000;

  if (tokenExpired) {
    STORAGE_KEYS.forEach(k => localStorage.removeItem(k));
    return { user: null, token: null, refreshToken: null, expiresAt: null, loading: false, error: null };
  }

  return {
    user:         JSON.parse(localStorage.getItem('sha_user') ?? 'null'),
    token:        localStorage.getItem('sha_token'),
    refreshToken: localStorage.getItem('sha_refresh_token'),
    expiresAt:    storedExpiresAt || null,
    loading:      false,
    error:        null,
  };
}

const initialState: AuthState = buildInitialState();

export const authReducer = createReducer(
  initialState,

  on(AuthActions.login, AuthActions.register, state => ({ ...state, loading: true, error: null })),

  on(AuthActions.loginSuccess, AuthActions.registerSuccess, (state, { payload }) => {
    // Prevent candidate users from logging into HR admin dashboard
    const HR_ROLES = ['HR_ADMIN', 'RECRUITER', 'SUPER_ADMIN', 'ADMIN'];
    if (!HR_ROLES.includes(payload.user.role)) {
      // Candidate user attempting to log into HR dashboard — reject and clear state
      return {
        ...state,
        loading: false,
        error: 'This portal is for HR staff only. Please use the candidate portal at http://localhost:5173',
        user: null,
        token: null,
        refreshToken: null,
        expiresAt: null,
      };
    }

    const expiresAt = computeExpiresAt(payload.expiresIn);
    localStorage.setItem('sha_token', payload.token);
    localStorage.setItem('sha_refresh_token', payload.refreshToken);
    localStorage.setItem('sha_expires_at', String(expiresAt));
    localStorage.setItem('sha_user', JSON.stringify(payload.user));

    return {
      ...state,
      loading: false,
      user: payload.user,
      token: payload.token,
      refreshToken: payload.refreshToken,
      expiresAt,
    };
  }),

  on(AuthActions.loginFailure, AuthActions.registerFailure, (state, { error }) =>
    ({ ...state, loading: false, error })),

  on(AuthActions.logout, state => {
    localStorage.removeItem('sha_token');
    localStorage.removeItem('sha_refresh_token');
    localStorage.removeItem('sha_expires_at');
    localStorage.removeItem('sha_user');
    return { ...state, user: null, token: null, refreshToken: null, expiresAt: null };
  }),

  on(AuthActions.refreshSuccess, (state, { payload }) => {
    const expiresAt = computeExpiresAt(payload.expiresIn);
    localStorage.setItem('sha_token', payload.token);
    localStorage.setItem('sha_refresh_token', payload.refreshToken);
    localStorage.setItem('sha_expires_at', String(expiresAt));
    localStorage.setItem('sha_user', JSON.stringify(payload.user));
    return {
      ...state,
      token: payload.token,
      refreshToken: payload.refreshToken,
      expiresAt,
      user: payload.user,
      error: null,
    };
  }),

  on(AuthActions.refreshFailure, (state, { error }) => ({ ...state, error })),

  on(AuthActions.loadCurrentUserSuccess, (state, { user }) => {
    const privileged = ['HR_ADMIN', 'RECRUITER', 'SUPER_ADMIN'];
    // Never downgrade a privileged role to CANDIDATE via a failed ME_QUERY fallback.
    const resolvedRole =
      privileged.includes(state.user?.role ?? '') && user?.role === 'CANDIDATE'
        ? state.user!.role
        : user?.role;
    const resolvedUser = user ? { ...user, role: resolvedRole } : user;
    return { ...state, user: resolvedUser };
  }),
);

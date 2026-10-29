import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { apolloClient } from '../apolloClient';
import { AuthPayload, AuthUser, LOGIN_MUTATION, REFRESH_TOKEN_MUTATION, REGISTER_MUTATION } from '../graphql';

export interface AuthState {
  user: AuthUser | null;
  token: string | null;
  refreshToken: string | null;
  expiresAt: number | null;
  loading: boolean;
  error: string | null;
}

const computeExpiresAt = (expiresIn: number): number => Date.now() + (expiresIn * 1000);

const persistAuth = (payload: AuthPayload): void => {
  const expiresAt = computeExpiresAt(payload.expiresIn);
  localStorage.setItem('sha_token', payload.token);
  localStorage.setItem('sha_refresh_token', payload.refreshToken);
  localStorage.setItem('sha_expires_at', String(expiresAt));
  localStorage.setItem('sha_user', JSON.stringify(payload.user));
};

const initialState: AuthState = {
  user: JSON.parse(localStorage.getItem('sha_user') ?? 'null'),
  token: localStorage.getItem('sha_token'),
  refreshToken: localStorage.getItem('sha_refresh_token'),
  expiresAt: Number(localStorage.getItem('sha_expires_at') ?? '') || null,
  loading: false,
  error: null,
};

export const loginAsync = createAsyncThunk('auth/login', async (payload: { username: string; password: string }) => {
  const res = await apolloClient.mutate<{ login: AuthPayload }>({ mutation: LOGIN_MUTATION, variables: payload });
  const authPayload = res.data?.login as AuthPayload;

  // For candidate portal, map all roles to CANDIDATE for frontend feature access
  // while preserving the actual role in the JWT for backend authorization
  if (authPayload?.user) {
    authPayload.user = { ...authPayload.user, role: 'CANDIDATE' };
  }

  return authPayload;
});

export const registerAsync = createAsyncThunk('auth/register', async (payload: { username: string; email: string; password: string; role: string }) => {
  const res = await apolloClient.mutate<{ register: AuthPayload }>({ mutation: REGISTER_MUTATION, variables: payload });
  const authPayload = res.data?.register as AuthPayload;

  // For candidate portal, map all roles to CANDIDATE for frontend feature access
  if (authPayload?.user) {
    authPayload.user = { ...authPayload.user, role: 'CANDIDATE' };
  }

  return authPayload;
});

export const refreshAsync = createAsyncThunk('auth/refresh', async (refreshToken: string) => {
  const res = await apolloClient.mutate<{ refreshToken: AuthPayload }>({
    mutation: REFRESH_TOKEN_MUTATION,
    variables: { refreshToken },
    fetchPolicy: 'no-cache',
  });
  const authPayload = res.data?.refreshToken as AuthPayload;

  // For candidate portal, map all roles to CANDIDATE for frontend feature access
  if (authPayload?.user) {
    authPayload.user = { ...authPayload.user, role: 'CANDIDATE' };
  }

  return authPayload;
});

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setAuthFromOAuthCallback(state, action: { payload: AuthPayload }) {
      const payload = action.payload;
      const expiresAt = computeExpiresAt(payload.expiresIn);
      state.user = payload.user;
      state.token = payload.token;
      state.refreshToken = payload.refreshToken;
      state.expiresAt = expiresAt;
      state.loading = false;
      state.error = null;
      persistAuth(payload);
    },
    logout(state) {
      state.user = null;
      state.token = null;
      state.refreshToken = null;
      state.expiresAt = null;
      localStorage.removeItem('sha_token');
      localStorage.removeItem('sha_refresh_token');
      localStorage.removeItem('sha_expires_at');
      localStorage.removeItem('sha_user');
    },
  },
  extraReducers: builder => {
    builder
      .addCase(loginAsync.pending, s => { s.loading = true; s.error = null; })
      .addCase(loginAsync.fulfilled, (s, a) => {
        const expiresAt = computeExpiresAt(a.payload.expiresIn);
        s.loading = false;
        s.token = a.payload.token;
        s.refreshToken = a.payload.refreshToken;
        s.expiresAt = expiresAt;
        s.user = a.payload.user;
        persistAuth(a.payload);
      })
      .addCase(loginAsync.rejected, (s, a) => { s.loading = false; s.error = a.error.message ?? 'Login failed'; })
      .addCase(registerAsync.pending, s => { s.loading = true; s.error = null; })
      .addCase(registerAsync.fulfilled, (s, a) => {
        const expiresAt = computeExpiresAt(a.payload.expiresIn);
        s.loading = false;
        s.token = a.payload.token;
        s.refreshToken = a.payload.refreshToken;
        s.expiresAt = expiresAt;
        s.user = a.payload.user;
        persistAuth(a.payload);
      })
      .addCase(registerAsync.rejected, (s, a) => { s.loading = false; s.error = a.error.message ?? 'Registration failed'; })

      .addCase(refreshAsync.pending, s => { s.loading = true; s.error = null; })
      .addCase(refreshAsync.fulfilled, (s, a) => {
        if (!a.payload?.token) {
          s.loading = false;
          s.error = 'Session refresh failed';
          return;
        }
        const expiresAt = computeExpiresAt(a.payload.expiresIn);
        s.loading = false;
        s.token = a.payload.token;
        s.refreshToken = a.payload.refreshToken;
        s.expiresAt = expiresAt;
        s.user = a.payload.user;
        persistAuth(a.payload);
      })
      .addCase(refreshAsync.rejected, (s, a) => {
        s.loading = false;
        s.error = a.error.message ?? 'Session expired';
        s.user = null;
        s.token = null;
        s.refreshToken = null;
        s.expiresAt = null;
        localStorage.removeItem('sha_token');
        localStorage.removeItem('sha_refresh_token');
        localStorage.removeItem('sha_expires_at');
        localStorage.removeItem('sha_user');
      });
  },
});

export const { setAuthFromOAuthCallback, logout } = authSlice.actions;
export default authSlice.reducer;

import { createAction, props } from '@ngrx/store';
import { AuthPayload, User } from '../../shared/models';

export const login        = createAction('[Auth] Login',         props<{ username: string; password: string }>());
export const loginSuccess = createAction('[Auth] Login Success', props<{ payload: AuthPayload }>());
export const loginFailure = createAction('[Auth] Login Failure', props<{ error: string }>());

export const register        = createAction('[Auth] Register',         props<{ username: string; email: string; password: string; role: string }>());
export const registerSuccess = createAction('[Auth] Register Success', props<{ payload: AuthPayload }>());
export const registerFailure = createAction('[Auth] Register Failure', props<{ error: string }>());

export const logout         = createAction('[Auth] Logout');
export const refreshToken   = createAction('[Auth] Refresh Token',   props<{ refreshToken: string }>());
export const refreshSuccess = createAction('[Auth] Refresh Success', props<{ payload: AuthPayload }>());
export const refreshFailure = createAction('[Auth] Refresh Failure', props<{ error: string }>());

export const loadCurrentUser        = createAction('[Auth] Load Current User');
export const loadCurrentUserSuccess = createAction('[Auth] Load Current User Success', props<{ user: User }>());


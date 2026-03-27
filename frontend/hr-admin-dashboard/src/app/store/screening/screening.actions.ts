import { createAction, props } from '@ngrx/store';
import { ScreeningSession } from '../../shared/models';

export const loadScreenings = createAction('[Screening] Load All');
export const loadScreeningsSuccess = createAction(
  '[Screening] Load All Success',
  props<{ sessions: ScreeningSession[] }>()
);
export const loadScreeningsFailure = createAction(
  '[Screening] Load All Failure',
  props<{ error: string }>()
);

export const loadScreeningSession = createAction(
  '[Screening] Load One',
  props<{ id: string }>()
);
export const loadScreeningSessionSuccess = createAction(
  '[Screening] Load One Success',
  props<{ session: ScreeningSession }>()
);
export const loadScreeningSessionFailure = createAction(
  '[Screening] Load One Failure',
  props<{ error: string }>()
);

export const advanceScreening = createAction(
  '[Screening] Advance',
  props<{ sessionId: string }>()
);
export const advanceScreeningSuccess = createAction(
  '[Screening] Advance Success',
  props<{ session: ScreeningSession }>()
);
export const advanceScreeningFailure = createAction(
  '[Screening] Advance Failure',
  props<{ error: string }>()
);

export const selectScreening = createAction(
  '[Screening] Select',
  props<{ id: string | null }>()
);


import { createEntityAdapter, EntityAdapter, EntityState } from '@ngrx/entity';
import { createReducer, on } from '@ngrx/store';
import { ScreeningSession } from '../../shared/models';
import * as ScreeningActions from './screening.actions';

export interface ScreeningState extends EntityState<ScreeningSession> {
  loading: boolean;
  error: string | null;
  selectedId: string | null;
}

const adapter: EntityAdapter<ScreeningSession> = createEntityAdapter<ScreeningSession>();

const initialState: ScreeningState = adapter.getInitialState({
  loading: false,
  error: null,
  selectedId: null,
});

export const screeningReducer = createReducer(
  initialState,
  on(
    ScreeningActions.loadScreenings,
    ScreeningActions.loadScreeningSession,
    ScreeningActions.advanceScreening,
    state => ({ ...state, loading: true, error: null })
  ),
  on(ScreeningActions.loadScreeningsSuccess, (state, { sessions }) =>
    adapter.setAll(sessions, { ...state, loading: false })
  ),
  on(
    ScreeningActions.loadScreeningSessionSuccess,
    ScreeningActions.advanceScreeningSuccess,
    (state, { session }) => adapter.upsertOne(session, { ...state, loading: false })
  ),
  on(
    ScreeningActions.loadScreeningsFailure,
    ScreeningActions.loadScreeningSessionFailure,
    ScreeningActions.advanceScreeningFailure,
    (state, { error }) => ({ ...state, loading: false, error })
  ),
  on(ScreeningActions.selectScreening, (state, { id }) => ({ ...state, selectedId: id }))
);

export const { selectAll, selectEntities } = adapter.getSelectors();


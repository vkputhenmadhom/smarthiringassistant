import { createFeatureSelector, createSelector } from '@ngrx/store';
import { ScreeningState, selectAll, selectEntities } from './screening.store.reducer';

const s = createFeatureSelector<ScreeningState>('screening');

export const selectAllScreenings = createSelector(s, selectAll);
export const selectScreeningEntities = createSelector(s, selectEntities);
export const selectScreeningLoading = createSelector(s, state => state.loading);
export const selectScreeningError = createSelector(s, state => state.error);
export const selectSelectedScreeningId = createSelector(s, state => state.selectedId);
export const selectSelectedScreening = createSelector(
  selectScreeningEntities,
  selectSelectedScreeningId,
  (entities, id) => (id ? entities[id] ?? null : null)
);


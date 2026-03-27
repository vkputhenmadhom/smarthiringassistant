import { createFeatureSelector, createSelector } from '@ngrx/store';
import { CandidatesState, selectAll, selectEntities } from './candidates.reducer';

const s = createFeatureSelector<CandidatesState>('candidates');
export const selectAllCandidates     = createSelector(s, selectAll);
export const selectCandidatesLoading = createSelector(s, c => c.loading);
export const selectCandidatesTotal   = createSelector(s, c => c.totalElements);
export const selectSelectedCandidateId = createSelector(s, c => c.selectedId);
export const selectSelectedCandidate = createSelector(selectEntities, selectSelectedCandidateId,
  (entities, id) => (id ? entities[id] : null));


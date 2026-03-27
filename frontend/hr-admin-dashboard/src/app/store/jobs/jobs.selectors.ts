import { createFeatureSelector, createSelector } from '@ngrx/store';
import { JobsState, selectAll, selectEntities } from './jobs.reducer';

const s = createFeatureSelector<JobsState>('jobs');

export const selectAllJobs        = createSelector(s, selectAll);
export const selectJobEntities    = createSelector(s, selectEntities);
export const selectJobsLoading    = createSelector(s, j => j.loading);
export const selectJobsError      = createSelector(s, j => j.error);
export const selectSelectedJobId  = createSelector(s, j => j.selectedId);
export const selectJobsTotalPages = createSelector(s, j => j.totalPages);
export const selectJobsTotalCount = createSelector(s, j => j.totalElements);
export const selectCurrentPage    = createSelector(s, j => j.currentPage);
export const selectSelectedJob    = createSelector(selectJobEntities, selectSelectedJobId,
  (entities, id) => (id ? entities[id] : null));


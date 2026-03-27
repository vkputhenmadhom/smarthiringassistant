import { createAction, props } from '@ngrx/store';
import { Candidate, PageResponse } from '../../shared/models';

export const loadCandidates        = createAction('[Candidates] Load',         props<{ page?: number; size?: number; status?: string; search?: string }>());
export const loadCandidatesSuccess = createAction('[Candidates] Load Success', props<{ data: PageResponse<Candidate> }>());
export const loadCandidatesFailure = createAction('[Candidates] Load Failure', props<{ error: string }>());

export const loadCandidate        = createAction('[Candidates] Load One',         props<{ id: string }>());
export const loadCandidateSuccess = createAction('[Candidates] Load One Success', props<{ candidate: Candidate }>());

export const selectCandidate = createAction('[Candidates] Select', props<{ id: string | null }>());


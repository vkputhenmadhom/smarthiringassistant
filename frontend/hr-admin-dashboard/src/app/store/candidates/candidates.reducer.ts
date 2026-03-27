import { createEntityAdapter, EntityAdapter, EntityState } from '@ngrx/entity';
import { createReducer, on } from '@ngrx/store';
import { Candidate } from '../../shared/models';
import * as CandidatesActions from './candidates.actions';

export interface CandidatesState extends EntityState<Candidate> {
  loading: boolean; error: string | null; selectedId: string | null;
  totalElements: number; totalPages: number; currentPage: number;
}

const adapter: EntityAdapter<Candidate> = createEntityAdapter<Candidate>();
const initialState: CandidatesState = adapter.getInitialState({
  loading: false, error: null, selectedId: null, totalElements: 0, totalPages: 0, currentPage: 0,
});

export const candidatesReducer = createReducer(
  initialState,
  on(CandidatesActions.loadCandidates, s => ({ ...s, loading: true })),
  on(CandidatesActions.loadCandidatesSuccess, (s, { data }) =>
    adapter.setAll(data.content, { ...s, loading: false, totalElements: data.totalElements, totalPages: data.totalPages, currentPage: data.page })),
  on(CandidatesActions.loadCandidatesFailure, (s, { error }) => ({ ...s, loading: false, error })),
  on(CandidatesActions.loadCandidateSuccess,  (s, { candidate }) => adapter.upsertOne(candidate, s)),
  on(CandidatesActions.selectCandidate,       (s, { id }) => ({ ...s, selectedId: id })),
);
export const { selectAll, selectEntities } = adapter.getSelectors();


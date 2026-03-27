import { createEntityAdapter, EntityAdapter, EntityState } from '@ngrx/entity';
import { createReducer, on } from '@ngrx/store';
import { Job } from '../../shared/models';
import * as JobsActions from './jobs.actions';

export interface JobsState extends EntityState<Job> {
  loading: boolean;
  error: string | null;
  selectedId: string | null;
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

const adapter: EntityAdapter<Job> = createEntityAdapter<Job>();

const initialState: JobsState = adapter.getInitialState({
  loading: false, error: null, selectedId: null,
  totalElements: 0, totalPages: 0, currentPage: 0,
});

export const jobsReducer = createReducer(
  initialState,
  on(JobsActions.loadJobs,  s => ({ ...s, loading: true, error: null })),
  on(JobsActions.loadJobsSuccess, (s, { data }) =>
    adapter.setAll(data.content, { ...s, loading: false, totalElements: data.totalElements, totalPages: data.totalPages, currentPage: data.page })),
  on(JobsActions.loadJobsFailure, (s, { error }) => ({ ...s, loading: false, error })),

  on(JobsActions.loadJobSuccess,   (s, { job }) => adapter.upsertOne(job, s)),
  on(JobsActions.createJobSuccess, (s, { job }) => adapter.addOne(job, s)),
  on(JobsActions.updateJobSuccess, (s, { job }) => adapter.upsertOne(job, s)),
  on(JobsActions.publishJobSuccess,(s, { job }) => adapter.upsertOne(job, s)),
  on(JobsActions.deleteJobSuccess, (s, { id  }) => adapter.removeOne(id, s)),
  on(JobsActions.selectJob,        (s, { id  }) => ({ ...s, selectedId: id })),
);

export const { selectAll, selectEntities, selectIds, selectTotal } = adapter.getSelectors();


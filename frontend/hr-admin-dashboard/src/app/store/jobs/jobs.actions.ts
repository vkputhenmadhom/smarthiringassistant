import { createAction, props } from '@ngrx/store';
import { Job, PageResponse } from '../../shared/models';

export const loadJobs        = createAction('[Jobs] Load',         props<{ page?: number; size?: number; status?: string; search?: string }>());
export const loadJobsSuccess = createAction('[Jobs] Load Success', props<{ data: PageResponse<Job> }>());
export const loadJobsFailure = createAction('[Jobs] Load Failure', props<{ error: string }>());

export const loadJob        = createAction('[Jobs] Load One',         props<{ id: string }>());
export const loadJobSuccess = createAction('[Jobs] Load One Success', props<{ job: Job }>());
export const loadJobFailure = createAction('[Jobs] Load One Failure', props<{ error: string }>());

export const createJob        = createAction('[Jobs] Create',         props<{ input: Partial<Job> }>());
export const createJobSuccess = createAction('[Jobs] Create Success', props<{ job: Job }>());
export const createJobFailure = createAction('[Jobs] Create Failure', props<{ error: string }>());

export const updateJob        = createAction('[Jobs] Update',         props<{ id: string; input: Partial<Job> }>());
export const updateJobSuccess = createAction('[Jobs] Update Success', props<{ job: Job }>());

export const deleteJob        = createAction('[Jobs] Delete',         props<{ id: string }>());
export const deleteJobSuccess = createAction('[Jobs] Delete Success', props<{ id: string }>());

export const publishJob        = createAction('[Jobs] Publish',         props<{ id: string }>());
export const publishJobSuccess = createAction('[Jobs] Publish Success', props<{ job: Job }>());

export const selectJob   = createAction('[Jobs] Select', props<{ id: string | null }>());
export const clearFilter = createAction('[Jobs] Clear Filter');


import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Apollo } from 'apollo-angular';
import { catchError, map, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import * as JobsActions from './jobs.actions';
import {
  JOBS_QUERY, JOB_QUERY, CREATE_JOB_MUTATION,
  UPDATE_JOB_MUTATION, DELETE_JOB_MUTATION, PUBLISH_JOB_MUTATION,
} from '../../graphql/queries';

@Injectable()
export class JobsEffects {
  private actions$ = inject(Actions);
  private apollo   = inject(Apollo);

  loadJobs$ = createEffect(() =>
    this.actions$.pipe(
      ofType(JobsActions.loadJobs),
      switchMap(({ page, size, status, search }) =>
        this.apollo.query<any>({ query: JOBS_QUERY, variables: { page, size, status, search }, fetchPolicy: 'network-only' }).pipe(
          map(res  => JobsActions.loadJobsSuccess({ data: res.data.jobs })),
          catchError(err => of(JobsActions.loadJobsFailure({ error: err.message })))
        )
      )
    )
  );

  loadJob$ = createEffect(() =>
    this.actions$.pipe(
      ofType(JobsActions.loadJob),
      switchMap(({ id }) =>
        this.apollo.query<any>({ query: JOB_QUERY, variables: { id } }).pipe(
          map(res  => JobsActions.loadJobSuccess({ job: res.data.job })),
          catchError(err => of(JobsActions.loadJobFailure({ error: err.message })))
        )
      )
    )
  );

  createJob$ = createEffect(() =>
    this.actions$.pipe(
      ofType(JobsActions.createJob),
      switchMap(({ input }) =>
        this.apollo.mutate<any>({ mutation: CREATE_JOB_MUTATION, variables: { input } }).pipe(
          map(res  => JobsActions.createJobSuccess({ job: res.data.createJob })),
          catchError(err => of(JobsActions.createJobFailure({ error: err.message })))
        )
      )
    )
  );

  updateJob$ = createEffect(() =>
    this.actions$.pipe(
      ofType(JobsActions.updateJob),
      switchMap(({ id, input }) =>
        this.apollo.mutate<any>({ mutation: UPDATE_JOB_MUTATION, variables: { id, input } }).pipe(
          map(res  => JobsActions.updateJobSuccess({ job: res.data.updateJob })),
          catchError(() => of(JobsActions.loadJobsFailure({ error: 'Update failed' })))
        )
      )
    )
  );

  deleteJob$ = createEffect(() =>
    this.actions$.pipe(
      ofType(JobsActions.deleteJob),
      switchMap(({ id }) =>
        this.apollo.mutate<any>({ mutation: DELETE_JOB_MUTATION, variables: { id } }).pipe(
          map(() => JobsActions.deleteJobSuccess({ id })),
          catchError(() => of(JobsActions.loadJobsFailure({ error: 'Delete failed' })))
        )
      )
    )
  );

  publishJob$ = createEffect(() =>
    this.actions$.pipe(
      ofType(JobsActions.publishJob),
      switchMap(({ id }) =>
        this.apollo.mutate<any>({ mutation: PUBLISH_JOB_MUTATION, variables: { id } }).pipe(
          map(res  => JobsActions.publishJobSuccess({ job: res.data.publishJob })),
          catchError(() => of(JobsActions.loadJobsFailure({ error: 'Publish failed' })))
        )
      )
    )
  );
}


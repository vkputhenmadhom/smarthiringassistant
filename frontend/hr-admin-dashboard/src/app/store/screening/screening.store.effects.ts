import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Apollo } from 'apollo-angular';
import { catchError, map, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import * as ScreeningActions from './screening.actions';
import {
  ADVANCE_SCREENING_MUTATION,
  MY_SCREENING_SESSIONS_QUERY,
  SCREENING_SESSION_QUERY,
} from '../../graphql/queries';

@Injectable()
export class ScreeningEffects {
  private actions$ = inject(Actions);
  private apollo = inject(Apollo);

  loadScreenings$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ScreeningActions.loadScreenings),
      switchMap(() =>
        this.apollo
          .query<any>({ query: MY_SCREENING_SESSIONS_QUERY, fetchPolicy: 'network-only' })
          .pipe(
            map(res => ScreeningActions.loadScreeningsSuccess({ sessions: res.data.myScreeningSessions ?? [] })),
            catchError(err => of(ScreeningActions.loadScreeningsFailure({ error: err.message })))
          )
      )
    )
  );

  loadScreeningSession$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ScreeningActions.loadScreeningSession),
      switchMap(({ id }) =>
        this.apollo
          .query<any>({ query: SCREENING_SESSION_QUERY, variables: { id }, fetchPolicy: 'network-only' })
          .pipe(
            map(res => ScreeningActions.loadScreeningSessionSuccess({ session: res.data.screeningSession })),
            catchError(err => of(ScreeningActions.loadScreeningSessionFailure({ error: err.message })))
          )
      )
    )
  );

  advanceScreening$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ScreeningActions.advanceScreening),
      switchMap(({ sessionId }) =>
        this.apollo
          .mutate<any>({ mutation: ADVANCE_SCREENING_MUTATION, variables: { sessionId } })
          .pipe(
            map(res => ScreeningActions.advanceScreeningSuccess({ session: res.data.advanceScreening })),
            catchError(err => of(ScreeningActions.advanceScreeningFailure({ error: err.message })))
          )
      )
    )
  );
}


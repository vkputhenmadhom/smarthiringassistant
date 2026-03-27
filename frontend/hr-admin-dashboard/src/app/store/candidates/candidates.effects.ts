import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Apollo } from 'apollo-angular';
import { catchError, map, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import * as CandidatesActions from './candidates.actions';
import { CANDIDATES_QUERY, CANDIDATE_QUERY } from '../../graphql/queries';

@Injectable()
export class CandidatesEffects {
  private actions$ = inject(Actions);
  private apollo   = inject(Apollo);

  loadCandidates$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CandidatesActions.loadCandidates),
      switchMap(({ page, size, status, search }) =>
        this.apollo.query<any>({ query: CANDIDATES_QUERY, variables: { page, size, status, search }, fetchPolicy: 'network-only' }).pipe(
          map(res  => CandidatesActions.loadCandidatesSuccess({ data: res.data.candidates })),
          catchError(err => of(CandidatesActions.loadCandidatesFailure({ error: err.message })))
        )
      )
    )
  );

  loadCandidate$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CandidatesActions.loadCandidate),
      switchMap(({ id }) =>
        this.apollo.query<any>({ query: CANDIDATE_QUERY, variables: { id } }).pipe(
          map(res  => CandidatesActions.loadCandidateSuccess({ candidate: res.data.candidate })),
          catchError(() => of(CandidatesActions.loadCandidatesFailure({ error: 'Failed to load candidate' })))
        )
      )
    )
  );
}


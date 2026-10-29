import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { Apollo, gql } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDividerModule } from '@angular/material/divider';
import { Observable, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ScreeningSession } from '../../../shared/models';
import {
  advanceScreening,
  loadScreeningSession,
  selectScreening,
} from '../../../store/screening/screening.actions';
import { selectSelectedScreening } from '../../../store/screening/screening.selectors';

const SCREENING_UPDATED_SUBSCRIPTION = gql`
  subscription ScreeningUpdated($sessionId: ID!) {
    screeningUpdated(sessionId: $sessionId) {
      id
      candidateId
      jobId
      currentStage
      status
      decision
      finalScore
      stageResults { stage passed score reasons evaluatedAt }
      failureReasons
      createdAt
      completedAt
    }
  }
`;

@Component({
  selector: 'sha-screening-detail',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatDividerModule],
  template: `
    <ng-container *ngIf="session$ | async as s">
      <div class="page-header flex justify-between items-center">
        <div>
          <h1>Screening {{ s.id | slice:0:8 }}</h1>
          <p>Candidate {{ s.candidateId }} · Job {{ s.jobId }}</p>
        </div>
        <div class="flex gap-4">
          <span [class]="statusChip(s.status)">{{ s.status }}</span>
          <span [class]="decisionChip(s.decision)">{{ s.decision }}</span>
        </div>
      </div>

      <div class="two-col">
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Stage Results</mat-card-title></mat-card-header>
          <mat-card-content>
            <div *ngFor="let r of s.stageResults" class="stage-result">
              <div class="flex justify-between items-center">
                <strong>{{ r.stage | titlecase }}</strong>
                <span [class]="r.passed ? 'chip chip-green' : 'chip chip-red'">
                  {{ r.passed ? 'PASS' : 'FAIL' }}
                </span>
              </div>
              <mat-progress-bar mode="determinate" [value]="r.score"
                color="primary" style="margin:.4rem 0"></mat-progress-bar>
              <div class="score-label">Score: {{ r.score.toFixed(1) }}/100</div>
              <div *ngFor="let reason of r.reasons" class="reason-item">- {{ reason }}</div>
            </div>
            <p *ngIf="!s.stageResults.length" style="color:#888;">No stage results yet.</p>
          </mat-card-content>
        </mat-card>

        <mat-card class="panel">
          <mat-card-header><mat-card-title>Summary</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="summary-row">
              <strong>Current Stage:</strong>
              <span class="chip chip-blue">{{ s.currentStage }}</span>
            </div>
            <div class="summary-row">
              <strong>Final Score:</strong>
              {{ s.finalScore != null ? s.finalScore.toFixed(1) : 'In progress' }}
            </div>
            <mat-divider style="margin:1rem 0"></mat-divider>
            <div *ngIf="s.failureReasons?.length">
              <strong style="color:#c62828">Failure Reasons:</strong>
              <div *ngFor="let r of s.failureReasons" class="reason-item">- {{ r }}</div>
            </div>
            <button mat-raised-button color="primary" (click)="advance(s.id)"
                    *ngIf="s.status === 'IN_PROGRESS'"
                    style="margin-top:1rem;width:100%">
              <mat-icon>skip_next</mat-icon> Advance Stage
            </button>
          </mat-card-content>
        </mat-card>
      </div>
    </ng-container>
  `,
  styles: [`
    .two-col      { display:grid; grid-template-columns:2fr 1fr; gap:1rem; }
    .stage-result { border-bottom:1px solid #f0f0f0; padding:.75rem 0; }
    .score-label  { font-size:.8rem; color:#666; }
    .reason-item  { font-size:.82rem; color:#c62828; margin:.15rem 0; }
    .summary-row  { padding:.4rem 0; border-bottom:1px solid #f5f5f5; font-size:.9rem;
                    display:flex; align-items:center; justify-content:space-between; }
  `],
})
export class ScreeningDetailComponent implements OnInit, OnDestroy {
  private route  = inject(ActivatedRoute);
  private store = inject(Store);
  private apollo = inject(Apollo);
  private destroy$ = new Subject<void>();

  session$!: Observable<ScreeningSession | null>;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.store.dispatch(selectScreening({ id }));
    this.store.dispatch(loadScreeningSession({ id }));
    this.subscribeToSessionUpdates(id);
    this.session$ = this.store.select(selectSelectedScreening);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  advance(sessionId: string): void {
    this.store.dispatch(advanceScreening({ sessionId }));
  }

  statusChip(s: string): string {
    return s === 'COMPLETED' ? 'chip chip-green' : s === 'IN_PROGRESS' ? 'chip chip-orange' : 'chip chip-grey';
  }
  decisionChip(s: string): string {
    return s === 'PASS' ? 'chip chip-green' : s === 'FAIL' ? 'chip chip-red' : 'chip chip-grey';
  }

  private subscribeToSessionUpdates(sessionId: string): void {
    this.apollo.subscribe<{ screeningUpdated: ScreeningSession }>({
      query: SCREENING_UPDATED_SUBSCRIPTION,
      variables: { sessionId },
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ data }) => {
        const updatedSession = data?.screeningUpdated;
        if (!updatedSession || updatedSession.id !== sessionId) {
          return;
        }
        // Refetch the session to keep detail view consistent with backend calculations.
        this.store.dispatch(loadScreeningSession({ id: sessionId }));
      });
  }
}

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ScreeningSession } from '../../../shared/models';
import { dismissScreeningError, loadScreenings, selectScreening } from '../../../store/screening/screening.actions';
import { selectAllScreenings, selectScreeningError, selectScreeningLoading } from '../../../store/screening/screening.selectors';

@Component({
  selector: 'sha-screening-list',
  standalone: true,
  imports: [CommonModule, RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatCardModule, MatProgressSpinnerModule],
  template: `
    <div class="page-header"><h1>Screening Sessions</h1><p>Monitor active screening pipelines</p></div>
    <div class="error-banner" role="alert" *ngIf="error$ | async as error">
      <mat-icon>error_outline</mat-icon>
      <span>{{ error }}</span>
      <button mat-icon-button (click)="dismissError()" aria-label="Dismiss"><mat-icon>close</mat-icon></button>
    </div>
    <mat-card class="panel">
      <mat-card-content>
        <mat-spinner *ngIf="loading$ | async" diameter="40" style="display:block;margin:1rem auto"></mat-spinner>

        <table mat-table [dataSource]="(sessions$ | async) ?? []" class="sha-table" *ngIf="!(loading$ | async)">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>Session ID</th>
            <td mat-cell *matCellDef="let s">
              <a [routerLink]="s.id" style="color:#3f51b5;font-weight:500;">{{ s.id | slice:0:8 }}...</a>
            </td>
          </ng-container>
          <ng-container matColumnDef="candidateId">
            <th mat-header-cell *matHeaderCellDef>Candidate</th>
            <td mat-cell *matCellDef="let s">{{ s.candidateId }}</td>
          </ng-container>
          <ng-container matColumnDef="stage">
            <th mat-header-cell *matHeaderCellDef>Current Stage</th>
            <td mat-cell *matCellDef="let s">
              <span class="chip chip-blue">{{ s.currentStage | titlecase }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let s">
              <span [class]="statusChip(s.status)">{{ s.status }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="decision">
            <th mat-header-cell *matHeaderCellDef>Decision</th>
            <td mat-cell *matCellDef="let s">
              <span [class]="decisionChip(s.decision)">{{ s.decision }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="score">
            <th mat-header-cell *matHeaderCellDef>Score</th>
            <td mat-cell *matCellDef="let s">{{ s.finalScore != null ? s.finalScore.toFixed(1) : '--' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let s">
              <button mat-icon-button [routerLink]="s.id"><mat-icon>visibility</mat-icon></button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let r; columns: cols;" (click)="select(r.id)"></tr>
        </table>

        <p *ngIf="!(loading$ | async) && (((sessions$ | async) ?? []).length === 0)" style="padding:1rem;color:#888;">
          No screening sessions found.
        </p>
      </mat-card-content>
    </mat-card>
  `,
})
export class ScreeningListComponent implements OnInit {
  private store = inject(Store);
  sessions$: Observable<ScreeningSession[]> = this.store.select(selectAllScreenings);
  loading$: Observable<boolean> = this.store.select(selectScreeningLoading);
  error$: Observable<string | null> = this.store.select(selectScreeningError);
  cols = ['id', 'candidateId', 'stage', 'status', 'decision', 'score', 'actions'];

  ngOnInit(): void {
    this.store.dispatch(loadScreenings());
  }

  select(id: string): void {
    this.store.dispatch(selectScreening({ id }));
  }

  dismissError(): void {
    this.store.dispatch(dismissScreeningError());
  }

  statusChip(s: string): string {
    return s === 'COMPLETED' ? 'chip chip-green' : s === 'IN_PROGRESS' ? 'chip chip-orange' : 'chip chip-grey';
  }
  decisionChip(s: string): string {
    return s === 'PASS' ? 'chip chip-green' : s === 'FAIL' ? 'chip chip-red' : 'chip chip-grey';
  }
}

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MATCHES_FOR_REVIEW_QUERY } from '../../graphql/queries';
import { CandidateMatch, MatchStatus } from '../../shared/models';
import { MatchActionsComponent } from '../../shared/components/match-actions.component';
import { scoreParts, seniorityLabel, statusChip, STATUS_LABELS } from '../../shared/match-format';

/** Every candidate-job match across jobs, best first, by stage: the HR review queue. */
@Component({
  selector: 'sha-review',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatTabsModule, MatTableModule, MatPaginatorModule,
            MatProgressSpinnerModule, MatIconModule, MatButtonModule, MatchActionsComponent],
  template: `
    <div class="page-header">
      <h1>Review</h1>
      <p>Candidates matched to open jobs, best first. Shortlisting or hiring lets the candidate know in the app; rejecting does not.</p>
    </div>

    <mat-tab-group [selectedIndex]="tabIndex" (selectedIndexChange)="selectTab($event)" animationDuration="0ms">
      <mat-tab *ngFor="let t of tabs" [label]="t.label"></mat-tab>
    </mat-tab-group>

    <div class="error-banner" role="alert" *ngIf="error">
      <mat-icon>error_outline</mat-icon><span>{{ error }}</span>
      <button mat-icon-button (click)="load()" aria-label="Retry"><mat-icon>refresh</mat-icon></button>
    </div>

    <div class="loading" *ngIf="loading"><mat-spinner diameter="36"></mat-spinner></div>

    <mat-card class="panel" *ngIf="!loading && !error">
      <mat-card-content>
        <p class="empty" *ngIf="rows.length === 0">{{ emptyText }}</p>

        <table mat-table [dataSource]="rows" class="sha-table" *ngIf="rows.length > 0">
          <ng-container matColumnDef="candidate">
            <th mat-header-cell *matHeaderCellDef>Candidate</th>
            <td mat-cell *matCellDef="let m">
              <a [routerLink]="['/app/candidates', m.candidateId]" class="link">{{ m.candidate?.name || ('Candidate #' + m.candidateId) }}</a>
              <div class="sub" *ngIf="m.reviewerComments">Note: {{ m.reviewerComments }}</div>
            </td>
          </ng-container>
          <ng-container matColumnDef="job">
            <th mat-header-cell *matHeaderCellDef>Job</th>
            <td mat-cell *matCellDef="let m">
              <a [routerLink]="['/app/jobs', m.jobId]" class="link">{{ m.job?.title || 'Job no longer available' }}</a>
              <div class="sub">{{ m.job?.companyName }}<span *ngIf="m.job?.location"> · {{ m.job.location }}</span></div>
              <span class="chip chip-purple" *ngIf="m.job">{{ seniority(m.job) }}</span>
              <span class="chip chip-grey" *ngIf="m.job && m.job.status !== 'OPEN'">Closed</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="score">
            <th mat-header-cell *matHeaderCellDef>Match</th>
            <td mat-cell *matCellDef="let m">
              <strong>{{ (m.score * 100).toFixed(0) }}%</strong>
              <div class="sub">{{ parts(m) }}</div>
            </td>
          </ng-container>
          <ng-container matColumnDef="skills">
            <th mat-header-cell *matHeaderCellDef>Skills</th>
            <td mat-cell *matCellDef="let m">
              <span *ngFor="let s of m.skillMatches.slice(0, 6)" class="chip chip-green">{{ s }}</span>
              <span *ngFor="let s of m.skillGaps.slice(0, 3)" class="chip chip-orange" title="Missing">{{ s }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let m"><span [class]="chip(m.status)">{{ label(m.status) }}</span></td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let m" class="actions-cell">
              <sha-match-actions [match]="m" (changed)="moved($event)"></sha-match-actions>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let r; columns: cols;"></tr>
        </table>

        <mat-paginator *ngIf="total > pageSize" [length]="total" [pageSize]="pageSize" [pageIndex]="page"
                       [hidePageSize]="true" (page)="pageChange($event)"></mat-paginator>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .loading { display:flex; justify-content:center; padding:3rem; }
    .empty { color:#666; padding:1.5rem 0; text-align:center; }
    .link { font-weight:600; }
    .sub { font-size:.8rem; color:#666; margin:.15rem 0; }
    .chip { margin:.1rem; }
    .chip-purple { background:#ede7f6; color:#4527a0; }
    .actions-cell { min-width:220px; }
  `],
})
export class ReviewComponent implements OnInit {
  private apollo = inject(Apollo);

  readonly tabs: { label: string; status: MatchStatus; empty: string }[] = [
    { label: 'New',         status: 'MATCHED',     empty: 'No new matches to review. Matches appear when candidates upload a resume or refresh their matches.' },
    { label: 'Shortlisted', status: 'SHORTLISTED', empty: 'Nobody is shortlisted yet.' },
    { label: 'Hired',       status: 'HIRED',       empty: 'Nobody is marked as hired yet.' },
    { label: 'Rejected',    status: 'REJECTED',    empty: 'Nobody is rejected.' },
  ];
  cols = ['candidate', 'job', 'score', 'skills', 'status', 'actions'];

  tabIndex = 0;
  rows: CandidateMatch[] = [];
  total = 0;
  page = 0;
  pageSize = 25;
  loading = false;
  error: string | null = null;

  get emptyText() { return this.tabs[this.tabIndex].empty; }

  ngOnInit() { this.load(); }

  selectTab(i: number) { this.tabIndex = i; this.page = 0; this.load(); }
  pageChange(e: PageEvent) { this.page = e.pageIndex; this.load(); }

  load() {
    this.loading = true;
    this.error = null;
    this.apollo.query<any>({
      query: MATCHES_FOR_REVIEW_QUERY,
      variables: { status: this.tabs[this.tabIndex].status, page: this.page, size: this.pageSize },
      fetchPolicy: 'network-only',
    }).subscribe({
      next: r => {
        this.rows = r.data.matchesForReview.content;
        this.total = r.data.matchesForReview.totalElements;
        this.loading = false;
      },
      error: err => { this.error = err.message; this.loading = false; },
    });
  }

  /** A decision moves the match to another tab: drop it from this one. */
  moved(m: CandidateMatch) {
    this.rows = this.rows.filter(r => r.id !== m.id);
    this.total = Math.max(0, this.total - 1);
  }

  seniority = seniorityLabel;
  parts = scoreParts;
  chip = statusChip;
  label(s: MatchStatus) { return STATUS_LABELS[s]; }
}

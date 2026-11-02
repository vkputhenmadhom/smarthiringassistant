import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { catchError, map } from 'rxjs/operators';
import { EMPTY } from 'rxjs';
import { JOB_QUERY, MATCHES_FOR_JOB_QUERY } from '../../../graphql/queries';
import { CandidateMatch, Job, MatchStatus } from '../../../shared/models';
import { MatchActionsComponent } from '../../../shared/components/match-actions.component';
import { scoreParts, seniorityLabel, statusChip, STATUS_LABELS } from '../../../shared/match-format';
import { loadJob, publishJob } from '../../../store/jobs/jobs.actions';

@Component({
  selector: 'sha-job-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatChipsModule, MatIconModule, MatButtonModule, MatTableModule, MatDividerModule,
            MatButtonToggleModule, MatchActionsComponent],
  template: `
    <div class="error-banner" role="alert" *ngIf="error">
      <mat-icon>error_outline</mat-icon>
      <span>{{ error }}</span>
      <button mat-icon-button (click)="error = null" aria-label="Dismiss"><mat-icon>close</mat-icon></button>
    </div>

    <ng-container *ngIf="job$ | async as job">
      <div class="page-header flex justify-between items-center">
        <div>
          <h1>{{ job.title }}</h1>
          <p>{{ job.companyName || job.department }} · {{ job.location }} · <span [class]="statusChip(job.status)">{{ job.status }}</span>
             · <span class="chip chip-purple">{{ seniority(job) }}</span></p>
        </div>
        <div class="flex gap-4">
          <a mat-stroked-button [routerLink]="['edit']">Edit</a>
          <button mat-raised-button color="primary" *ngIf="job.status==='DRAFT'" (click)="publish(job.id)">Publish</button>
        </div>
      </div>

      <div class="two-col">
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Job Details</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="detail-row"><strong>Type:</strong> {{ job.type }}</div>
            <div class="detail-row"><strong>Salary:</strong> {{ salaryLabel(job) }}</div>
            <div class="detail-row"><strong>AI Salary Confidence:</strong> {{ ((job.salaryConfidence??0)*100).toFixed(0) }}%</div>
            <div class="detail-row"><strong>Applicants:</strong> {{ job.applicantCount ?? 0 }}</div>
            <mat-divider class="my-2"></mat-divider>
            <div class="desc">{{ job.description }}</div>
          </mat-card-content>
        </mat-card>

        <mat-card class="panel">
          <mat-card-header><mat-card-title>Required Skills</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="skills-wrap">
              <span *ngFor="let s of job.skills" class="chip chip-blue">{{ s }}</span>
            </div>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Matched candidates -->
      <mat-card class="panel mt-4" *ngIf="matchData">
        <mat-card-header>
          <mat-card-title>Matched Candidates ({{ matchData.totalElements }})</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-button-toggle-group [value]="filter" (change)="filter = $event.value" aria-label="Show matches by status" class="filter">
            <mat-button-toggle *ngFor="let f of filters" [value]="f.value">{{ f.label }} ({{ count(f.value) }})</mat-button-toggle>
          </mat-button-toggle-group>
          <p class="empty" *ngIf="shown().length === 0">No candidates here.</p>
          <table mat-table [dataSource]="shown()" class="sha-table" *ngIf="shown().length > 0">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Candidate</th>
              <td mat-cell *matCellDef="let m">
                <a [routerLink]="['/app/candidates', m.candidateId]" class="link">{{ m.candidate?.name || ('Candidate #' + m.candidateId) }}</a>
                <div class="sub" *ngIf="m.reviewerComments">Note: {{ m.reviewerComments }}</div>
              </td>
            </ng-container>
            <ng-container matColumnDef="score">
              <th mat-header-cell *matHeaderCellDef>Match</th>
              <td mat-cell *matCellDef="let m"><strong>{{ (m.score*100).toFixed(0) }}%</strong><div class="sub">{{ parts(m) }}</div></td>
            </ng-container>
            <ng-container matColumnDef="skills">
              <th mat-header-cell *matHeaderCellDef>Matching Skills</th>
              <td mat-cell *matCellDef="let m">
                <span *ngFor="let s of m.skillMatches" class="chip chip-green" style="margin:.1rem">{{ s }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="gaps">
              <th mat-header-cell *matHeaderCellDef>Skill Gaps</th>
              <td mat-cell *matCellDef="let m">
                <span *ngFor="let s of m.skillGaps" class="chip chip-orange" style="margin:.1rem">{{ s }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let m"><span [class]="matchChip(m.status)">{{ label(m.status) }}</span></td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let m" class="match-actions">
                <sha-match-actions [match]="m" (changed)="updated($event)"></sha-match-actions>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="matchCols"></tr>
            <tr mat-row *matRowDef="let r; columns:matchCols;"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </ng-container>
  `,
  styles: [`
    .two-col { display:grid; grid-template-columns:2fr 1fr; gap:1rem; margin-bottom:1rem; }
    .detail-row { padding:.35rem 0; border-bottom:1px solid #f5f5f5; font-size:.9rem; }
    .desc { font-size:.9rem; line-height:1.6; color:#333; white-space:pre-wrap; margin-top:.5rem; }
    .skills-wrap { display:flex; flex-wrap:wrap; gap:.4rem; }
    .match-actions { min-width:220px; }
    .filter { margin-bottom:.75rem; }
    .empty { color:#666; padding:1rem 0; }
    .link { font-weight:600; }
    .sub { font-size:.8rem; color:#666; margin-top:.15rem; }
    .chip-purple { background:#ede7f6; color:#4527a0; }
  `],
})
export class JobDetailComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private store  = inject(Store);
  private apollo = inject(Apollo);

  jobId!: string;
  matchCols = ['name','score','skills','gaps','status','actions'];
  matchData: { totalElements: number; content: CandidateMatch[] } | null = null;
  error: string | null = null;
  readonly filters: { label: string; value: MatchStatus | 'ALL' }[] = [
    { label: 'All', value: 'ALL' }, { label: 'New', value: 'MATCHED' }, { label: 'Shortlisted', value: 'SHORTLISTED' },
    { label: 'Hired', value: 'HIRED' }, { label: 'Rejected', value: 'REJECTED' },
  ];
  filter: MatchStatus | 'ALL' = 'ALL';

  job$ = this.apollo.query<any>({ query: JOB_QUERY, variables: { id: '' } }).pipe(map(r => r.data.job as Job));

  ngOnInit() {
    this.jobId = this.route.snapshot.paramMap.get('id')!;
    this.job$  = this.apollo.query<any>({ query: JOB_QUERY, variables: { id: this.jobId }, fetchPolicy:'network-only' })
                   .pipe(map(r => r.data.job), catchError(err => { this.error = err.message; return EMPTY; }));
    this.loadMatches();
    this.store.dispatch(loadJob({ id: this.jobId }));
  }

  loadMatches() {
    this.apollo.query<any>({ query: MATCHES_FOR_JOB_QUERY, variables: { jobId: this.jobId, page: 0, size: 200 }, fetchPolicy: 'network-only' })
      .subscribe({
        next: r => this.matchData = r.data.matchesForJob,
        error: err => this.error = err.message,
      });
  }

  shown(): CandidateMatch[] {
    const all = this.matchData?.content ?? [];
    return this.filter === 'ALL' ? all : all.filter(m => this.stage(m.status) === this.filter);
  }

  count(value: MatchStatus | 'ALL'): number {
    const all = this.matchData?.content ?? [];
    return value === 'ALL' ? all.length : all.filter(m => this.stage(m.status) === value).length;
  }

  /** PENDING and MATCHED are both "New" */
  private stage(s: MatchStatus): MatchStatus { return s === 'PENDING' ? 'MATCHED' : s; }

  updated(m: CandidateMatch) {
    if (this.matchData) {
      this.matchData = { ...this.matchData, content: this.matchData.content.map(x => x.id === m.id ? m : x) };
    }
  }

  seniority = seniorityLabel;
  parts = scoreParts;
  label(s: MatchStatus) { return STATUS_LABELS[s]; }

  publish(id: string) { this.store.dispatch(publishJob({ id })); }
  statusChip(s: string) { return s==='OPEN'?'chip chip-green':s==='DRAFT'?'chip chip-orange':'chip chip-grey'; }
  matchChip = statusChip;
  salaryLabel(j: Job)   { return j.salaryMin ? `${j.salaryCurrency??'USD'} ${(j.salaryMin/1000).toFixed(0)}k – ${((j.salaryMax??0)/1000).toFixed(0)}k` : 'Not specified'; }
}


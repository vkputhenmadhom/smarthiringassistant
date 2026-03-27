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
import { map } from 'rxjs/operators';
import { JOB_QUERY, MATCHES_FOR_JOB_QUERY, UPDATE_MATCH_STATUS_MUTATION } from '../../../graphql/queries';
import { Job } from '../../../shared/models';
import { loadJob, publishJob } from '../../../store/jobs/jobs.actions';

@Component({
  selector: 'sha-job-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatChipsModule, MatIconModule, MatButtonModule, MatTableModule],
  template: `
    <ng-container *ngIf="job$ | async as job">
      <div class="page-header flex justify-between items-center">
        <div>
          <h1>{{ job.title }}</h1>
          <p>{{ job.department }} · {{ job.location }} · <span [class]="statusChip(job.status)">{{ job.status }}</span></p>
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
      <mat-card class="panel mt-4" *ngIf="matches$ | async as matchData">
        <mat-card-header><mat-card-title>Matched Candidates ({{ matchData.totalElements }})</mat-card-title></mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="matchData.content" class="sha-table">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Candidate</th>
              <td mat-cell *matCellDef="let m">{{ m.candidate?.name }}</td>
            </ng-container>
            <ng-container matColumnDef="score">
              <th mat-header-cell *matHeaderCellDef>Match Score</th>
              <td mat-cell *matCellDef="let m"><strong>{{ (m.score*100).toFixed(0) }}%</strong></td>
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
              <td mat-cell *matCellDef="let m"><span [class]="matchChip(m.status)">{{ m.status }}</span></td>
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
  `],
})
export class JobDetailComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private store  = inject(Store);
  private apollo = inject(Apollo);

  jobId!: string;
  matchCols = ['name','score','skills','gaps','status'];

  job$ = this.apollo.query<any>({ query: JOB_QUERY, variables: { id: '' } }).pipe(map(r => r.data.job as Job));
  matches$ = this.apollo.query<any>({ query: MATCHES_FOR_JOB_QUERY, variables: { jobId: '', page: 0, size: 20 } })
               .pipe(map(r => r.data.matchesForJob));

  ngOnInit() {
    this.jobId = this.route.snapshot.paramMap.get('id')!;
    this.job$  = this.apollo.query<any>({ query: JOB_QUERY, variables: { id: this.jobId }, fetchPolicy:'network-only' }).pipe(map(r => r.data.job));
    this.matches$ = this.apollo.query<any>({ query: MATCHES_FOR_JOB_QUERY, variables: { jobId: this.jobId, page:0, size:20 } }).pipe(map(r => r.data.matchesForJob));
    this.store.dispatch(loadJob({ id: this.jobId }));
  }

  publish(id: string) { this.store.dispatch(publishJob({ id })); }
  statusChip(s: string) { return s==='OPEN'?'chip chip-green':s==='DRAFT'?'chip chip-orange':'chip chip-grey'; }
  matchChip(s: string)  { return s==='SHORTLISTED'?'chip chip-green':s==='REJECTED'?'chip chip-red':'chip chip-blue'; }
  salaryLabel(j: Job)   { return j.salaryMin ? `${j.salaryCurrency??'USD'} ${(j.salaryMin/1000).toFixed(0)}k – ${((j.salaryMax??0)/1000).toFixed(0)}k` : 'Not specified'; }
}


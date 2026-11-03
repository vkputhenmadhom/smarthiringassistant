import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { map } from 'rxjs/operators';
import { CANDIDATE_QUERY, MATCHES_FOR_CANDIDATE_QUERY } from '../../../graphql/queries';
import { Candidate, CandidateMatch, MatchStatus } from '../../../shared/models';
import { MatchActionsComponent } from '../../../shared/components/match-actions.component';
import { scoreParts, seniorityLabel, statusChip, STATUS_LABELS } from '../../../shared/match-format';

@Component({
  selector: 'sha-candidate-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, MatCardModule, MatChipsModule, MatIconModule, MatButtonModule, MatDividerModule, MatchActionsComponent],
  template: `
    <ng-container *ngIf="candidate$ | async as c">
      <div class="page-header flex justify-between items-center">
        <div>
          <h1>{{ c.name }}</h1>
          <p>{{ c.email }} {{ c.phone ? '· '+c.phone : '' }}</p>
        </div>
        <div class="flex gap-4">
          <span [class]="parseChip(c.parseStatus)">{{ c.parseStatus }}</span>
          <span [class]="screenChip(c.screeningStatus??'NOT_STARTED')">{{ c.screeningStatus??'NOT_STARTED' }}</span>
        </div>
      </div>

      <div class="three-col">
        <!-- Skills -->
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Skills</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="chips-wrap">
              <span *ngFor="let s of c.skills" class="chip chip-blue">{{ s }}</span>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Experience -->
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Experience</mat-card-title></mat-card-header>
          <mat-card-content>
            <div *ngFor="let e of c.experience" class="exp-row">
              <div class="exp-title">{{ e.title }} &#64; {{ e.company }}</div>
              <div class="exp-dates">{{ e.startDate }} – {{ e.endDate ?? 'Present' }}</div>
              <div class="exp-desc" *ngIf="e.description">{{ e.description }}</div>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Education -->
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Education</mat-card-title></mat-card-header>
          <mat-card-content>
            <div *ngFor="let e of c.education" class="edu-row">
              <div class="edu-title">{{ e.degree }} in {{ e.field }}</div>
              <div class="edu-sub">{{ e.institution }} · {{ e.graduationYear }}</div>
            </div>
          </mat-card-content>
        </mat-card>
      </div>

      <mat-card class="panel mt-4">
        <mat-card-header><mat-card-title>Matched Jobs ({{ matches.length }})</mat-card-title></mat-card-header>
        <mat-card-content>
          <div class="error" role="alert" *ngIf="matchesError">{{ matchesError }}</div>
          <p class="empty" *ngIf="!matchesError && matches.length === 0">No matches yet. They appear when the candidate uploads a resume or refreshes their matches.</p>
          <div *ngFor="let m of matches" class="match-row">
            <div class="match-main">
              <a [routerLink]="['/app/jobs', m.jobId]" class="link">{{ m.job?.title || 'Job no longer available' }}</a>
              <div class="sub">{{ m.job?.companyName }}<span *ngIf="m.job?.location"> · {{ m.job?.location }}</span></div>
              <span class="chip chip-purple" *ngIf="m.job">{{ seniority(m.job) }}</span>
              <span [class]="chip(m.status)">{{ label(m.status) }}</span>
              <div class="sub" *ngIf="m.reviewerComments">Note: {{ m.reviewerComments }}</div>
            </div>
            <div class="match-score-col">
              <strong>{{ (m.score * 100).toFixed(0) }}%</strong>
              <div class="sub">{{ parts(m) }}</div>
            </div>
            <sha-match-actions class="match-actions" [match]="m" (changed)="updated($event)"></sha-match-actions>
          </div>
        </mat-card-content>
      </mat-card>
    </ng-container>
  `,
  styles: [`
    .three-col { display:grid; grid-template-columns:1fr 1fr 1fr; gap:1rem; margin-bottom:1rem; }
    .chips-wrap { display:flex; flex-wrap:wrap; gap:.4rem; }
    .exp-row { border-bottom:1px solid #f5f5f5; padding:.5rem 0; }
    .exp-title { font-weight:500; font-size:.9rem; }
    .exp-dates, .edu-sub { font-size:.78rem; color:#888; }
    .exp-desc  { font-size:.82rem; color:#555; margin-top:.2rem; }
    .edu-row   { padding:.4rem 0; border-bottom:1px solid #f5f5f5; }
    .edu-title { font-weight:500; font-size:.88rem; }
    .match-row { display:grid; grid-template-columns:2fr 1fr 1.4fr; gap:1rem; align-items:center; padding:.6rem 0; border-bottom:1px solid #f5f5f5; }
    .link { font-weight:600; }
    .sub { font-size:.8rem; color:#666; margin:.15rem 0; }
    .chip { margin:.1rem .2rem .1rem 0; }
    .chip-purple { background:#ede7f6; color:#4527a0; }
    .empty { color:#666; }
    .error { color:#c62828; }
    @media (max-width: 900px) { .match-row { grid-template-columns:1fr; } }
  `],
})
export class CandidateDetailComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private apollo = inject(Apollo);
  candidate$ = this.apollo.query<any>({ query: CANDIDATE_QUERY, variables: { id: '' } }).pipe(map(r => r.data.candidate as Candidate));
  matches: CandidateMatch[] = [];
  matchesError: string | null = null;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.candidate$ = this.apollo.query<any>({ query: CANDIDATE_QUERY, variables: { id }, fetchPolicy:'network-only' }).pipe(map(r => r.data.candidate as Candidate));
    this.apollo.query<any>({ query: MATCHES_FOR_CANDIDATE_QUERY, variables: { candidateId: id, page: 0, size: 50 }, fetchPolicy: 'network-only' })
      .subscribe({
        next: r => this.matches = r.data.matchesForCandidate.content,
        error: err => this.matchesError = err.message,
      });
  }

  updated(m: CandidateMatch) { this.matches = this.matches.map(x => x.id === m.id ? m : x); }

  seniority = seniorityLabel;
  parts = scoreParts;
  chip = statusChip;
  label(s: MatchStatus) { return STATUS_LABELS[s]; }

  parseChip(s: string)  { return s==='COMPLETED'?'chip chip-green':s==='FAILED'?'chip chip-red':'chip chip-orange'; }
  screenChip(s: string) { return s==='PASSED'?'chip chip-green':s==='FAILED'?'chip chip-red':s==='IN_PROGRESS'?'chip chip-orange':'chip chip-grey'; }
}


import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { map } from 'rxjs/operators';
import { CANDIDATE_QUERY } from '../../../graphql/queries';
import { Candidate } from '../../../shared/models';

@Component({
  selector: 'sha-candidate-detail',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatChipsModule, MatIconModule, MatButtonModule, MatDividerModule],
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
              <div class="exp-title">{{ e.title }} @ {{ e.company }}</div>
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

      <mat-card class="panel mt-4" *ngIf="c.matchScore">
        <mat-card-header><mat-card-title>AI Match Score</mat-card-title></mat-card-header>
        <mat-card-content>
          <div class="match-score">{{ (c.matchScore * 100).toFixed(1) }}%</div>
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
    .match-score { font-size:3rem; font-weight:700; color:#3f51b5; }
  `],
})
export class CandidateDetailComponent implements OnInit {
  private route  = inject(ActivatedRoute);
  private apollo = inject(Apollo);
  candidate$ = this.apollo.query<any>({ query: CANDIDATE_QUERY, variables: { id: '' } }).pipe(map(r => r.data.candidate as Candidate));

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.candidate$ = this.apollo.query<any>({ query: CANDIDATE_QUERY, variables: { id }, fetchPolicy:'network-only' }).pipe(map(r => r.data.candidate as Candidate));
  }

  parseChip(s: string)  { return s==='COMPLETED'?'chip chip-green':s==='FAILED'?'chip chip-red':'chip chip-orange'; }
  screenChip(s: string) { return s==='PASSED'?'chip chip-green':s==='FAILED'?'chip chip-red':s==='IN_PROGRESS'?'chip chip-orange':'chip chip-grey'; }
}


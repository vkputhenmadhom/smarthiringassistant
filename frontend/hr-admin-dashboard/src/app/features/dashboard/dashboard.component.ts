import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDividerModule } from '@angular/material/divider';
import { map } from 'rxjs/operators';
import { DASHBOARD_METRICS_QUERY } from '../../graphql/queries';
import { DashboardMetrics } from '../../shared/models';
import { selectCurrentUser } from '../../store/auth/auth.selectors';

@Component({
  selector: 'sha-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatDividerModule],
  template: `
    <div class="page-header">
      <h1>Welcome back, {{ (user$ | async)?.username }}!</h1>
      <p>Here's what's happening in your hiring pipeline today.</p>
    </div>

    <ng-container *ngIf="metrics$ | async as m">
      <!-- Stat cards -->
      <div class="stats-grid">
        <div class="stat-card">
          <mat-icon class="stat-icon" style="color:#3f51b5">work</mat-icon>
          <div><div class="stat-value">{{ m.openJobs }}</div><div class="stat-label">Open Jobs</div></div>
        </div>
        <div class="stat-card">
          <mat-icon class="stat-icon" style="color:#4caf50">people</mat-icon>
          <div><div class="stat-value">{{ m.totalCandidates }}</div><div class="stat-label">Total Candidates</div></div>
        </div>
        <div class="stat-card">
          <mat-icon class="stat-icon" style="color:#ff9800">fact_check</mat-icon>
          <div><div class="stat-value">{{ m.pendingScreenings }}</div><div class="stat-label">Pending Screenings</div></div>
        </div>
        <div class="stat-card">
          <mat-icon class="stat-icon" style="color:#e91e63">trending_up</mat-icon>
          <div><div class="stat-value">{{ (m.hireRate * 100).toFixed(1) }}%</div><div class="stat-label">Hire Rate</div></div>
        </div>
      </div>

      <div class="two-col">
        <!-- Stage pass rates -->
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Stage Pass Rates</mat-card-title></mat-card-header>
          <mat-card-content>
            <div *ngFor="let s of m.stagePassRates" class="stage-row">
              <div class="stage-label">{{ s.stage | titlecase }}</div>
              <mat-progress-bar mode="determinate" [value]="s.passRate * 100" color="primary"></mat-progress-bar>
              <span class="stage-pct">{{ (s.passRate * 100).toFixed(0) }}%</span>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Top skills in demand -->
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Top Skills in Demand</mat-card-title></mat-card-header>
          <mat-card-content>
            <div *ngFor="let sk of m.topSkillsInDemand" class="skill-row">
              <span class="chip chip-blue">{{ sk.skill }}</span>
              <span class="skill-count">{{ sk.count }} jobs</span>
            </div>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Recent activity -->
      <mat-card class="panel mt-4">
        <mat-card-header><mat-card-title>Recent Activity</mat-card-title></mat-card-header>
        <mat-card-content>
          <div *ngFor="let a of m.recentActivity" class="activity-row">
            <mat-icon class="act-icon">{{ actIcon(a.type) }}</mat-icon>
            <div><div class="act-desc">{{ a.description }}</div><div class="act-time">{{ a.timestamp | date:'short' }}</div></div>
          </div>
        </mat-card-content>
      </mat-card>
    </ng-container>
  `,
  styles: [`
    .stats-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:1rem; margin-bottom:1.5rem; }
    .two-col { display:grid; grid-template-columns:1fr 1fr; gap:1rem; }
    .panel { border-radius:12px!important; }
    .stage-row { display:flex; align-items:center; gap:.75rem; margin:.6rem 0; .stage-label{width:100px;font-size:.85rem;} .stage-pct{font-size:.8rem;width:36px;text-align:right;} mat-progress-bar{flex:1;} }
    .skill-row { display:flex; align-items:center; justify-content:space-between; padding:.4rem 0; border-bottom:1px solid #f0f0f0; }
    .skill-count { font-size:.8rem; color:#666; }
    .activity-row { display:flex; align-items:flex-start; gap:.75rem; padding:.6rem 0; border-bottom:1px solid #f5f5f5; }
    .act-icon { color:#3f51b5; font-size:1.3rem; margin-top:.1rem; }
    .act-desc { font-size:.88rem; }
    .act-time { font-size:.78rem; color:#999; }
  `],
})
export class DashboardComponent implements OnInit {
  private apollo = inject(Apollo);
  private store  = inject(Store);

  user$    = this.store.select(selectCurrentUser);
  metrics$ = this.apollo.query<any>({ query: DASHBOARD_METRICS_QUERY })
               .pipe(map(r => r.data.dashboardMetrics as DashboardMetrics));

  ngOnInit() {}

  actIcon(type: string): string {
    const map: Record<string, string> = {
      NEW_CANDIDATE: 'person_add', SCREENING_COMPLETED: 'check_circle',
      NEW_JOB: 'work', MATCH_FOUND: 'star',
    };
    return map[type] ?? 'notifications';
  }
}


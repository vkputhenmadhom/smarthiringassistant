import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { map } from 'rxjs/operators';
import { DASHBOARD_METRICS_QUERY } from '../../graphql/queries';
import { DashboardMetrics } from '../../shared/models';

@Component({
  selector: 'sha-analytics',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatProgressBarModule],
  template: `
    <div class="page-header"><h1>Analytics</h1><p>Pipeline metrics and hiring insights</p></div>
    <ng-container *ngIf="metrics$ | async as m">

      <!-- KPI cards -->
      <div class="kpi-grid">
        <div class="stat-card"><mat-icon class="stat-icon" style="color:#3f51b5">work</mat-icon>
          <div><div class="stat-value">{{ m.totalJobs }}</div><div class="stat-label">Total Jobs</div></div></div>
        <div class="stat-card"><mat-icon class="stat-icon" style="color:#4caf50">people</mat-icon>
          <div><div class="stat-value">{{ m.totalCandidates }}</div><div class="stat-label">Candidates</div></div></div>
        <div class="stat-card"><mat-icon class="stat-icon" style="color:#ff9800">check_circle</mat-icon>
          <div><div class="stat-value">{{ m.completedScreenings }}</div><div class="stat-label">Screenings Done</div></div></div>
        <div class="stat-card"><mat-icon class="stat-icon" style="color:#e91e63">trending_up</mat-icon>
          <div><div class="stat-value">{{ (m.hireRate*100).toFixed(1) }}%</div><div class="stat-label">Hire Rate</div></div></div>
        <div class="stat-card"><mat-icon class="stat-icon" style="color:#9c27b0">star</mat-icon>
          <div><div class="stat-value">{{ (m.averageMatchScore*100).toFixed(1) }}%</div><div class="stat-label">Avg Match Score</div></div></div>
      </div>

      <div class="two-col mt-4">
        <!-- Stage funnel -->
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Screening Funnel</mat-card-title></mat-card-header>
          <mat-card-content>
            <div *ngFor="let s of m.stagePassRates" class="funnel-row">
              <div class="funnel-label">{{ s.stage | titlecase }}</div>
              <mat-progress-bar mode="determinate" [value]="s.passRate*100" color="primary" style="flex:1"></mat-progress-bar>
              <span class="funnel-pct">{{ (s.passRate*100).toFixed(0) }}% pass</span>
              <span class="funnel-count">({{ s.totalCount }})</span>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Skills heatmap -->
        <mat-card class="panel">
          <mat-card-header><mat-card-title>Skills in Demand</mat-card-title></mat-card-header>
          <mat-card-content>
            <table mat-table [dataSource]="m.topSkillsInDemand">
              <ng-container matColumnDef="skill">
                <th mat-header-cell *matHeaderCellDef>Skill</th>
                <td mat-cell *matCellDef="let s"><span class="chip chip-blue">{{ s.skill }}</span></td>
              </ng-container>
              <ng-container matColumnDef="count">
                <th mat-header-cell *matHeaderCellDef>Job Count</th>
                <td mat-cell *matCellDef="let s"><strong>{{ s.count }}</strong></td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['skill','count']"></tr>
              <tr mat-row *matRowDef="let r; columns:['skill','count'];"></tr>
            </table>
          </mat-card-content>
        </mat-card>
      </div>
    </ng-container>
  `,
  styles: [`
    .kpi-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(200px,1fr)); gap:1rem; }
    .two-col  { display:grid; grid-template-columns:1fr 1fr; gap:1rem; }
    .funnel-row { display:flex; align-items:center; gap:.6rem; margin:.5rem 0; }
    .funnel-label { width:90px; font-size:.83rem; }
    .funnel-pct   { font-size:.8rem; width:60px; text-align:right; }
    .funnel-count { font-size:.78rem; color:#888; }
  `],
})
export class AnalyticsComponent implements OnInit {
  private apollo = inject(Apollo);
  metrics$ = this.apollo.query<any>({ query: DASHBOARD_METRICS_QUERY }).pipe(map(r => r.data.dashboardMetrics as DashboardMetrics));
  ngOnInit() {}
}


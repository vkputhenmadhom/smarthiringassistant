import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { loadCandidates, selectCandidate } from '../../../store/candidates/candidates.actions';
import { selectAllCandidates, selectCandidatesLoading, selectCandidatesTotal } from '../../../store/candidates/candidates.selectors';

@Component({
  selector: 'sha-candidates-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule,
    MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatPaginatorModule, MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-header">
      <h1>Candidates</h1><p>Review parsed resumes and candidate profiles</p>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [(ngModel)]="search" (ngModelChange)="load()" placeholder="Name, email, skills…" />
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Screening Status</mat-label>
        <mat-select [(ngModel)]="statusFilter" (ngModelChange)="load()">
          <mat-option value="">All</mat-option>
          <mat-option value="NOT_STARTED">Not Started</mat-option>
          <mat-option value="IN_PROGRESS">In Progress</mat-option>
          <mat-option value="PASSED">Passed</mat-option>
          <mat-option value="FAILED">Failed</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    <mat-spinner *ngIf="loading$ | async" diameter="40" class="center-spinner"></mat-spinner>

    <table mat-table [dataSource]="candidates$ | async" class="sha-table" *ngIf="!(loading$ | async)">
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let c">
          <a [routerLink]="c.id" class="candidate-link">{{ c.name }}</a>
          <div class="sub">{{ c.email }}</div>
        </td>
      </ng-container>
      <ng-container matColumnDef="skills">
        <th mat-header-cell *matHeaderCellDef>Skills</th>
        <td mat-cell *matCellDef="let c">
          <span *ngFor="let s of (c.skills|slice:0:4)" class="chip chip-blue" style="margin:.1rem">{{ s }}</span>
          <span *ngIf="c.skills.length>4" class="chip chip-grey">+{{ c.skills.length-4 }}</span>
        </td>
      </ng-container>
      <ng-container matColumnDef="parseStatus">
        <th mat-header-cell *matHeaderCellDef>Parse</th>
        <td mat-cell *matCellDef="let c"><span [class]="parseChip(c.parseStatus)">{{ c.parseStatus }}</span></td>
      </ng-container>
      <ng-container matColumnDef="screeningStatus">
        <th mat-header-cell *matHeaderCellDef>Screening</th>
        <td mat-cell *matCellDef="let c"><span [class]="screenChip(c.screeningStatus)">{{ c.screeningStatus??'NOT_STARTED' }}</span></td>
      </ng-container>
      <ng-container matColumnDef="matchScore">
        <th mat-header-cell *matHeaderCellDef>Match Score</th>
        <td mat-cell *matCellDef="let c">{{ c.matchScore ? (c.matchScore*100).toFixed(0)+'%' : '—' }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let c">
          <button mat-icon-button [routerLink]="c.id"><mat-icon>visibility</mat-icon></button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let r; columns:cols;" (click)="select(r.id)"></tr>
    </table>

    <mat-paginator [length]="total$ | async" [pageSize]="pageSize" [pageSizeOptions]="[10,20,50]"
                   (page)="onPage($event)" showFirstLastButtons></mat-paginator>
  `,
  styles: [`
    .filter-bar{display:flex;gap:1rem;margin-bottom:1rem;}
    .candidate-link{font-weight:500;color:#3f51b5;text-decoration:none;}
    .sub{font-size:.78rem;color:#888;}
    .center-spinner{margin:3rem auto;display:block;}
  `],
})
export class CandidatesListComponent implements OnInit {
  private store = inject(Store);
  candidates$ = this.store.select(selectAllCandidates);
  loading$    = this.store.select(selectCandidatesLoading);
  total$      = this.store.select(selectCandidatesTotal);

  cols = ['name','skills','parseStatus','screeningStatus','matchScore','actions'];
  search = ''; statusFilter = ''; pageSize = 20; page = 0;

  ngOnInit() { this.load(); }
  load() { this.store.dispatch(loadCandidates({ page: this.page, size: this.pageSize, status: this.statusFilter||undefined, search: this.search||undefined })); }
  onPage(e: PageEvent) { this.page = e.pageIndex; this.pageSize = e.pageSize; this.load(); }
  select(id: string) { this.store.dispatch(selectCandidate({ id })); }

  parseChip(s: string)  { return s==='COMPLETED'?'chip chip-green':s==='FAILED'?'chip chip-red':'chip chip-orange'; }
  screenChip(s: string) { return s==='PASSED'?'chip chip-green':s==='FAILED'?'chip chip-red':s==='IN_PROGRESS'?'chip chip-orange':'chip chip-grey'; }
}


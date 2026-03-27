import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { loadJobs, deleteJob, publishJob, selectJob } from '../../../store/jobs/jobs.actions';
import { selectAllJobs, selectJobsLoading, selectJobsTotalCount } from '../../../store/jobs/jobs.selectors';
import { Job } from '../../../shared/models';

@Component({
  selector: 'sha-jobs-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule,
    MatTableModule, MatButtonModule, MatIconModule, MatInputModule,
    MatFormFieldModule, MatSelectModule, MatProgressSpinnerModule,
    MatTooltipModule, MatPaginatorModule,
  ],
  template: `
    <div class="page-header flex justify-between items-center">
      <div><h1>Jobs</h1><p>Manage job postings and view applicants</p></div>
      <a mat-raised-button color="primary" routerLink="new">
        <mat-icon>add</mat-icon> Post New Job
      </a>
    </div>

    <!-- Filters -->
    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [(ngModel)]="search" (ngModelChange)="applyFilter()" placeholder="Title, skills…" />
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [(ngModel)]="statusFilter" (ngModelChange)="applyFilter()">
          <mat-option value="">All</mat-option>
          <mat-option value="OPEN">Open</mat-option>
          <mat-option value="DRAFT">Draft</mat-option>
          <mat-option value="CLOSED">Closed</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    <mat-spinner *ngIf="loading$ | async" diameter="40" class="center-spinner"></mat-spinner>

    <table mat-table [dataSource]="jobs$ | async" class="sha-table" *ngIf="!(loading$ | async)">
      <ng-container matColumnDef="title">
        <th mat-header-cell *matHeaderCellDef>Title</th>
        <td mat-cell *matCellDef="let j">
          <a [routerLink]="j.id" class="job-link">{{ j.title }}</a>
          <div class="job-sub">{{ j.department }} · {{ j.location }}</div>
        </td>
      </ng-container>
      <ng-container matColumnDef="type">
        <th mat-header-cell *matHeaderCellDef>Type</th>
        <td mat-cell *matCellDef="let j"><span class="chip chip-blue">{{ j.type }}</span></td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let j"><span [class]="statusChip(j.status)">{{ j.status }}</span></td>
      </ng-container>
      <ng-container matColumnDef="salary">
        <th mat-header-cell *matHeaderCellDef>Salary Range</th>
        <td mat-cell *matCellDef="let j">{{ salaryLabel(j) }}</td>
      </ng-container>
      <ng-container matColumnDef="applicants">
        <th mat-header-cell *matHeaderCellDef>Applicants</th>
        <td mat-cell *matCellDef="let j">{{ j.applicantCount ?? 0 }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let j">
          <button mat-icon-button [routerLink]="[j.id,'edit']" matTooltip="Edit"><mat-icon>edit</mat-icon></button>
          <button mat-icon-button color="primary" (click)="publish(j)" *ngIf="j.status==='DRAFT'" matTooltip="Publish"><mat-icon>publish</mat-icon></button>
          <button mat-icon-button color="warn" (click)="delete(j.id)" matTooltip="Delete"><mat-icon>delete</mat-icon></button>
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols;" (click)="select(row.id)"></tr>
    </table>

    <mat-paginator [length]="total$ | async" [pageSize]="pageSize" [pageSizeOptions]="[10,20,50]"
                   (page)="onPage($event)" showFirstLastButtons></mat-paginator>
  `,
  styles: [`
    .filter-bar{ display:flex; gap:1rem; margin-bottom:1rem; }
    .job-link  { font-weight:500; color:#3f51b5; text-decoration:none; }
    .job-sub   { font-size:.78rem; color:#888; margin-top:.1rem; }
    .center-spinner{ margin:3rem auto; display:block; }
    table{ margin-bottom:.5rem; }
  `],
})
export class JobsListComponent implements OnInit {
  private store = inject(Store);
  jobs$    = this.store.select(selectAllJobs);
  loading$ = this.store.select(selectJobsLoading);
  total$   = this.store.select(selectJobsTotalCount);

  cols     = ['title','type','status','salary','applicants','actions'];
  search   = '';
  statusFilter = '';
  pageSize = 20;
  page     = 0;

  ngOnInit() { this.load(); }

  load() {
    this.store.dispatch(loadJobs({ page: this.page, size: this.pageSize, status: this.statusFilter || undefined, search: this.search || undefined }));
  }
  applyFilter() { this.page = 0; this.load(); }
  onPage(e: PageEvent) { this.page = e.pageIndex; this.pageSize = e.pageSize; this.load(); }
  select(id: string) { this.store.dispatch(selectJob({ id })); }
  publish(j: Job)    { this.store.dispatch(publishJob({ id: j.id })); }
  delete(id: string) { if (confirm('Delete this job?')) this.store.dispatch(deleteJob({ id })); }

  statusChip(s: string) { return s==='OPEN'?'chip chip-green':s==='DRAFT'?'chip chip-orange':'chip chip-grey'; }
  salaryLabel(j: Job)   { return j.salaryMin ? `${j.salaryCurrency??'USD'} ${(j.salaryMin/1000).toFixed(0)}k–${((j.salaryMax??0)/1000).toFixed(0)}k` : '—'; }
}


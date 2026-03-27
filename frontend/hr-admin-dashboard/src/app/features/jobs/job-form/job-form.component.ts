import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { MatChipInputEvent } from '@angular/material/chips';
import { createJob, updateJob, loadJob } from '../../../store/jobs/jobs.actions';
import { selectSelectedJob } from '../../../store/jobs/jobs.selectors';
import { take } from 'rxjs/operators';

@Component({
  selector: 'sha-job-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatChipsModule, MatIconModule, MatCardModule,
  ],
  template: `
    <div class="page-header">
      <h1>{{ isEdit ? 'Edit Job' : 'Post New Job' }}</h1>
    </div>

    <mat-card class="form-card">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="two-col">
            <mat-form-field appearance="outline">
              <mat-label>Job Title *</mat-label>
              <input matInput formControlName="title" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Department</mat-label>
              <input matInput formControlName="department" />
            </mat-form-field>
          </div>

          <mat-form-field appearance="outline" class="w-full">
            <mat-label>Description *</mat-label>
            <textarea matInput formControlName="description" rows="5"></textarea>
          </mat-form-field>

          <div class="two-col">
            <mat-form-field appearance="outline">
              <mat-label>Location</mat-label>
              <input matInput formControlName="location" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Job Type</mat-label>
              <mat-select formControlName="type">
                <mat-option *ngFor="let t of jobTypes" [value]="t">{{ t }}</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <!-- Skills chips -->
          <mat-form-field appearance="outline" class="w-full">
            <mat-label>Required Skills</mat-label>
            <mat-chip-grid #chipGrid>
              <mat-chip-row *ngFor="let s of skills" (removed)="removeSkill(s)">
                {{ s }} <button matChipRemove><mat-icon>cancel</mat-icon></button>
              </mat-chip-row>
              <input placeholder="Add skill…" [matChipInputFor]="chipGrid"
                     [matChipInputSeparatorKeyCodes]="separatorKeys"
                     (matChipInputTokenEnd)="addSkill($event)" />
            </mat-chip-grid>
          </mat-form-field>

          <div class="three-col">
            <mat-form-field appearance="outline">
              <mat-label>Salary Min</mat-label>
              <input matInput type="number" formControlName="salaryMin" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Salary Max</mat-label>
              <input matInput type="number" formControlName="salaryMax" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Currency</mat-label>
              <mat-select formControlName="salaryCurrency">
                <mat-option value="USD">USD</mat-option>
                <mat-option value="EUR">EUR</mat-option>
                <mat-option value="GBP">GBP</mat-option>
                <mat-option value="INR">INR</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="actions">
            <button mat-stroked-button type="button" (click)="cancel()">Cancel</button>
            <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">
              {{ isEdit ? 'Update Job' : 'Create Job' }}
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .form-card { max-width:800px; border-radius:12px!important; }
    .two-col   { display:grid; grid-template-columns:1fr 1fr; gap:1rem; }
    .three-col { display:grid; grid-template-columns:1fr 1fr 1fr; gap:1rem; }
    .actions   { display:flex; justify-content:flex-end; gap:1rem; margin-top:1rem; }
    mat-form-field { display:block; margin-bottom:.5rem; }
  `],
})
export class JobFormComponent implements OnInit {
  private store  = inject(Store);
  private fb     = inject(FormBuilder);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  isEdit        = false;
  jobId: string | null = null;
  skills: string[] = [];
  separatorKeys = [ENTER, COMMA];
  jobTypes = ['FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','REMOTE'];

  form = this.fb.group({
    title:          ['', Validators.required],
    description:    ['', Validators.required],
    department:     [''],
    location:       [''],
    type:           ['FULL_TIME'],
    salaryMin:      [null as number | null],
    salaryMax:      [null as number | null],
    salaryCurrency: ['USD'],
  });

  ngOnInit() {
    this.jobId = this.route.snapshot.paramMap.get('id');
    if (this.jobId) {
      this.isEdit = true;
      this.store.dispatch(loadJob({ id: this.jobId }));
      this.store.select(selectSelectedJob).pipe(take(1)).subscribe(job => {
        if (job) {
          this.form.patchValue({ ...job });
          this.skills = [...job.skills];
        }
      });
    }
  }

  addSkill(e: MatChipInputEvent) {
    const v = (e.value || '').trim();
    if (v) this.skills.push(v);
    e.chipInput!.clear();
  }
  removeSkill(s: string) { this.skills = this.skills.filter(x => x !== s); }

  submit() {
    const input = { ...this.form.value, skills: this.skills };
    if (this.isEdit && this.jobId) {
      this.store.dispatch(updateJob({ id: this.jobId, input }));
    } else {
      this.store.dispatch(createJob({ input }));
    }
    this.router.navigate(['/app/jobs']);
  }

  cancel() { this.router.navigate(['/app/jobs']); }
}


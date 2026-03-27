import { Routes } from '@angular/router';

export const jobsRoutes: Routes = [
  { path: '',    loadComponent: () => import('./jobs-list/jobs-list.component').then(m => m.JobsListComponent) },
  { path: 'new', loadComponent: () => import('./job-form/job-form.component').then(m => m.JobFormComponent) },
  { path: ':id', loadComponent: () => import('./job-detail/job-detail.component').then(m => m.JobDetailComponent) },
  { path: ':id/edit', loadComponent: () => import('./job-form/job-form.component').then(m => m.JobFormComponent) },
];


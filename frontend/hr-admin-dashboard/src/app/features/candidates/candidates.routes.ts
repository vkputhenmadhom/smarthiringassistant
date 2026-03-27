import { Routes } from '@angular/router';
export const candidatesRoutes: Routes = [
  { path: '',    loadComponent: () => import('./candidates-list/candidates-list.component').then(m => m.CandidatesListComponent) },
  { path: ':id', loadComponent: () => import('./candidate-detail/candidate-detail.component').then(m => m.CandidateDetailComponent) },
];

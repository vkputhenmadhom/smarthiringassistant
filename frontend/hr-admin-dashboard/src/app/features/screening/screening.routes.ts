import { Routes } from '@angular/router';
export const screeningRoutes: Routes = [
  { path: '',    loadComponent: () => import('./screening-list/screening-list.component').then(m => m.ScreeningListComponent) },
  { path: ':id', loadComponent: () => import('./screening-detail/screening-detail.component').then(m => m.ScreeningDetailComponent) },
];

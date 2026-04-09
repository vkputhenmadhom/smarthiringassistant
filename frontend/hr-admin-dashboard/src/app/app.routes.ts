import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const appRoutes: Routes = [
  { path: '', redirectTo: 'app/dashboard', pathMatch: 'full' },

  // Auth (public)
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then(m => m.authRoutes),
  },

  // Protected shell
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'jobs',
        loadChildren: () => import('./features/jobs/jobs.routes').then(m => m.jobsRoutes),
        canActivate: [roleGuard],
        data: { roles: ['HR_ADMIN', 'RECRUITER', 'SUPER_ADMIN'] },
      },
      {
        path: 'candidates',
        loadChildren: () => import('./features/candidates/candidates.routes').then(m => m.candidatesRoutes),
        canActivate: [roleGuard],
        data: { roles: ['HR_ADMIN', 'RECRUITER', 'SUPER_ADMIN'] },
      },
      {
        path: 'screening',
        loadChildren: () => import('./features/screening/screening.routes').then(m => m.screeningRoutes),
        canActivate: [roleGuard],
        data: { roles: ['HR_ADMIN', 'RECRUITER', 'SUPER_ADMIN'] },
      },
      {
        path: 'notifications',
        loadChildren: () => import('./features/notifications/notifications.routes').then(m => m.notificationsRoutes),
        canActivate: [roleGuard],
        data: { roles: ['HR_ADMIN', 'RECRUITER', 'SUPER_ADMIN'] },
      },
      {
        path: 'analytics',
        loadComponent: () => import('./features/analytics/analytics.component').then(m => m.AnalyticsComponent),
        canActivate: [roleGuard],
        data: { roles: ['HR_ADMIN', 'RECRUITER', 'SUPER_ADMIN'] },
      },
    ],
  },

  { path: '**', redirectTo: 'app/dashboard' },
];


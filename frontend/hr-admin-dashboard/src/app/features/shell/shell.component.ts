import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Store } from '@ngrx/store';
import { map } from 'rxjs/operators';
import { take } from 'rxjs/operators';
import { MatSidenav } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { selectCurrentUser } from '../../store/auth/auth.selectors';
import { logout } from '../../store/auth/auth.actions';

interface NavItem { label: string; icon: string; route: string; roles?: string[]; }

@Component({
  selector: 'sha-shell',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule,
    MatIconModule, MatButtonModule, MatMenuModule, MatBadgeModule,
  ],
  template: `
    <mat-sidenav-container class="sidenav-container">

      <!-- Sidebar -->
      <mat-sidenav #drawer [mode]="(isMobile$ | async) ? 'over' : 'side'" [opened]="!(isMobile$ | async)" class="sidenav">
        <div class="brand-area">
          <mat-icon class="brand-icon">work</mat-icon>
          <span class="brand-name">SHA</span>
          <span class="brand-sub">HR Dashboard</span>
        </div>

        <mat-nav-list>
          <a mat-list-item *ngFor="let item of navItems"
             [routerLink]="item.route" routerLinkActive="active-link" (click)="closeOnMobile(drawer)">
            <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
            <span matListItemTitle>{{ item.label }}</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <!-- Main content -->
      <mat-sidenav-content class="main-content">
        <!-- Top toolbar -->
        <mat-toolbar color="primary" class="top-bar">
          <button mat-icon-button *ngIf="isMobile$ | async" (click)="drawer.toggle()" aria-label="Toggle navigation">
            <mat-icon>menu</mat-icon>
          </button>
          <span class="toolbar-spacer"></span>
          <button mat-icon-button [matMenuTriggerFor]="userMenu">
            <mat-icon>account_circle</mat-icon>
          </button>
          <mat-menu #userMenu>
            <div class="user-info-menu" *ngIf="user$ | async as user">
              <strong>{{ user.username }}</strong>
              <span>{{ user.email }}</span>
              <span class="chip chip-blue">{{ user.role }}</span>
            </div>
            <button mat-menu-item (click)="logout()">
              <mat-icon>logout</mat-icon> Sign out
            </button>
          </mat-menu>
        </mat-toolbar>

        <div class="content-area">
          <router-outlet />
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .sidenav-container { height:100vh; }
    .sidenav { width:220px; background:#1a237e; color:#fff; border:none; display:flex; flex-direction:column; }
    .brand-area { padding:1.5rem 1rem 1rem; display:flex; flex-direction:column; align-items:flex-start; border-bottom:1px solid rgba(255,255,255,.15); margin-bottom:.5rem; }
    .brand-icon { font-size:2.2rem; color:#7986cb; }
    .brand-name { font-size:1.2rem; font-weight:700; color:#fff; line-height:1; }
    .brand-sub  { font-size:.7rem; color:#9fa8da; text-transform:uppercase; letter-spacing:1px; }
    mat-nav-list a { color:rgba(255,255,255,.8)!important; border-radius:8px!important; margin:.15rem .5rem; }
    mat-nav-list a:hover, mat-nav-list a.active-link { background:rgba(255,255,255,.15)!important; color:#fff!important; }
    .main-content { display:flex; flex-direction:column; }
    .top-bar { box-shadow:0 2px 8px rgba(0,0,0,.12); z-index:10; }
    .toolbar-spacer { flex:1; }
    .content-area { flex:1; padding:2rem; overflow:auto; background:#f5f6fa; }
    .user-info-menu { padding:.75rem 1rem; display:flex; flex-direction:column; gap:.25rem; border-bottom:1px solid #eee; margin-bottom:.25rem; }
    @media (max-width: 960px) {
      .sidenav { width:260px; }
      .content-area { padding:1rem; }
    }
  `],
})
export class ShellComponent implements OnInit {
  private store = inject(Store);
  private breakpointObserver = inject(BreakpointObserver);
  user$ = this.store.select(selectCurrentUser);
  isMobile$ = this.breakpointObserver.observe('(max-width: 960px)').pipe(map(state => state.matches));

  navItems: NavItem[] = [
    { label: 'Dashboard',   icon: 'dashboard',   route: '/app/dashboard'  },
    { label: 'Jobs',        icon: 'work',         route: '/app/jobs'       },
    { label: 'Candidates',  icon: 'people',       route: '/app/candidates' },
    { label: 'Screening',   icon: 'fact_check',   route: '/app/screening'  },
    { label: 'Analytics',   icon: 'bar_chart',    route: '/app/analytics'  },
  ];

  ngOnInit() {}

  closeOnMobile(drawer: MatSidenav): void {
    this.isMobile$.pipe(take(1)).subscribe(isMobile => {
      if (isMobile) {
        drawer.close();
      }
    });
  }

  logout() { this.store.dispatch(logout()); }
}


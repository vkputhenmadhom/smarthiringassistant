import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Store } from '@ngrx/store';
import { Apollo, gql } from 'apollo-angular';
import { map, filter, distinctUntilChanged, takeUntil } from 'rxjs/operators';
import { take } from 'rxjs/operators';
import { Subject } from 'rxjs';
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
import {
  MARK_ALL_READ_MUTATION,
  MY_NOTIFICATIONS_QUERY,
  NEW_NOTIFICATION_SUBSCRIPTION,
} from '../../graphql/queries/index';
import { User } from '../../shared/models';

interface NavItem { label: string; icon: string; route: string; roles?: string[]; }
interface NotificationItem { id: string; userId?: string; type: string; title: string; message: string; read: boolean; createdAt?: string; }
const MAX_VISIBLE_NOTIFICATIONS = 8;
const MARK_NOTIFICATION_READ_MUTATION = gql`
  mutation MarkNotificationRead($id: ID!) {
    markNotificationRead(id: $id) {
      id
      read
    }
  }
`;

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
          <button mat-icon-button [matMenuTriggerFor]="notificationsMenu" [matBadge]="notificationCount" [matBadgeHidden]="notificationCount === 0" matBadgeColor="warn" aria-label="Notifications">
            <mat-icon>notifications</mat-icon>
          </button>
          <mat-menu #notificationsMenu="matMenu" xPosition="before" class="notifications-menu">
            <div class="notifications-header">
              <strong>Notifications</strong>
              <button mat-button color="primary" (click)="markAllAsRead($event)" [disabled]="notificationCount === 0">Mark all read</button>
            </div>
            <div *ngIf="notifications.length === 0" class="notifications-empty">
              You are all caught up.
            </div>
            <div class="notifications-list">
              <div *ngFor="let notification of notifications" class="notification-item" [class.notification-item-unread]="!notification.read">
                <div class="notification-title-row">
                  <strong>{{ notification.title }}</strong>
                  <button
                    mat-icon-button
                    *ngIf="!notification.read"
                    (click)="markAsRead(notification, $event)"
                    aria-label="Mark notification as read"
                  >
                    <mat-icon>done</mat-icon>
                  </button>
                </div>
                <div class="notification-message">{{ notification.message }}</div>
                <div class="notification-time">{{ formatNotificationTime(notification.createdAt) }}</div>
              </div>
            </div>
            <button mat-menu-item routerLink="/app/notifications">
              <mat-icon>list</mat-icon>
              View all notifications
            </button>
          </mat-menu>
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
    .notifications-header { width:320px; display:flex; justify-content:space-between; align-items:center; padding:.25rem .75rem .5rem; border-bottom:1px solid #eee; }
    .notifications-empty { width:320px; padding:1rem .75rem; color:#666; }
    .notifications-list { max-height:320px; overflow:auto; }
    .notification-item { width:320px; padding:.6rem .75rem; border-bottom:1px solid #f1f1f1; }
    .notification-item-unread { background:#f3f7ff; }
    .notification-title-row { display:flex; justify-content:space-between; align-items:flex-start; gap:.5rem; }
    .notification-message { font-size:.85rem; color:#333; margin-top:.15rem; }
    .notification-time { font-size:.75rem; color:#666; margin-top:.3rem; }
    @media (max-width: 960px) {
      .sidenav { width:260px; }
      .content-area { padding:1rem; }
    }
  `],
})
export class ShellComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private breakpointObserver = inject(BreakpointObserver);
  private apollo = inject(Apollo);
  private destroy$ = new Subject<void>();
  private notificationFeedDestroy$ = new Subject<void>();
  user$ = this.store.select(selectCurrentUser);
  isMobile$ = this.breakpointObserver.observe('(max-width: 960px)').pipe(map(state => state.matches));
  notificationCount = 0;
  notifications: NotificationItem[] = [];
  private unreadNotificationIds = new Set<string>();

  navItems: NavItem[] = [
    { label: 'Dashboard',   icon: 'dashboard',   route: '/app/dashboard'  },
    { label: 'Jobs',        icon: 'work',         route: '/app/jobs'       },
    { label: 'Candidates',  icon: 'people',       route: '/app/candidates' },
    { label: 'Screening',   icon: 'fact_check',   route: '/app/screening'  },
    { label: 'Analytics',   icon: 'bar_chart',    route: '/app/analytics'  },
  ];

  ngOnInit() {
    this.user$
      .pipe(
        filter((user): user is User => !!user),
        distinctUntilChanged((previous, current) => previous.id === current.id),
        takeUntil(this.destroy$),
      )
      .subscribe(user => this.startNotificationFeed(String(user.id)));
  }

  closeOnMobile(drawer: MatSidenav): void {
    this.isMobile$.pipe(take(1)).subscribe(isMobile => {
      if (isMobile) {
        drawer.close();
      }
    });
  }

  logout() { this.store.dispatch(logout()); }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.notificationFeedDestroy$.next();
    this.notificationFeedDestroy$.complete();
  }

  markAsRead(notification: NotificationItem, event: Event): void {
    event.stopPropagation();
    if (notification.read) {
      return;
    }

    this.applyMarkedRead(notification.id);
    this.apollo.mutate<{ markNotificationRead: NotificationItem }>({
      mutation: MARK_NOTIFICATION_READ_MUTATION,
      variables: { id: notification.id },
    })
      .pipe(take(1), takeUntil(this.notificationFeedDestroy$))
      .subscribe();
  }

  markAllAsRead(event: Event): void {
    event.stopPropagation();
    if (this.notificationCount === 0) {
      return;
    }

    this.notifications = this.notifications.map(notification => ({ ...notification, read: true }));
    this.unreadNotificationIds.clear();
    this.notificationCount = 0;

    this.apollo.mutate<{ markAllNotificationsRead: boolean }>({
      mutation: MARK_ALL_READ_MUTATION,
    })
      .pipe(take(1), takeUntil(this.notificationFeedDestroy$))
      .subscribe();
  }

  formatNotificationTime(createdAt?: string): string {
    if (!createdAt) {
      return 'Just now';
    }

    const timestamp = new Date(createdAt).getTime();
    if (Number.isNaN(timestamp)) {
      return 'Just now';
    }

    const diffMinutes = Math.floor((Date.now() - timestamp) / 60000);
    if (diffMinutes < 1) {
      return 'Just now';
    }
    if (diffMinutes < 60) {
      return `${diffMinutes}m ago`;
    }
    if (diffMinutes < 1440) {
      return `${Math.floor(diffMinutes / 60)}h ago`;
    }
    return `${Math.floor(diffMinutes / 1440)}d ago`;
  }

  private startNotificationFeed(userId: string): void {
    this.notificationFeedDestroy$.next();
    this.notifications = [];
    this.unreadNotificationIds.clear();
    this.notificationCount = 0;

    this.apollo.watchQuery<{ myNotifications: NotificationItem[] }>({
      query: MY_NOTIFICATIONS_QUERY,
      variables: { unreadOnly: false },
      fetchPolicy: 'network-only',
    }).valueChanges
      .pipe(take(1), takeUntil(this.notificationFeedDestroy$))
      .subscribe(({ data }) => {
        const notifications = (data?.myNotifications ?? []).slice(0, MAX_VISIBLE_NOTIFICATIONS);
        this.notifications = notifications;
        notifications
          .filter(notification => !notification.read)
          .forEach(notification => this.unreadNotificationIds.add(notification.id));
        this.notificationCount = this.unreadNotificationIds.size;
      });

    this.apollo.subscribe<{ newNotification: NotificationItem }>({
      query: NEW_NOTIFICATION_SUBSCRIPTION,
      variables: { userId },
    })
      .pipe(takeUntil(this.notificationFeedDestroy$))
      .subscribe(({ data }) => {
        const notification = data?.newNotification;
        if (!notification || notification.read) {
          return;
        }
        this.upsertNotification(notification);
      });
  }

  private applyMarkedRead(notificationId: string): void {
    this.notifications = this.notifications.map(notification =>
      notification.id === notificationId ? { ...notification, read: true } : notification,
    );
    this.unreadNotificationIds.delete(notificationId);
    this.notificationCount = this.unreadNotificationIds.size;
  }

  private upsertNotification(notification: NotificationItem): void {
    const withoutExisting = this.notifications.filter(item => item.id !== notification.id);
    this.notifications = [notification, ...withoutExisting].slice(0, MAX_VISIBLE_NOTIFICATIONS);
    if (!notification.read) {
      this.unreadNotificationIds.add(notification.id);
    }
    this.notificationCount = this.unreadNotificationIds.size;
  }
}

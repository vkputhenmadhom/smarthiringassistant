import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Apollo, gql } from 'apollo-angular';
import { Store } from '@ngrx/store';
import { Subject } from 'rxjs';
import { filter, takeUntil, take, distinctUntilChanged } from 'rxjs/operators';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import {
  MARK_ALL_READ_MUTATION,
  MY_NOTIFICATIONS_QUERY,
  NEW_NOTIFICATION_SUBSCRIPTION,
} from '../../../graphql/queries';
import { selectCurrentUser } from '../../../store/auth/auth.selectors';
import { User } from '../../../shared/models';

interface NotificationItem {
  id: string;
  userId?: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  createdAt?: string;
}

const MARK_NOTIFICATION_READ_MUTATION = gql`
  mutation MarkNotificationRead($id: ID!) {
    markNotificationRead(id: $id) {
      id
      read
    }
  }
`;

@Component({
  selector: 'sha-notifications-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
  ],
  template: `
    <div class="page-header flex justify-between items-center">
      <div>
        <h1>Notifications</h1>
        <p>Notification history with live updates.</p>
      </div>
      <div class="flex gap-2">
        <button mat-stroked-button color="primary" (click)="markAllAsRead()" [disabled]="unreadCount === 0">
          <mat-icon>done_all</mat-icon>
          Mark all read
        </button>
        <a mat-button routerLink="/app/dashboard">Back to dashboard</a>
      </div>
    </div>

    <mat-card class="panel">
      <mat-card-content>
        <div *ngIf="pagedNotifications.length === 0" class="empty-state">
          You are all caught up.
        </div>

        <div *ngFor="let notification of pagedNotifications" class="notification-item" [class.unread]="!notification.read">
          <div class="notification-top-row">
            <div>
              <strong>{{ notification.title }}</strong>
              <span class="type-chip">{{ notification.type }}</span>
            </div>
            <button mat-button color="primary" *ngIf="!notification.read" (click)="markAsRead(notification)">
              Mark as read
            </button>
          </div>
          <div class="notification-message">{{ notification.message }}</div>
          <div class="notification-time">{{ formatNotificationTime(notification.createdAt) }}</div>
        </div>

        <mat-paginator
          [length]="totalCount"
          [pageIndex]="pageIndex"
          [pageSize]="pageSize"
          [pageSizeOptions]="[10, 20, 50]"
          (page)="onPage($event)"
          showFirstLastButtons
        ></mat-paginator>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .notification-item { border-bottom: 1px solid #f0f0f0; padding: 0.85rem 0.25rem; }
    .notification-item.unread { background: #f3f7ff; }
    .notification-top-row { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
    .notification-message { margin-top: 0.35rem; color: #333; }
    .notification-time { margin-top: 0.35rem; font-size: 0.8rem; color: #666; }
    .type-chip { margin-left: 0.5rem; padding: 0.1rem 0.45rem; border-radius: 999px; background: #eef2ff; color: #334155; font-size: 0.72rem; }
    .empty-state { padding: 1rem; color: #666; }
  `],
})
export class NotificationsListComponent implements OnInit, OnDestroy {
  private store = inject(Store);
  private apollo = inject(Apollo);
  private destroy$ = new Subject<void>();
  private userId: string | null = null;

  notifications: NotificationItem[] = [];
  pagedNotifications: NotificationItem[] = [];
  totalCount = 0;
  unreadCount = 0;
  pageIndex = 0;
  pageSize = 10;

  ngOnInit(): void {
    this.store.select(selectCurrentUser)
      .pipe(
        filter((user): user is User => !!user),
        distinctUntilChanged((previous, current) => previous.id === current.id),
        takeUntil(this.destroy$),
      )
      .subscribe(user => {
        this.userId = user.id;
        this.startFeed(user.id);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePagedNotifications();
  }

  markAsRead(notification: NotificationItem): void {
    if (notification.read) {
      return;
    }

    this.notifications = this.notifications.map(item =>
      item.id === notification.id ? { ...item, read: true } : item,
    );
    this.updateCountersAndPage();

    this.apollo.mutate<{ markNotificationRead: NotificationItem }>({
      mutation: MARK_NOTIFICATION_READ_MUTATION,
      variables: { id: notification.id },
    })
      .pipe(take(1), takeUntil(this.destroy$))
      .subscribe();
  }

  markAllAsRead(): void {
    if (this.unreadCount === 0) {
      return;
    }

    this.notifications = this.notifications.map(notification => ({ ...notification, read: true }));
    this.updateCountersAndPage();

    this.apollo.mutate<{ markAllNotificationsRead: boolean }>({
      mutation: MARK_ALL_READ_MUTATION,
    })
      .pipe(take(1), takeUntil(this.destroy$))
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

  private startFeed(userId: string): void {
    this.apollo.watchQuery<{ myNotifications: NotificationItem[] }>({
      query: MY_NOTIFICATIONS_QUERY,
      variables: { unreadOnly: false },
      fetchPolicy: 'network-only',
    }).valueChanges
      .pipe(take(1), takeUntil(this.destroy$))
      .subscribe(({ data }) => {
        this.notifications = this.sortNotifications(data?.myNotifications ?? []);
        this.updateCountersAndPage();
      });

    this.apollo.subscribe<{ newNotification: NotificationItem }>({
      query: NEW_NOTIFICATION_SUBSCRIPTION,
      variables: { userId },
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ data }) => {
        const incoming = data?.newNotification;
        if (!incoming) {
          return;
        }
        this.upsertNotification(incoming);
      });
  }

  private sortNotifications(items: NotificationItem[]): NotificationItem[] {
    return [...items].sort((left, right) => {
      const leftTime = left.createdAt ? new Date(left.createdAt).getTime() : 0;
      const rightTime = right.createdAt ? new Date(right.createdAt).getTime() : 0;
      return rightTime - leftTime;
    });
  }

  private upsertNotification(notification: NotificationItem): void {
    const withoutExisting = this.notifications.filter(item => item.id !== notification.id);
    this.notifications = this.sortNotifications([notification, ...withoutExisting]);
    this.updateCountersAndPage();
  }

  private updateCountersAndPage(): void {
    this.totalCount = this.notifications.length;
    this.unreadCount = this.notifications.filter(notification => !notification.read).length;

    const maxPageIndex = Math.max(0, Math.ceil(this.totalCount / this.pageSize) - 1);
    if (this.pageIndex > maxPageIndex) {
      this.pageIndex = maxPageIndex;
    }

    this.updatePagedNotifications();
  }

  private updatePagedNotifications(): void {
    const start = this.pageIndex * this.pageSize;
    this.pagedNotifications = this.notifications.slice(start, start + this.pageSize);
  }
}


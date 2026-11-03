import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Apollo } from 'apollo-angular';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { APPROVE_USER_MUTATION, PENDING_USERS_QUERY, REJECT_USER_MUTATION } from '../../graphql/queries';
import { Role, User } from '../../shared/models';

/** Administrators approve or reject HR accounts that signed up and are waiting for access. */
@Component({
  selector: 'sha-approvals',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatTableModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatFormFieldModule, MatProgressSpinnerModule, MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h1>Account approvals</h1><p>HR accounts waiting for an administrator before they can sign in</p>
    </div>

    <mat-spinner *ngIf="loading" diameter="40" class="center-spinner"></mat-spinner>
    <div class="error-msg" *ngIf="error">{{ error }}</div>

    <p class="empty" *ngIf="!loading && !error && users.length === 0">
      <mat-icon>verified_user</mat-icon> No accounts are waiting for approval.
    </p>

    <table mat-table [dataSource]="users" class="sha-table" *ngIf="!loading && users.length > 0">
      <ng-container matColumnDef="user">
        <th mat-header-cell *matHeaderCellDef>User</th>
        <td mat-cell *matCellDef="let u">{{ u.username }}<div class="sub">{{ u.email }}</div></td>
      </ng-container>
      <ng-container matColumnDef="requested">
        <th mat-header-cell *matHeaderCellDef>Requested</th>
        <td mat-cell *matCellDef="let u">{{ u.createdAt ? (u.createdAt | date:'medium') : '—' }}</td>
      </ng-container>
      <ng-container matColumnDef="role">
        <th mat-header-cell *matHeaderCellDef>Grant role</th>
        <td mat-cell *matCellDef="let u">
          <mat-form-field appearance="outline" class="role-select">
            <mat-select [(ngModel)]="grantRole[u.id]" [attr.aria-label]="'Role for ' + u.username">
              <mat-option value="RECRUITER">Recruiter</mat-option>
              <mat-option value="HR_ADMIN">HR Admin</mat-option>
            </mat-select>
          </mat-form-field>
        </td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let u" class="actions">
          <button mat-flat-button color="primary" (click)="approve(u)" [disabled]="busy.has(u.id)">Approve</button>
          <button mat-stroked-button color="warn" (click)="reject(u)" [disabled]="busy.has(u.id)">Reject</button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
  `,
  styles: [`
    .sub{color:#777;font-size:.8rem;}
    .error-msg{color:#c62828;margin:1rem 0;}
    .empty{display:flex;gap:.5rem;align-items:center;color:#666;}
    .role-select{width:150px;margin:.5rem 0 -1rem;}
    .actions{display:flex;gap:.5rem;justify-content:flex-end;padding-top:.75rem;}
  `],
})
export class ApprovalsComponent implements OnInit {
  private apollo = inject(Apollo);
  private snackBar = inject(MatSnackBar);

  columns = ['user', 'requested', 'role', 'actions'];
  users: User[] = [];
  grantRole: Record<string, Role> = {};
  busy = new Set<string>();
  loading = false;
  error: string | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.apollo.query<{ pendingUsers: User[] }>({ query: PENDING_USERS_QUERY, fetchPolicy: 'network-only' })
      .subscribe({
        next: res => {
          this.users = res.data.pendingUsers;
          this.users.forEach(u => this.grantRole[u.id] ??= u.role === 'HR_ADMIN' ? 'HR_ADMIN' : 'RECRUITER');
          this.loading = false;
        },
        error: err => {
          this.error = err.message;
          this.loading = false;
        },
      });
  }

  approve(user: User): void {
    this.decide(user, APPROVE_USER_MUTATION, { id: user.id, role: this.grantRole[user.id] }, 'approved');
  }

  reject(user: User): void {
    this.decide(user, REJECT_USER_MUTATION, { id: user.id }, 'rejected');
  }

  private decide(user: User, mutation: any, variables: Record<string, unknown>, outcome: string): void {
    this.busy.add(user.id);
    this.apollo.mutate({ mutation, variables }).subscribe({
      next: () => {
        this.busy.delete(user.id);
        this.users = this.users.filter(u => u.id !== user.id);
        this.snackBar.open(`${user.username} ${outcome}`, undefined, { duration: 3000 });
      },
      error: err => {
        this.busy.delete(user.id);
        this.snackBar.open(err.message, 'Dismiss', { duration: 5000 });
      },
    });
  }
}

import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { login } from '../../../store/auth/auth.actions';
import { selectAuthLoading, selectAuthError } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'sha-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatProgressSpinnerModule, MatIconModule,
  ],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        <mat-card-header>
          <div class="brand">
            <mat-icon>work</mat-icon>
            <h1>Smart Hiring Assistant</h1>
          </div>
          <p class="subtitle">HR Admin Dashboard</p>
        </mat-card-header>

        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" autocomplete="username" />
              <mat-error *ngIf="form.get('username')?.hasError('required')">Username is required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Password</mat-label>
              <input matInput [type]="hide ? 'password' : 'text'" formControlName="password" autocomplete="current-password" />
              <button mat-icon-button matSuffix (click)="hide=!hide" type="button">
                <mat-icon>{{ hide ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-error *ngIf="form.get('password')?.hasError('required')">Password is required</mat-error>
            </mat-form-field>

            <div class="error-msg" *ngIf="error$ | async as err">{{ err }}</div>

            <button mat-raised-button color="primary" class="w-full submit-btn" type="submit"
                    [disabled]="form.invalid || (loading$ | async)">
              <mat-spinner diameter="20" *ngIf="loading$ | async" class="inline-spinner"></mat-spinner>
              <span *ngIf="!(loading$ | async)">Sign In</span>
            </button>
          </form>
        </mat-card-content>

        <mat-card-actions>
          <p class="register-link">Don't have an account? <a routerLink="/auth/register">Register</a></p>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-page { min-height:100vh; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#3f51b5,#5c6bc0); }
    .auth-card { width:420px; padding:2rem; border-radius:16px !important; }
    .brand     { display:flex; align-items:center; gap:.6rem; mat-icon { font-size:2rem; color:#3f51b5; } h1 { font-size:1.3rem; font-weight:700; } }
    .subtitle  { color:#666; font-size:.85rem; margin:.25rem 0 1.5rem; }
    .submit-btn{ margin-top:.5rem; height:44px; }
    .error-msg { color:#c62828; font-size:.85rem; margin-bottom:.5rem; }
    .register-link { text-align:center; font-size:.85rem; }
    .inline-spinner{ display:inline-block; }
    mat-form-field { display:block; margin-bottom:.5rem; }
  `],
})
export class LoginComponent {
  private store = inject(Store);
  private fb    = inject(FormBuilder);

  loading$ = this.store.select(selectAuthLoading);
  error$   = this.store.select(selectAuthError);
  hide     = true;

  form = this.fb.group({
    username: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  submit() {
    if (this.form.valid) {
      const { username, password } = this.form.value;
      this.store.dispatch(login({ username: username!, password: password! }));
    }
  }
}


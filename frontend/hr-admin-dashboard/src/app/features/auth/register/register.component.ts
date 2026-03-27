import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { register } from '../../../store/auth/auth.actions';
import { selectAuthLoading, selectAuthError } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'sha-register',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatSelectModule, MatIconModule,
  ],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        <mat-card-header>
          <div class="brand"><mat-icon>work</mat-icon><h1>Smart Hiring Assistant</h1></div>
          <p class="subtitle">Create your account</p>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Password</mat-label>
              <input matInput [type]="hide ? 'password':'text'" formControlName="password" />
              <button mat-icon-button matSuffix type="button" (click)="hide=!hide">
                <mat-icon>{{ hide ? 'visibility_off':'visibility' }}</mat-icon>
              </button>
            </mat-form-field>
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Role</mat-label>
              <mat-select formControlName="role">
                <mat-option value="HR_ADMIN">HR Admin</mat-option>
                <mat-option value="RECRUITER">Recruiter</mat-option>
                <mat-option value="CANDIDATE">Candidate</mat-option>
              </mat-select>
            </mat-form-field>
            <div class="error-msg" *ngIf="error$ | async as err">{{ err }}</div>
            <button mat-raised-button color="primary" class="w-full" type="submit" [disabled]="form.invalid || (loading$ | async)">
              Create Account
            </button>
          </form>
        </mat-card-content>
        <mat-card-actions>
          <p class="register-link">Already have an account? <a routerLink="/auth/login">Sign in</a></p>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-page{min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#3f51b5,#5c6bc0);}
    .auth-card{width:440px;padding:2rem;border-radius:16px!important;}
    .brand{display:flex;align-items:center;gap:.6rem;mat-icon{font-size:2rem;color:#3f51b5;}h1{font-size:1.3rem;font-weight:700;}}
    .subtitle{color:#666;font-size:.85rem;margin:.25rem 0 1.5rem;}
    .error-msg{color:#c62828;font-size:.85rem;margin-bottom:.5rem;}
    .register-link{text-align:center;font-size:.85rem;}
    mat-form-field{display:block;margin-bottom:.5rem;}
  `],
})
export class RegisterComponent {
  private store = inject(Store);
  private fb    = inject(FormBuilder);
  loading$      = this.store.select(selectAuthLoading);
  error$        = this.store.select(selectAuthError);
  hide          = true;

  form = this.fb.group({
    username: ['', Validators.required],
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    role:     ['HR_ADMIN', Validators.required],
  });

  submit() {
    if (this.form.valid) {
      const { username, email, password, role } = this.form.value;
      this.store.dispatch(register({ username: username!, email: email!, password: password!, role: role! }));
    }
  }
}


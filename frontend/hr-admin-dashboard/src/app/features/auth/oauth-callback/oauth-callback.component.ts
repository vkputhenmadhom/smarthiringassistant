import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { AuthPayload, Role, User } from '../../../shared/models';
import { loginSuccess } from '../../../store/auth/auth.actions';

@Component({
  selector: 'sha-oauth-callback',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatProgressSpinnerModule, MatButtonModule],
  template: `
    <div class="auth-page">
      <mat-card class="auth-card">
        <h2>Completing sign-in</h2>
        <p *ngIf="!error">Processing OAuth callback and preparing your session...</p>
        <mat-spinner diameter="28" *ngIf="!error"></mat-spinner>

        <div class="error-block" *ngIf="error">
          <p>{{ error }}</p>
          <button mat-raised-button color="primary" (click)="backToLogin()">Back to login</button>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .auth-page { min-height:100vh; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#3f51b5,#5c6bc0); padding:1rem; }
    .auth-card { width:min(460px, 96vw); padding:1.5rem; border-radius:16px !important; display:flex; flex-direction:column; gap:1rem; }
    .error-block { display:flex; flex-direction:column; gap:.75rem; }
  `],
})
export class OauthCallbackComponent {
  private route = inject(ActivatedRoute);
  private store = inject(Store);
  private router = inject(Router);

  error: string | null = null;

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const callbackError = params.get('error');
    if (callbackError) {
      this.error = 'OAuth sign-in failed. Please try again.';
      return;
    }

    const accessToken = params.get('accessToken');
    const refreshToken = params.get('refreshToken');
    const expiresInRaw = params.get('expiresIn');
    const callbackRole = params.get('role');

    if (!accessToken || !refreshToken || !expiresInRaw) {
      this.error = 'Missing OAuth callback parameters.';
      return;
    }

    const expiresIn = Number(expiresInRaw);
    const user = this.decodeJwtUser(accessToken, callbackRole);

    if (!user || !Number.isFinite(expiresIn) || expiresIn <= 0) {
      this.error = 'Invalid OAuth callback payload.';
      return;
    }

    const payload: AuthPayload = {
      token: accessToken,
      refreshToken,
      expiresIn,
      user,
    };

    this.store.dispatch(loginSuccess({ payload }));
    this.router.navigate(['/app/dashboard']);
  }

  backToLogin(): void {
    this.router.navigate(['/auth/login']);
  }

  private decodeJwtUser(token: string, callbackRole?: string | null): User | null {
    try {
      const parts = token.split('.');
      if (parts.length < 2) {
        return null;
      }

      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
      const payload = JSON.parse(atob(padded));

      const normalizedRoleFromToken = this.normalizeRole(payload.role);
      const normalizedRoleFromCallback = this.normalizeRole(callbackRole ?? undefined);
      const role = normalizedRoleFromToken === 'CANDIDATE' && normalizedRoleFromCallback !== 'CANDIDATE'
        ? normalizedRoleFromCallback
        : normalizedRoleFromToken;
      const username = payload.username ?? payload.sub ?? 'oauth-user';

      return {
        id: String(payload.id ?? username),
        username,
        email: payload.email ?? `${username}@oauth.local`,
        role,
      };
    } catch {
      return null;
    }
  }

  private normalizeRole(rawRole: string | undefined): Role {
    switch (rawRole) {
      case 'HR_ADMIN':
      case 'SUPER_ADMIN':
      case 'RECRUITER':
        return rawRole;
      // Backend UserRole enum names that map to GraphQL Role values
      case 'ADMIN':
      case 'HIRING_MANAGER':
        return 'HR_ADMIN';
      case 'JOB_SEEKER':
        return 'CANDIDATE';
      default:
        return 'CANDIDATE';
    }
  }
}

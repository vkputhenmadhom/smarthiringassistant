import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { Spin, Alert, Card, Typography } from 'antd';
import { setAuthFromOAuthCallback, logout } from '../../store/authSlice';
import { AuthPayload, AuthUser } from '../../graphql';

const { Title } = Typography;

const OAuthCallbackPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [searchParams] = useSearchParams();
  const [error, setError] = React.useState<string | null>(null);

  useEffect(() => {
    const accessToken = searchParams.get('accessToken');
    const refreshToken = searchParams.get('refreshToken');
    const expiresInStr = searchParams.get('expiresIn');
    const callbackError = searchParams.get('error');
    const role = searchParams.get('role');
    const username = searchParams.get('username') ?? 'oauth_user';

    // OAuth error from backend
    if (callbackError) {
      setError(`OAuth login failed: ${callbackError}`);
      dispatch(logout());
      setTimeout(() => navigate('/login'), 2000);
      return;
    }

    // Missing params
    if (!accessToken || !refreshToken || !expiresInStr) {
      setError('Invalid OAuth callback parameters');
      dispatch(logout());
      setTimeout(() => navigate('/login'), 2000);
      return;
    }

    const expiresIn = Number(expiresInStr);
    if (!Number.isFinite(expiresIn) || expiresIn <= 0) {
      setError('Invalid token expiration');
      dispatch(logout());
      setTimeout(() => navigate('/login'), 2000);
      return;
    }

    // Decode JWT if available, or use callback params
    let user: AuthUser | null = null;
    try {
      if (accessToken.includes('.')) {
        const parts = accessToken.split('.');
        if (parts.length >= 2) {
          const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
          const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
          const decoded = JSON.parse(atob(padded));
          user = {
            id: String(decoded.id ?? 'unknown'),
            username: decoded.username ?? username,
            email: decoded.email ?? `${username}@oauth.local`,
            role: normalizeRole(decoded.role ?? role),
          };
        }
      }
    } catch (e) {
      console.warn('Failed to decode JWT, using callback params');
    }

    // Fallback: construct user from callback params
    if (!user) {
      user = {
        id: 'unknown',
        username,
        email: `${username}@oauth.local`,
        role: normalizeRole(role),
      };
    }

    const payload: AuthPayload = {
      token: accessToken,
      refreshToken,
      expiresIn,
      user,
    };

    // Dispatch OAuth auth to Redux
    dispatch(setAuthFromOAuthCallback(payload));

    // Navigate to dashboard
    setTimeout(() => navigate('/dashboard'), 500);
  }, [searchParams, dispatch, navigate]);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
      <Card style={{ width: 'min(420px, 96vw)', borderRadius: 16 }}>
        <div style={{ textAlign: 'center' }}>
          <Title level={4}>Processing Sign-In</Title>
          {error ? (
            <Alert message={error} type="error" showIcon style={{ marginTop: 16 }} />
          ) : (
            <>
              <p style={{ color: '#666', marginBottom: 24 }}>Completing your OAuth login...</p>
              <Spin size="large" />
            </>
          )}
        </div>
      </Card>
    </div>
  );
};

function normalizeRole(rawRole: string | null | undefined): string {
  const role = (rawRole ?? 'CANDIDATE').toUpperCase();
  switch (role) {
    case 'HR_ADMIN':
    case 'SUPER_ADMIN':
    case 'RECRUITER':
    case 'CANDIDATE':
      return role;
    case 'ADMIN':
    case 'HIRING_MANAGER':
      return 'HR_ADMIN'; // Map backend roles to GraphQL roles
    case 'JOB_SEEKER':
      return 'CANDIDATE';
    default:
      return 'CANDIDATE';
  }
}

export default OAuthCallbackPage;


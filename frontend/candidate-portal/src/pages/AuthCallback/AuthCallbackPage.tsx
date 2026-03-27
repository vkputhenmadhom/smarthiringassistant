import React from 'react';
import { Alert, Card, Spin, Typography } from 'antd';
import { useDispatch } from 'react-redux';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AppDispatch } from '../../store';
import { setAuthFromOAuthCallback } from '../../store/authSlice';
import { AuthPayload, AuthUser } from '../../graphql';

const { Title, Text } = Typography;

const normalizeRole = (role: string | undefined): string => {
  if (!role) {
    return 'CANDIDATE';
  }
  if (role === 'JOB_SEEKER') {
    return 'CANDIDATE';
  }
  return role;
};

const decodeJwtUser = (token: string): AuthUser | null => {
  try {
    const parts = token.split('.');
    if (parts.length < 2) {
      return null;
    }

    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const payload = JSON.parse(atob(padded));

    const username = payload.username ?? payload.sub ?? 'oauth-user';
    const email = payload.email ?? `${username}@oauth.local`;
    const role = normalizeRole(payload.role);
    const id = String(payload.id ?? username);

    return { id, username, email, role };
  } catch {
    return null;
  }
};

const AuthCallbackPage: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    const callbackError = params.get('error');
    if (callbackError) {
      setError('OAuth sign-in failed. Please try again.');
      return;
    }

    const accessToken = params.get('accessToken');
    const refreshToken = params.get('refreshToken');
    const expiresInRaw = params.get('expiresIn');

    if (!accessToken || !refreshToken || !expiresInRaw) {
      setError('Missing OAuth callback parameters.');
      return;
    }

    const user = decodeJwtUser(accessToken);
    if (!user) {
      setError('Unable to read user details from access token.');
      return;
    }

    const expiresIn = Number(expiresInRaw);
    if (!Number.isFinite(expiresIn) || expiresIn <= 0) {
      setError('Invalid token expiry received from OAuth callback.');
      return;
    }

    const payload: AuthPayload = {
      token: accessToken,
      refreshToken,
      expiresIn,
      user,
    };

    dispatch(setAuthFromOAuthCallback(payload));
    navigate('/dashboard', { replace: true });
  }, [dispatch, navigate, params]);

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px',
        background: 'linear-gradient(135deg,#3f51b5,#5c6bc0)',
      }}
    >
      <Card style={{ width: 'min(460px, 96vw)', borderRadius: 16 }}>
        <Title level={4} style={{ marginBottom: 8 }}>Completing sign-in</Title>
        {!error && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Spin size="small" />
            <Text>Processing OAuth callback and preparing your session...</Text>
          </div>
        )}
        {error && (
          <Alert
            type="error"
            message={error}
            description="Please return to login and try your provider sign-in again."
            showIcon
            action={<a href="/login">Back to login</a>}
          />
        )}
      </Card>
    </div>
  );
};

export default AuthCallbackPage;


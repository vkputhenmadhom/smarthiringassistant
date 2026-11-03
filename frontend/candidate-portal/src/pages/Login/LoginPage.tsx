import React, { useEffect } from 'react';
import { Card, Form, Input, Button, Alert, Typography } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { loginAsync } from '../../store/authSlice';
import { RootState, AppDispatch } from '../../store';

const { Title, Text } = Typography;
// Must point directly to auth-service (same host:port as the registered Google callback
// http://localhost:8001/api/auth/login/oauth2/code/google) so the JSESSIONID that
// carries the OAuth2 state is reachable when Google redirects back.
const OAUTH_BASE = import.meta.env.VITE_OAUTH_BASE_URL ?? 'http://localhost:8001/api/auth/oauth2/authorization';
const ENABLE_LINKEDIN_OAUTH = import.meta.env.VITE_LINKEDIN_OAUTH_ENABLED === 'true';
const OAUTH_PORTAL = 'candidate';

const LoginPage: React.FC = () => {
  const dispatch  = useDispatch<AppDispatch>();
  const navigate  = useNavigate();
  const { loading, error, token, user } = useSelector((s: RootState) => s.auth);

  // Clear any HR dashboard session/localStorage to start fresh for candidate portal
  React.useEffect(() => {
    // Clear HR dashboard tokens if present (in case user navigated from HR dashboard)
    localStorage.removeItem('authToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('expiresAt');
    localStorage.removeItem('user');
    sessionStorage.clear();
  }, []);

  useEffect(() => {
    // All authenticated users (including HR) can use candidate portal as CANDIDATE
    if (token && user) {
      navigate('/dashboard');
    }
  }, [token, user, navigate]);

  const onFinish = (values: { username: string; password: string }) => {
    dispatch(loginAsync(values));
  };

  const continueWithProvider = async (provider: 'google' | 'linkedin') => {
    // Clear server session first to avoid mixing portals
    // (user might have logged into HR dashboard previously)
    try {
      await fetch('http://localhost:8001/api/auth/clear-session', { mode: 'no-cors' });
    } catch (e) {
      console.warn('Failed to clear session:', e);
    }
    // Then redirect to OAuth flow
    window.location.href = `${OAUTH_BASE}/${provider}?portal=${encodeURIComponent(OAUTH_PORTAL)}`;
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center',
                  justifyContent: 'center', padding: '16px', background: 'linear-gradient(135deg,#3f51b5,#5c6bc0)' }}>
      <Card style={{ width: 'min(420px, 96vw)', borderRadius: 16, boxShadow: '0 8px 32px rgba(0,0,0,.2)' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ margin: 0 }}>Smart Hiring Assistant</Title>
          <Text type="secondary">Candidate Portal — Sign In</Text>
        </div>
        {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="username" rules={[{ required: true, message: 'Username is required' }]}>
            <Input prefix={<UserOutlined />} placeholder="Username" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: 'Password is required' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="Password" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={loading}
                  style={{ marginTop: 8 }}>
            Sign In
          </Button>

          <div style={{ textAlign: 'center', margin: '14px 0 10px', color: '#777', fontSize: 13 }}>
            or continue with
          </div>

          <div style={{ display: 'grid', gap: 10 }}>
            <Button size="large" block onClick={() => continueWithProvider('google')}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <span style={{
                  width: 18,
                  height: 18,
                  borderRadius: '50%',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background: '#fff',
                  border: '1px solid #dadce0',
                  color: '#4285F4',
                  fontSize: 11,
                  fontWeight: 700,
                }}>G</span>
                Continue with Google
              </span>
            </Button>
            {ENABLE_LINKEDIN_OAUTH && (
              <Button size="large" block onClick={() => continueWithProvider('linkedin')}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <span style={{
                    width: 18,
                    height: 18,
                    borderRadius: '3px',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: '#0A66C2',
                    color: '#fff',
                    fontSize: 10,
                    fontWeight: 700,
                  }}>in</span>
                  Continue with LinkedIn
                </span>
              </Button>
            )}
          </div>
        </Form>
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Text>Don't have an account? </Text>
          <Link to="/register">Create one</Link>
        </div>
      </Card>
    </div>
  );
};

export default LoginPage;

import React, { useEffect } from 'react';
import { Card, Form, Input, Button, Alert, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { registerAsync } from '../../store/authSlice';
import { RootState, AppDispatch } from '../../store';

const { Title, Text } = Typography;

const RegisterPage: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const { loading, error, token, user } = useSelector((s: RootState) => s.auth);

  useEffect(() => {
    if (token && user?.role === 'CANDIDATE') {
      navigate('/dashboard');
    }
  }, [token, user, navigate]);

  const onFinish = (values: { username: string; email: string; password: string }) =>
    dispatch(registerAsync({ ...values, role: 'CANDIDATE' }));

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center',
                  justifyContent: 'center', padding: '16px', background: 'linear-gradient(135deg,#3f51b5,#5c6bc0)' }}>
      <Card style={{ width: 'min(440px, 96vw)', borderRadius: 16 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ margin: 0 }}>Create Account</Title>
          <Text type="secondary">Smart Hiring Assistant</Text>
        </div>
        {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
        {token && user?.role && user.role !== 'CANDIDATE' && (
          <Alert
            message="This portal is for candidates only. Please use the HR dashboard for your role."
            type="warning"
            style={{ marginBottom: 16 }}
          />
        )}
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="username" rules={[{ required: true }]}>
            <Input placeholder="Username" size="large" />
          </Form.Item>
          <Form.Item name="email" rules={[{ required: true, type: 'email' }]}>
            <Input placeholder="Email" size="large" type="email" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, min: 6 }]}>
            <Input.Password placeholder="Password (min 6 chars)" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={loading}>
            Create Account
          </Button>
        </Form>
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Text>Already have an account? </Text>
          <Link to="/login">Sign in</Link>
        </div>
      </Card>
    </div>
  );
};

export default RegisterPage;

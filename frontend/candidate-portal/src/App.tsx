import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import ProtectedRoute from './components/ProtectedRoute/ProtectedRoute';
import { AppDispatch, RootState } from './store';
import { refreshAsync } from './store/authSlice';

const Layout = lazy(() => import('./components/Layout/Layout'));
const LoginPage = lazy(() => import('./pages/Login/LoginPage'));
const RegisterPage = lazy(() => import('./pages/Register/RegisterPage'));
const AuthCallbackPage = lazy(() => import('./pages/AuthCallback/AuthCallbackPage'));
const DashboardPage = lazy(() => import('./pages/Dashboard/DashboardPage'));
const JobsPage = lazy(() => import('./pages/Jobs/JobsPage'));
const ResumeUploadPage = lazy(() => import('./pages/ResumeUpload/ResumeUploadPage'));
const InterviewPrepPage = lazy(() => import('./pages/InterviewPrep/InterviewPrepPage'));

const routeLoadingFallback = (
  <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
    Loading...
  </div>
);

const App: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { token, refreshToken, expiresAt } = useSelector((s: RootState) => s.auth);
  const attemptedBootstrapRefresh = React.useRef(false);

  React.useEffect(() => {
    if (attemptedBootstrapRefresh.current) {
      return;
    }

    if (!token && refreshToken) {
      attemptedBootstrapRefresh.current = true;
      dispatch(refreshAsync(refreshToken));
    }
  }, [dispatch, token, refreshToken]);

  React.useEffect(() => {
    if (!token || !refreshToken || !expiresAt) {
      return;
    }

    if (expiresAt <= Date.now() + 30_000) {
      dispatch(refreshAsync(refreshToken));
      return;
    }

    const refreshDelayMs = Math.max(expiresAt - Date.now() - 60_000, 5_000);
    const timer = setTimeout(() => {
      dispatch(refreshAsync(refreshToken));
    }, refreshDelayMs);

    return () => clearTimeout(timer);
  }, [dispatch, token, refreshToken, expiresAt]);

  return (
    <BrowserRouter>
      <Suspense fallback={routeLoadingFallback}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />
          <Route element={<ProtectedRoute allowedRoles={['CANDIDATE']} />}>
            <Route element={<Layout />}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/jobs" element={<JobsPage />} />
              {/* Government jobs are a tab on Find Jobs now */}
              <Route path="/govt-jobs" element={<Navigate to="/jobs?feed=govt" replace />} />
              <Route path="/resume" element={<ResumeUploadPage />} />
              <Route path="/interview" element={<InterviewPrepPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
};

export default App;

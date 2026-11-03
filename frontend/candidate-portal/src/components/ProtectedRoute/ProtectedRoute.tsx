import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { RootState } from '../../store';

type ProtectedRouteProps = {
  allowedRoles?: string[];
};

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ allowedRoles }) => {
  const { token, role, expiresAt, loading } = useSelector((s: RootState) => ({
    token: s.auth.token,
    role: s.auth.user?.role,
    expiresAt: s.auth.expiresAt,
    loading: s.auth.loading,
  }));

  const persistedToken = localStorage.getItem('sha_token');
  const persistedExpiresAt = Number(localStorage.getItem('sha_expires_at') ?? '') || null;
  const effectiveToken = token ?? persistedToken;
  const effectiveExpiresAt = expiresAt ?? persistedExpiresAt;

  if (!effectiveToken) {
    return <Navigate to="/login" replace />;
  }

  if (effectiveExpiresAt && effectiveExpiresAt <= Date.now() && loading) {
    return null;
  }

   if (effectiveExpiresAt && effectiveExpiresAt <= Date.now()) {
     return <Navigate to="/login" replace />;
   }

   // Candidate portal allows all authenticated users - HR users are mapped to CANDIDATE role
   // for this portal's feature access, but retain their actual role in the JWT for backend checks
   if (allowedRoles && (!role || !allowedRoles.includes(role))) {
     return <Navigate to="/login" replace />;
   }

   return <Outlet />;
};

export default ProtectedRoute;

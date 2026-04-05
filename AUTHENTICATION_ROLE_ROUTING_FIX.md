# Authentication Role-Based Routing Fix

## Problem

When an HR admin user logged into the **candidate portal** with the same Google account they used for HR admin, they were automatically redirected to the HR admin dashboard instead of being allowed to access the candidate portal.

## Root Cause Analysis

The `OAuth2AuthenticationSuccessHandler` contained logic that redirected any HR-role user away from the candidate portal:

```java
// OLD (incorrect) — rejected HR users from candidate portal
if ("candidate".equals(portalOrigin) && HR_ROLES.contains(role)) {
    targetRedirectUri = hrAdminSuccessRedirectUri;
    warningParam = "HR account cannot access candidate portal";
}
```

This meant that any user with an `ADMIN`, `RECRUITER`, or `HIRING_MANAGER` database role who tried to use the candidate portal would always be forcibly redirected to the HR dashboard — even if they explicitly chose to log in to the candidate portal.

## Design Decision

**Portals are access channels, not strict role gates for users.**

- The **candidate portal** is a public-facing interface. Any authenticated user should be able to browse jobs, submit applications, and use candidate features.
- The **HR admin dashboard** is for internal HR staff to manage jobs and candidates.
- A user's database `role` determines their **backend permissions**. The JWT token always carries the real role for backend authorization.
- Each portal **maps the role to CANDIDATE** for frontend feature-gating purposes when users access the candidate portal. The JWT still carries the real role for API calls.

## Solution

### Backend — `OAuth2AuthenticationSuccessHandler.java`

**Removed** the HR-user rejection. The handler now simply routes to the portal that was requested — `candidate` portal receives the callback if `?portal=candidate` was present, `hr-admin` receives it otherwise.

```java
// NEW — portal origin drives redirect, not role
String targetRedirectUri = resolveTargetRedirectUri(portalOrigin, role);
// No special case for HR users accessing candidate portal
```

### Backend — `SecurityConfiguration.java`

Added `/clear-session` to the permit-all list so browsers can invalidate stale sessions before starting a new OAuth flow:

```java
.requestMatchers("/clear-session").permitAll()
```

### Candidate Portal — `authSlice.ts`

All async thunks (`loginAsync`, `registerAsync`, `refreshAsync`) now map user role to `CANDIDATE` for frontend feature access in the candidate portal:

```typescript
if (authPayload?.user) {
  authPayload.user = { ...authPayload.user, role: 'CANDIDATE' };
}
```

### Candidate Portal — `AuthCallbackPage.tsx`

OAuth callback maps all authenticated users to CANDIDATE role for the portal session, regardless of their database role:

```typescript
// Map all roles to CANDIDATE for candidate portal feature access
const candidatePortalUser = { ...user, role: 'CANDIDATE' };
```

### Candidate Portal — `LoginPage.tsx`

Removed the HR-user redirect. All authenticated users are now navigated to `/dashboard`:

```typescript
useEffect(() => {
  if (token && user) {
    navigate('/dashboard');
  }
}, [token, user, navigate]);
```

### Candidate Portal — `ProtectedRoute.tsx`

Removed explicit HR role blocking. Any authenticated user can access candidate portal protected routes:

```typescript
// Candidate portal allows all authenticated users
if (allowedRoles && (!role || !allowedRoles.includes(role))) {
  return <Navigate to="/login" replace />;
}
```

## Testing Scenarios

### Scenario 1: HR User logs in via Traditional Login on Candidate Portal
- ✅ Login succeeds, role is mapped to CANDIDATE in frontend store
- ✅ User navigates to `/dashboard` and can use candidate features
- ✅ JWT still carries real HR role for backend API authorization

### Scenario 2: HR User logs in via OAuth (Google) on Candidate Portal
- ✅ OAuth callback receives HR role in token
- ✅ Role is mapped to CANDIDATE for portal session
- ✅ User navigates to `/dashboard` and can browse jobs / apply

### Scenario 3: HR User navigates to protected Candidate Portal route directly
- ✅ Token is valid, user proceeds to route
- ✅ No unexpected redirects to HR dashboard

### Scenario 4: Candidate User logs in on HR Dashboard
- ✅ HR dashboard reducer rejects candidate role
- ✅ Error message: "This portal is for HR staff only"
- ✅ User sees candidate portal redirect link

### Scenario 5: Candidate User logs in via OAuth on HR Dashboard
- ✅ OAuth callback detects CANDIDATE role
- ✅ User is redirected to candidate portal after 2 seconds

### Scenario 6: Candidate User logs in on Candidate Portal
- ✅ User is logged in successfully with CANDIDATE role
- ✅ User can access all candidate features

### Scenario 7: HR User logs in on HR Dashboard
- ✅ User is logged in successfully with HR_ADMIN role
- ✅ User can access all HR admin features

## Security Model

| User Type | Candidate Portal | HR Admin Dashboard |
|---|---|---|
| Candidate | ✅ Full access as CANDIDATE | ❌ Blocked (reducer rejects) |
| HR Admin | ✅ Access as CANDIDATE (portal-scoped role) | ✅ Full access as HR_ADMIN |

**Backend authorization is NOT affected** — the JWT token always contains the real database role. Backend API endpoints (`@PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")`) continue to enforce proper access control.

## Files Changed

| File | Change |
|---|---|
| `services/auth-service/.../OAuth2AuthenticationSuccessHandler.java` | Removed HR-user redirect from candidate portal |
| `services/auth-service/.../SecurityConfiguration.java` | Permitted `/clear-session` without authentication |
| `frontend/candidate-portal/src/store/authSlice.ts` | Map all roles to CANDIDATE in async thunks |
| `frontend/candidate-portal/src/pages/AuthCallback/AuthCallbackPage.tsx` | Map all roles to CANDIDATE in OAuth callback |
| `frontend/candidate-portal/src/pages/Login/LoginPage.tsx` | Removed HR-user redirect; navigate all users to /dashboard |
| `frontend/candidate-portal/src/components/ProtectedRoute/ProtectedRoute.tsx` | Removed explicit HR role blocking |

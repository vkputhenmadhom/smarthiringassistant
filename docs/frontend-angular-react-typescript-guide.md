# Smart Hiring Assistant Frontend Engineering Guide

This document explains how the frontend code is structured across Angular, React, and TypeScript so you can learn the codebase quickly and contribute safely.

## Scope

- React app: `frontend/candidate-portal`
- Angular app: `frontend/hr-admin-dashboard`
- Shared integration boundary: API Gateway GraphQL/REST via local proxy (`/graphql`, `/api`)

---

## 1) High-Level Architecture

Both frontend apps follow the same integration model:

1. UI triggers action (route, button, form submit)
2. State layer dispatches operation (Redux Toolkit/NgRx)
3. GraphQL client executes query/mutation/subscription (Apollo)
4. Gateway (`:8000`) orchestrates backend services
5. UI rerenders from updated state and Apollo cache

Local development proxy targets:

- React Vite proxy: `frontend/candidate-portal/vite.config.ts`
- Angular proxy: `frontend/hr-admin-dashboard/proxy.conf.json`

---

## 2) React Candidate Portal (React + Vite + Apollo + Redux Toolkit)

### 2.1 Bootstrap and Providers

Entry point: `frontend/candidate-portal/src/main.tsx`

- Wraps app with Redux `Provider`
- Wraps app with Apollo `ApolloProvider`
- Uses Ant Design reset styles

This is the root composition point for app-wide concerns.

### 2.2 Routing and Lazy Loading

Routing file: `frontend/candidate-portal/src/App.tsx`

Patterns used:

- `lazy(...)` + `Suspense` for route-level code splitting
- Public routes: `/login`, `/register`, `/auth/callback`
- Protected routes guarded by `ProtectedRoute`
- Shared shell layout with nested page routes

Why this matters:

- Smaller initial bundle
- Better route isolation and maintainability
- Consistent auth enforcement at route boundary

### 2.3 Auth Lifecycle

Main files:

- `frontend/candidate-portal/src/store/authSlice.ts`
- `frontend/candidate-portal/src/apolloClient.ts`
- `frontend/candidate-portal/src/components/ProtectedRoute/ProtectedRoute.tsx`

Flow:

1. Login/register mutation returns `token`, `refreshToken`, `expiresIn`, `user`
2. Tokens persisted to `localStorage`
3. App boot tries silent refresh if access token missing but refresh token exists
4. Apollo link refreshes expiring token before requests and retries once on auth errors
5. Protected route checks token + expiry before allowing navigation

### 2.4 Apollo GraphQL Client Design

File: `frontend/candidate-portal/src/apolloClient.ts`

Key design choices:

- HTTP link for queries/mutations (`/graphql`)
- WebSocket link for subscriptions (`/graphql-ws`)
- `split(...)` routes subscription operations to WS
- `setContext(...)` injects Bearer token
- `onError(...)` handles `UNAUTHENTICATED` / 401 with one retry path
- `refreshInFlight` prevents duplicate concurrent refresh calls

### 2.5 State Management (Redux Toolkit)

Store root: `frontend/candidate-portal/src/store/index.ts`

Slices:

- `auth`
- `jobs`
- `resume`

Patterns:

- `createSlice` for state + reducers
- `createAsyncThunk` for async GraphQL operations
- Strongly typed selectors/dispatch via `RootState` and `AppDispatch`

### 2.6 UI and Composition

Shell: `frontend/candidate-portal/src/components/Layout/Layout.tsx`

- Responsive desktop sider + mobile drawer
- GraphQL notifications query + subscription merged in UI state
- Auth avatar/dropdown actions
- Route outlet for nested pages

---

## 3) Angular HR Admin Dashboard (Angular 17 + Material + Apollo + NgRx)

### 3.1 Standalone App Configuration

Root config: `frontend/hr-admin-dashboard/src/app/app.config.ts`

This app uses Angular standalone providers (no NgModule root required):

- `provideRouter(...)`
- `provideHttpClient(withInterceptors(...))`
- `provideStore(...)`, `provideEffects(...)`
- Apollo setup with HTTP + GraphQL WS split

### 3.2 Routing and Feature Segmentation

Root routes: `frontend/hr-admin-dashboard/src/app/app.routes.ts`

Pattern:

- Public auth area under `/auth`
- Protected shell under `/app`
- Child feature routes are lazy loaded (`jobs`, `candidates`, `screening`, `notifications`, `analytics`)
- Route metadata + `roleGuard` enforce role-based access

Example feature routing: `frontend/hr-admin-dashboard/src/app/features/jobs/jobs.routes.ts`

### 3.3 Auth and Session Management

Files:

- `frontend/hr-admin-dashboard/src/app/store/auth/auth.reducer.ts`
- `frontend/hr-admin-dashboard/src/app/store/auth/auth.effects.ts`
- `frontend/hr-admin-dashboard/src/app/core/interceptors/jwt.interceptor.ts`
- `frontend/hr-admin-dashboard/src/app/core/guards/auth.guard.ts`
- `frontend/hr-admin-dashboard/src/app/core/guards/role.guard.ts`

Behavior:

- Bootstraps auth state from `localStorage`
- Clears stale sessions early if clearly expired
- Schedules proactive refresh before token expiry
- Interceptor attaches JWT and triggers refresh when near expiry
- Guards block unauthenticated or unauthorized access

### 3.4 NgRx Data Flow

Representative files:

- `frontend/hr-admin-dashboard/src/app/store/jobs/jobs.actions.ts`
- `frontend/hr-admin-dashboard/src/app/store/jobs/jobs.effects.ts`
- `frontend/hr-admin-dashboard/src/app/store/jobs/jobs.reducer.ts`
- `frontend/hr-admin-dashboard/src/app/store/jobs/jobs.selectors.ts`

Pattern:

1. Component dispatches action
2. Effect executes Apollo GraphQL call
3. Success/failure action emitted
4. Reducer updates state
5. Component reads state through selectors

### 3.5 UI Shell and Real-Time Notifications

Shell component: `frontend/hr-admin-dashboard/src/app/features/shell/shell.component.ts`

Responsibilities:

- Responsive sidenav + toolbar
- Loads notifications query on user context
- Subscribes to `newNotification` stream
- Supports mark-one and mark-all read operations
- Hosts feature `router-outlet`

### 3.6 Dashboard View Example

File: `frontend/hr-admin-dashboard/src/app/features/dashboard/dashboard.component.ts`

- Pulls `dashboardMetrics` via Apollo query
- Maps result into typed model (`DashboardMetrics`)
- Renders metrics cards, pass rates, skills demand, activity timeline

---

## 4) TypeScript Practices Used in This Repo

### 4.1 Strong Typing of Domain Models

Examples:

- React GraphQL/domain interfaces: `frontend/candidate-portal/src/graphql/types.ts`
- Angular shared models: `frontend/hr-admin-dashboard/src/app/shared/models/index.ts`

Benefits:

- Safer refactoring
- Better IDE support
- Fewer runtime shape mismatches

### 4.2 Strict Compiler Modes

- React TS config: `frontend/candidate-portal/tsconfig.json` (`strict: true`)
- Angular TS config: `frontend/hr-admin-dashboard/tsconfig.json` (`strict: true`, strict templates)

### 4.3 Typed State and Actions

- React: typed `RootState` and `AppDispatch`
- Angular: typed action payloads + selectors + reducer state interfaces

### 4.4 Environment-Driven Endpoints

- React uses Vite env vars and proxy defaults
- Angular uses environment config and proxy

This keeps local/staging/prod endpoint changes outside component logic.

---

## 5) GraphQL Contract Organization

- React operations are grouped in `frontend/candidate-portal/src/graphql/`
- Angular operations are grouped in `frontend/hr-admin-dashboard/src/app/graphql/`
- Contract source-of-truth lives at repo root `contracts/`

Common operation categories:

- Auth: login/register/me/refresh
- Jobs and candidates
- Screening sessions and updates
- Notifications query + subscriptions

---

## 6) Suggested Learning Path (Hands-On)

1. Start from app entrypoints:
   - `frontend/candidate-portal/src/main.tsx`
   - `frontend/hr-admin-dashboard/src/app/app.config.ts`
2. Trace routing:
   - React: `frontend/candidate-portal/src/App.tsx`
   - Angular: `frontend/hr-admin-dashboard/src/app/app.routes.ts`
3. Trace one auth login end-to-end in each app
4. Trace one data feature (jobs or screening):
   - UI component -> action/thunk -> GraphQL -> reducer -> UI update
5. Trace one real-time feature using GraphQL subscription (notifications)

---

## 7) Practical Contribution Guidelines

- Keep business logic out of presentational components when possible
- Add/update TypeScript interfaces before wiring UI changes
- Reuse existing GraphQL operation grouping by feature
- Add selector-based reads instead of direct deep state access
- Keep auth token handling centralized (do not duplicate header logic)

---

## 8) Fast File Map

### React Candidate Portal

- App bootstrap: `frontend/candidate-portal/src/main.tsx`
- Routes: `frontend/candidate-portal/src/App.tsx`
- Apollo config: `frontend/candidate-portal/src/apolloClient.ts`
- Auth state: `frontend/candidate-portal/src/store/authSlice.ts`
- Layout shell: `frontend/candidate-portal/src/components/Layout/Layout.tsx`

### Angular HR Admin Dashboard

- App config/providers: `frontend/hr-admin-dashboard/src/app/app.config.ts`
- Routes: `frontend/hr-admin-dashboard/src/app/app.routes.ts`
- Auth effects/reducer: `frontend/hr-admin-dashboard/src/app/store/auth/`
- JWT interceptor: `frontend/hr-admin-dashboard/src/app/core/interceptors/jwt.interceptor.ts`
- Shell component: `frontend/hr-admin-dashboard/src/app/features/shell/shell.component.ts`

---

## 9) Where to Extend Next

- Add generated GraphQL typings (codegen) to reduce `any` usage
- Add shared frontend lint rules for both apps
- Add architecture decision records for auth/session strategy
- Add feature-level README files under each frontend app


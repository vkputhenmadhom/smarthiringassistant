# Frontend Interview Prep: 10 Common Q&A

This document provides candidate-ready answers for common frontend architecture questions tied directly to Smart Hiring Assistant codebase.

---

## Q1: How do you handle authentication in a React + GraphQL application?

**Answer:**
In the React Candidate Portal, authentication is handled through a three-layer approach:

1. **Token Storage** (`frontend/candidate-portal/src/store/authSlice.ts`): Login/register mutations return `token`, `refreshToken`, `expiresIn`, and `user` - these are persisted to `localStorage`.

2. **Apollo Link Middleware** (`frontend/candidate-portal/src/apolloClient.ts`): The `setContext()` link automatically injects the Bearer token into every GraphQL request header. If a token nears expiry, it proactively refreshes before the request.

3. **Route Guard** (`frontend/candidate-portal/src/components/ProtectedRoute/ProtectedRoute.tsx`): Protected routes check if a valid token exists and hasn't expired before rendering. On app boot, if the access token is missing but refresh token exists, a silent refresh is attempted.

**Code tie-in:** The `onError()` link in `apolloClient.ts` catches `UNAUTHENTICATED` (401) errors and retries the request once after refreshing, preventing user interruption.

---

## Q2: Explain the difference between Redux Toolkit and NgRx. When would you use each?

**Answer:**
Both are state management solutions but serve different architectural models:

- **Redux Toolkit** (`frontend/candidate-portal/src/store/`): Used in React. Simpler, more imperative. Slices combine state + reducers + actions. Async logic via `createAsyncThunk`. Best for smaller teams or moderate complexity (this app has `authSlice`, `jobsSlice`, `resumeSlice`).

- **NgRx** (`frontend/hr-admin-dashboard/src/app/store/`): Used in Angular. More reactive and RxJS-based. Effects handle side effects explicitly. Actions trigger effects → reducers → selectors. Better for enterprise apps with complex event streams and audit trails.

**This codebase:** React uses Redux Toolkit because it's lighter and faster to prototype. Angular uses NgRx because it's the ecosystem standard and scales with large team feature modules (jobs, candidates, screening, notifications, analytics).

---

## Q3: How does GraphQL subscriptions work in this app, and why use WebSocket?

**Answer:**
Subscriptions enable real-time updates (e.g., new notifications, live screening results).

**Implementation** (`frontend/candidate-portal/src/apolloClient.ts`):
- Apollo splits operations: queries/mutations → HTTP link, subscriptions → WebSocket link.
- WebSocket connection to `/graphql-ws` enables server to push updates without polling.
- React Shell (`frontend/candidate-portal/src/components/Layout/Layout.tsx`) subscribes to `newNotification` stream and merges updates into Redux state.

**Why WebSocket vs polling?** Polling wastes bandwidth (constant requests), while WebSocket maintains a persistent connection. Only new data flows in—critical for user-facing notifications without draining battery or server resources.

**Code:** Apollo's `split()` function routes subscription operations to the WS link automatically.

---

## Q4: Explain lazy loading and code splitting in the React routing setup. Why does it matter?

**Answer:**
Lazy loading (`frontend/candidate-portal/src/App.tsx`) uses React's `lazy()` + `Suspense` to split routes into separate chunks:

```tsx
const LoginPage = lazy(() => import('./pages/LoginPage'));
const CandidatePortal = lazy(() => import('./pages/CandidatePortal'));
```

**Benefits:**
1. **Smaller initial bundle:** Users download only the login page code at startup (~50KB vs 300KB).
2. **Route isolation:** Each feature is independently deployable and testable.
3. **Faster First Contentful Paint (FCP):** App becomes interactive faster.

**Suspense fallback** shows a spinner while the chunk loads—perceived responsiveness improves.

**This matters for interviews:** Demonstrates understanding of performance optimization, bundling strategy, and user experience design decisions.

---

## Q5: How does token refresh work without interrupting the user experience?

**Answer:**
`frontend/candidate-portal/src/apolloClient.ts` implements a **refresh-before-request** pattern:

1. **Proactive Refresh:** Before each GraphQL request, the `setContext()` link checks if the token is near expiry (e.g., < 5 minutes remaining).
2. **Refresh-in-flight Guard:** A `refreshInFlight` promise prevents duplicate concurrent refresh calls (if 10 requests fire simultaneously, only one refresh call is made).
3. **Retry Logic:** If a request returns 401 (UNAUTHENTICATED), the onError handler retries once after refreshing.

**User experience:** No popup, no redirect to login, no request failure—refresh happens silently in the background.

**Edge case handled:** If refresh token is also invalid, the user is redirected to login (caught by ProtectedRoute on next navigation attempt).

---

## Q6: Describe the Angular auth flow in `frontend/hr-admin-dashboard`. How is it different from React?

**Answer:**
Angular uses a **reactive, effect-driven** auth flow:

1. **Bootstrap** (`frontend/hr-admin-dashboard/src/app/store/auth/auth.reducer.ts`): On app init, auth state is loaded from `localStorage`.

2. **Effects** (`frontend/hr-admin-dashboard/src/app/store/auth/auth.effects.ts`): 
   - `LoginEffect` listens for login actions → calls Apollo → emits `loginSuccess` or `loginFailure`
   - Tokens saved to localStorage via effect side-effect
   - Proactive refresh scheduled before token expiry

3. **Interceptor** (`frontend/hr-admin-dashboard/src/app/core/interceptors/jwt.interceptor.ts`): Attaches JWT to all HTTP requests. If token near expiry, triggers refresh.

4. **Guards** (`frontend/hr-admin-dashboard/src/app/core/guards/auth.guard.ts`, `role.guard.ts`):
   - `authGuard` blocks unauthenticated navigation
   - `roleGuard` enforces role-based access (e.g., only HR can access `/app/jobs/approve`)

**React vs Angular difference:**
- React: Manual token check in ProtectedRoute, imperative async thunk for refresh
- Angular: Guards + Effects automatically enforce auth, more declarative, easier to audit

---

## Q7: What is the purpose of the Shell component, and how does it coordinate with feature routes?

**Answer:**
The Shell component is the **persistent layout wrapper** for authenticated users:

**React** (`frontend/candidate-portal/src/components/Layout/Layout.tsx`):
- Responsive desktop sidenav + mobile drawer
- Loads notifications query + subscribes to new notifications
- Auth avatar dropdown with logout action
- `<Outlet>` renders child routes (jobs, resume, applications)

**Angular** (`frontend/hr-admin-dashboard/src/app/features/shell/shell.component.ts`):
- Same responsibilities, plus Material Design sidenav
- Pulls dashboardMetrics on load
- Handles mark-one and mark-all-read for notifications
- `<router-outlet>` for feature routes

**Why it matters:**
- Single shared UI frame reduces code duplication
- Notifications and auth state persist across route changes
- Feature modules (jobs, screening, analytics) can focus on their domain logic

---

## Q8: How are GraphQL operations organized, and where do you add new queries/mutations?

**Answer:**
GraphQL operations follow a **feature-grouped structure**:

**React:**
- `frontend/candidate-portal/src/graphql/` groups by domain
- Example: `auth.graphql` (login, register, me, refresh), `jobs.graphql` (listJobs, applyJob)

**Angular:**
- `frontend/hr-admin-dashboard/src/app/graphql/` same pattern

**Contract source-of-truth:** `contracts/` directory at repo root holds canonical schema + operation definitions shared by gateway and frontend.

**To add a new mutation:**
1. Add `mutation ApplyToJob { ... }` to `contracts/candidatePortal.graphql`
2. Run codegen (if configured) to generate TypeScript types
3. Import generated types in component
4. Call via Apollo: `useMutation(APPLY_TO_JOB_MUTATION)`

**Interview angle:** Shows understanding of DRY principle, API contracts, and code generation benefits.

---

## Q9: What is the role of Selectors in Redux/NgRx, and why not just read state directly?

**Answer:**
Selectors (`frontend/candidate-portal/src/store/jobsSlice.ts`) are **memoized functions** that derive state:

```typescript
export const selectAllJobs = (state: RootState) => state.jobs.items;
export const selectJobById = (id: string) => (state: RootState) =>
  state.jobs.items.find(j => j.id === id);
```

**Why selectors matter:**
1. **Memoization:** If state hasn't changed, selector returns cached value (no re-render).
2. **Decoupling:** Component doesn't assume state shape—if state structure changes, update selector once instead of 20 component files.
3. **Reusability:** Same selector used by multiple components without duplication.

**Compare:**
- Bad: `const jobs = useSelector(state => state.jobs.items)` (new array reference each time, forces re-render)
- Good: `const jobs = useSelector(selectAllJobs)` (memoized, stable reference)

**NgRx equivalent:** Same pattern, just with `store.select(selectAllJobs)`.

---

## Q10: Explain the lifecycle of a user applying for a job (end-to-end data flow).

**Answer:**
Complete flow from UI to backend:

1. **User Action** (`frontend/candidate-portal/src/pages/JobDetails.tsx`): Click "Apply" button
2. **Redux Dispatch** (`frontend/candidate-portal/src/store/`): Dispatch `applyForJob` async thunk
3. **GraphQL Mutation** (`frontend/candidate-portal/src/graphql/jobs.graphql`): `ApplyForJob` mutation with jobId + resumeId
4. **Apollo Client** (`frontend/candidate-portal/src/apolloClient.ts`): Sends mutation to `/graphql` via HTTP link
5. **Gateway** (API Gateway at `:8000`): Routes `ApplyForJob` mutation to job-service GraphQL resolver
6. **Backend Service** (`services/job-service/src/main/java/...`): Validates job exists, user is eligible, creates ApplicationEntity
7. **Database:** Application record inserted into `applications` table
8. **Response:** Returns `{ success: true, application: {...} }`
9. **Redux Reducer:** `applyJobSuccess` action updates state: `state.jobs.userApplications.push(application)`
10. **UI Re-render:** `selectUserApplications` selector updates, component shows "✓ Applied"
11. **Real-time (optional):** `newApplicationNotification` subscription triggers, HR dashboard updates in real-time

**Why this matters:** Shows full-stack thinking, understanding of async operations, state synchronization, and real-time architecture.

---

## Interview Prep Checklist

- [ ] Run the app locally, trace one feature end-to-end
- [ ] Read `frontend/candidate-portal/src/apolloClient.ts` (understand link composition)
- [ ] Read `frontend/candidate-portal/src/store/authSlice.ts` (understand token lifecycle)
- [ ] Read `frontend/hr-admin-dashboard/src/app/store/auth/auth.effects.ts` (understand effect-driven auth)
- [ ] Compare `ProtectedRoute.tsx` (React) vs `auth.guard.ts` (Angular)
- [ ] Ask yourself: "Why did the team choose React+Redux for candidate portal vs Angular+NgRx for HR dashboard?"
- [ ] Practice explaining each Q&A without looking at notes (2 min per question)

---

**Last Updated:** August 2026


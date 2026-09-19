# HR Portal Frontend Deep Dive

This document explains the HR portal end to end: framework, screen structure, routing, NgRx state, Apollo GraphQL, auth/session lifecycle, BFF usage, and where each page talks to the backend.

## 1. What frontend stack does the HR portal use?

The HR portal is the Angular application under:

- `frontend/hr-admin-dashboard`

From `frontend/README.md` and `frontend/hr-admin-dashboard/package.json`, the stack is:

- **Angular 17**
- **TypeScript**
- **Angular Material**
- **Apollo Angular**
- **GraphQL**
- **graphql-ws**
- **NgRx Store / Effects / Entity / Router Store**
- **RxJS**
- **Chart.js / ng2-charts**

This is the cleanest answer if asked:

> “The HR portal is an Angular 17 SPA using Angular Material for UI, Apollo Angular for GraphQL, NgRx for global state, and GraphQL WebSockets for subscriptions.”

## 2. HR portal directory map

Main areas:

- `frontend/hr-admin-dashboard/src/app/app.config.ts`
- `frontend/hr-admin-dashboard/src/app/app.routes.ts`
- `frontend/hr-admin-dashboard/src/app/core/guards/`
- `frontend/hr-admin-dashboard/src/app/core/interceptors/`
- `frontend/hr-admin-dashboard/src/app/features/`
- `frontend/hr-admin-dashboard/src/app/store/`
- `frontend/hr-admin-dashboard/src/app/graphql/queries/index.ts`
- `frontend/hr-admin-dashboard/src/app/shared/models/index.ts`
- `frontend/hr-admin-dashboard/src/environments/environment.ts`
- `frontend/hr-admin-dashboard/proxy.conf.json`

This app is much more complete than the candidate portal, so you can study it screen by screen.

## 3. High-level architecture: how HR portal talks to backend

```text
Browser
  -> Angular HR dashboard
  -> /graphql, /graphql-ws, /api via Angular dev proxy
  -> API Gateway (BFF)
  -> GraphQL resolvers in gateway
  -> downstream microservices
     - auth-service
     - job-analyzer-service
     - resume-parser-service
     - candidate-matcher-service
     - screening-bot-service
```

Dev proxy:

- `frontend/hr-admin-dashboard/proxy.conf.json`

It proxies:

- `/api` -> `http://localhost:8000`
- `/graphql` -> `http://localhost:8000`
- `/graphql-ws` -> `ws://localhost:8000`
- `/graphiql` -> `http://localhost:8000`

That means the Angular app never needs hard-coded per-service URLs. It only targets gateway-facing paths.

## 4. Why this is a BFF from HR portal perspective

The HR portal sends GraphQL operations to the gateway. The gateway then orchestrates service calls.

Examples:

- dashboard analytics -> gateway aggregates jobs + screening metrics
- candidate list -> gateway calls resume-parser-service
- candidate detail -> gateway calls resume-parser-service
- matched candidates for a job -> gateway calls candidate-matcher-service
- screening workflow -> gateway calls screening-bot-service
- auth -> gateway calls auth-service

Files to study:

- `services/api-gateway/src/main/resources/graphql/schema.graphqls`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/JobGraphQLResolver.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/CandidateGraphQLResolver.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/ScreeningGraphQLResolver.java`

Interview phrasing:

> “The gateway is the BFF because it exposes dashboard-friendly GraphQL operations and hides the underlying REST/microservice topology from the Angular app.”

## 5. App bootstrap: how the HR frontend is wired

The central bootstrap file is:

- `frontend/hr-admin-dashboard/src/app/app.config.ts`

This one file tells you most of the app architecture.

### 5.1 Router setup

It uses:

- `provideRouter(appRoutes, withComponentInputBinding())`

So the app uses Angular standalone APIs instead of legacy `NgModule` bootstrapping.

### 5.2 HTTP pipeline

It uses:

- `provideHttpClient(withInterceptors([jwtInterceptor]))`

So outgoing HTTP requests can be enriched with JWT auth headers and silent refresh behavior.

### 5.3 Global state

It wires NgRx slices:

- `auth`
- `jobs`
- `candidates`
- `screening`

via:

- `provideStore(...)`
- `provideEffects(...)`
- `provideStoreDevtools(...)`
- `provideRouterStore()`

### 5.4 GraphQL client

This file creates the Apollo client:

- HTTP link -> `environment.graphqlUrl`
- WS link -> `environment.graphqlWsUrl`
- `split(...)` routes subscriptions to websocket, all else to HTTP
- cache -> `InMemoryCache`

This is how HR frontend handles both normal GraphQL and live GraphQL subscriptions.

### 5.5 WebSocket auth

In `createWsLink()` the app reads:

- `localStorage.getItem('sha_token')`

and passes it in `connectionParams` under multiple header-like keys:

- `Authorization`
- `authorization`
- `authToken`

That matches the gateway `GraphQLContextInterceptor`, which accepts those variants from WebSocket `connection_init`.

Files:

- `frontend/hr-admin-dashboard/src/app/app.config.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/config/GraphQLContextInterceptor.java`

## 6. Routing: how screens are created and organized

The main route table is:

- `frontend/hr-admin-dashboard/src/app/app.routes.ts`

### 6.1 Route structure

Public area:

- `/auth/...`

Protected app shell:

- `/app/...`

Children under `/app`:

- `/app/dashboard`
- `/app/jobs`
- `/app/candidates`
- `/app/screening`
- `/app/notifications`
- `/app/analytics`

### 6.2 Guards

Two guard layers exist:

- `authGuard`
- `roleGuard`

Files:

- `frontend/hr-admin-dashboard/src/app/core/guards/auth.guard.ts`
- `frontend/hr-admin-dashboard/src/app/core/guards/role.guard.ts`

This gives a strong answer to:

> “How are screens secured?”

Answer:

1. user must be logged in to access `/app`
2. HR-only routes also enforce role checks
3. candidate users are blocked from HR-only areas

## 7. Session and auth state in HR portal

This is one of the best-implemented parts of the frontend and worth deep study.

### 7.1 Auth state shape

Defined in:

- `frontend/hr-admin-dashboard/src/app/store/auth/auth.reducer.ts`

Stored state:

- `user`
- `token`
- `refreshToken`
- `expiresAt`
- `loading`
- `error`

### 7.2 localStorage keys

The reducer explicitly persists:

- `sha_token`
- `sha_refresh_token`
- `sha_expires_at`
- `sha_user`

This is how browser-side session continuity survives refresh/navigation.

### 7.3 Initial state hydration

`buildInitialState()` reads those keys from `localStorage`.

Important behavior:

- if token is clearly expired, it wipes storage immediately

That prevents the app from booting into a stale logged-in state.

### 7.4 Silent refresh model

Implemented in:

- `frontend/hr-admin-dashboard/src/app/store/auth/auth.effects.ts`
- `frontend/hr-admin-dashboard/src/app/core/interceptors/jwt.interceptor.ts`

There are two refresh triggers:

1. **timer-based proactive refresh**
2. **interceptor-based refresh when token is close to expiry**

#### Timer-based refresh

`scheduleRefresh(refreshToken, expiresIn)` sets a timeout to dispatch:

- `AuthActions.refreshToken(...)`

before token expiry.

#### Interceptor-based refresh

`jwtInterceptor` checks:

- `sha_refresh_token`
- `sha_expires_at`

If token expires within ~30 seconds, it dispatches refresh.

### 7.5 App boot auth behavior

`initRefresh$` runs on `ROOT_EFFECTS_INIT`.

Boot logic:

1. if no token/refresh token -> try `loadCurrentUser()`
2. if token is clearly stale -> logout immediately
3. if token is expiring -> refresh it
4. if token is valid -> schedule refresh and load current user

This is a mature auth flow and good interview material.

### 7.6 `me` query

The effect `loadCurrentUser$` calls:

- `ME_QUERY`

The gateway resolves `me` from the JWT `Authorization` header:

- `AuthGraphQLResolver.me(...)`

So user identity in the HR portal is not re-fetched from a separate REST auth endpoint. It is reconstructed through the gateway BFF using the token.

## 8. HR login and OAuth flow

### 8.1 Username/password login

Frontend login UI:

- `frontend/hr-admin-dashboard/src/app/features/auth/login/login.component.ts`

When submit runs:

- dispatches `login({ username, password })`

Effect flow:

1. NgRx action `login`
2. `AuthEffects.login$`
3. Apollo mutation `LOGIN_MUTATION`
4. GraphQL request to gateway
5. gateway `AuthGraphQLResolver.login(...)`
6. auth-service REST `/login`
7. auth-service generates JWTs
8. gateway normalizes payload
9. reducer stores tokens/user in localStorage + state
10. `loginSuccess$` navigates to `/app/dashboard`

Files:

- `frontend/hr-admin-dashboard/src/app/features/auth/login/login.component.ts`
- `frontend/hr-admin-dashboard/src/app/store/auth/auth.effects.ts`
- `frontend/hr-admin-dashboard/src/app/store/auth/auth.reducer.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/controller/AuthController.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/service/AuthService.java`

### 8.2 OAuth login

Login component sends browser to:

- `${environment.oauthBaseUrl}/${provider}?portal=hr-admin`

where:

- `environment.oauthBaseUrl = 'http://localhost:8001/api/auth/oauth2/authorization'`

So HR OAuth intentionally goes **directly to auth-service**, not through gateway.

Why?

The component comments explain it:

> OAuth must point directly to auth-service so the `JSESSIONID` carrying OAuth2 state is present on callback.

That is a very strong interview detail.

### 8.3 OAuth callback handling

File:

- `frontend/hr-admin-dashboard/src/app/features/auth/oauth-callback/oauth-callback.component.ts`

It:

1. reads `accessToken`, `refreshToken`, `expiresIn`, `role`
2. decodes JWT payload client-side
3. normalizes role names
4. rejects candidate users from HR portal
5. dispatches `loginSuccess({ payload })`
6. navigates to `/app/dashboard`

This component also demonstrates **portal-level role enforcement**.

### 8.4 Server-side OAuth routing

The auth-service stores the portal hint in server session and redirects correctly after OAuth:

- candidate -> candidate portal callback
- hr-admin -> HR dashboard callback

Files:

- `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestResolver.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestRepository.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/security/OAuth2AuthenticationSuccessHandler.java`

This means:

- **frontend session state** = token/localStorage/NgRx
- **OAuth handshake state** = Spring Security server-side HTTP session

That separation is exactly what interviewers like hearing.

## 9. Authorization and route protection

### 9.1 `authGuard`

File:

- `frontend/hr-admin-dashboard/src/app/core/guards/auth.guard.ts`

It reads:

- `selectIsLoggedIn`
- `selectIsTokenExpired`

Behavior:

- if logged in but token expired -> dispatch logout and redirect to login
- else allow only logged-in users

### 9.2 HR-role filtering in reducer

In `auth.reducer.ts`, `loginSuccess/registerSuccess` explicitly allow only:

- `HR_ADMIN`
- `RECRUITER`
- `SUPER_ADMIN`
- `ADMIN`

If another role logs in, it clears auth state and shows an error directing user to candidate portal.

### 9.3 Route-level role guard

`app.routes.ts` uses `roleGuard` for:

- jobs
- candidates
- screening
- notifications
- analytics

Allowed roles:

- `HR_ADMIN`
- `RECRUITER`
- `SUPER_ADMIN`

So the app has:

1. auth guard
2. reducer-level role filtering
3. route-level role guard

## 10. Shell and navigation

The main protected layout is:

- `frontend/hr-admin-dashboard/src/app/features/shell/shell.component.ts`

This file is worth reading slowly because it shows how the dashboard frame is built.

### 10.1 UI structure

It uses Angular Material:

- `mat-sidenav-container`
- `mat-sidenav`
- `mat-toolbar`
- `mat-menu`
- `mat-badge`
- `mat-nav-list`

### 10.2 Responsive behavior

It uses `BreakpointObserver` to switch between:

- desktop side nav
- mobile overlay drawer

### 10.3 User menu

It reads current user from store:

- `selectCurrentUser`

and shows:

- username
- email
- role

### 10.4 Notifications in shell

The shell itself loads notifications and subscribes to live updates.

It does:

1. `watchQuery(MY_NOTIFICATIONS_QUERY)`
2. `subscribe(NEW_NOTIFICATION_SUBSCRIPTION)`
3. `markNotificationRead`
4. `markAllNotificationsRead`

That means the top-bar bell is a live GraphQL-driven feature, not static UI.

## 11. GraphQL layer used by HR portal

All main operations are centralized in:

- `frontend/hr-admin-dashboard/src/app/graphql/queries/index.ts`

This single file is your best map of frontend-to-BFF communication.

### 11.1 Auth operations

- `LOGIN_MUTATION`
- `REGISTER_MUTATION`
- `ME_QUERY`
- `REFRESH_TOKEN_MUTATION`

### 11.2 Dashboard/analytics

- `DASHBOARD_METRICS_QUERY`

### 11.3 Jobs

- `JOBS_QUERY`
- `JOB_QUERY`
- `CREATE_JOB_MUTATION`
- `UPDATE_JOB_MUTATION`
- `DELETE_JOB_MUTATION`
- `PUBLISH_JOB_MUTATION`

### 11.4 Candidates

- `CANDIDATES_QUERY`
- `CANDIDATE_QUERY`
- `MATCHES_FOR_JOB_QUERY`
- `UPDATE_MATCH_STATUS_MUTATION`

### 11.5 Screening

- `SCREENING_SESSION_QUERY`
- `MY_SCREENING_SESSIONS_QUERY`
- `START_SCREENING_MUTATION`
- `ADVANCE_SCREENING_MUTATION`
- `SCREENING_UPDATED_SUBSCRIPTION`

### 11.6 Notifications

- `MY_NOTIFICATIONS_QUERY`
- `NEW_NOTIFICATION_SUBSCRIPTION`
- `MARK_NOTIFICATION_READ_MUTATION`
- `MARK_ALL_READ_MUTATION`

## 12. Screen-by-screen breakdown

## 12.1 Login screen

File:

- `frontend/hr-admin-dashboard/src/app/features/auth/login/login.component.ts`

How it is built:

- standalone Angular component
- reactive form
- Angular Material form controls
- NgRx dispatch for submit
- direct redirect for OAuth provider buttons

Backend connection:

- username/password -> GraphQL `login`
- OAuth buttons -> auth-service `/oauth2/authorization/{provider}?portal=hr-admin`

## 12.2 Dashboard / Analytics views

Analytics file:

- `frontend/hr-admin-dashboard/src/app/features/analytics/analytics.component.ts`

It directly calls:

- `DASHBOARD_METRICS_QUERY`

Returned fields:

- total jobs
- total candidates
- completed screenings
- hire rate
- average match score
- stage pass rates
- top skills in demand

Backend chain:

1. Angular Apollo query
2. gateway `dashboardMetrics()`
3. gateway calls:
   - `job-analyzer-service /metrics/dashboard`
   - `screening-bot-service /metrics/dashboard`
4. gateway merges them in `mergeDashboardMetrics(...)`

Reference:

- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/ScreeningGraphQLResolver.java`

This is one of the clearest BFF examples in the project.

## 12.3 Candidates list

File:

- `frontend/hr-admin-dashboard/src/app/features/candidates/candidates-list/candidates-list.component.ts`

Construction pattern:

- standalone component
- `ngModel` filters
- Angular Material table
- paginator
- NgRx selectors + dispatch

User interactions:

- type search
- choose screening status
- paginate
- click candidate row/detail

State flow:

1. component dispatches `loadCandidates(...)`
2. `CandidatesEffects.loadCandidates$`
3. Apollo `CANDIDATES_QUERY`
4. gateway `candidates(...)`
5. gateway calls resume-parser-service `/resumes?...`
6. reducer stores page of candidates
7. selector feeds table

Files:

- `frontend/hr-admin-dashboard/src/app/store/candidates/candidates.effects.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/CandidateGraphQLResolver.java`

## 12.4 Candidate detail

File:

- `frontend/hr-admin-dashboard/src/app/features/candidates/candidate-detail/candidate-detail.component.ts`

What it renders:

- identity and status chips
- skills
- experience
- education
- AI match score

Backend chain:

1. route param `id`
2. Apollo `CANDIDATE_QUERY`
3. gateway `candidate(id)`
4. gateway calls resume-parser-service `/resumes/{id}`

This is a good example of a detail page that bypasses NgRx for the actual entity fetch and uses Apollo query directly.

## 12.5 Job form

File:

- `frontend/hr-admin-dashboard/src/app/features/jobs/job-form/job-form.component.ts`

How it is built:

- reactive form
- Material inputs/selects
- chip-based skill editor
- create/edit mode by route param

State flow:

- edit mode dispatches `loadJob(id)`
- submit dispatches either:
  - `createJob({ input })`
  - `updateJob({ id, input })`

Backend chain:

1. NgRx jobs effect
2. GraphQL create/update mutation
3. gateway `createJob` or `updateJob`
4. gateway calls job-analyzer-service REST

## 12.6 Job detail

File:

- `frontend/hr-admin-dashboard/src/app/features/jobs/job-detail/job-detail.component.ts`

This page is especially useful to understand multi-service orchestration from the frontend.

It loads:

1. `JOB_QUERY`
2. `MATCHES_FOR_JOB_QUERY`

So one screen shows:

- job metadata from job-analyzer-service
- matched candidates from candidate-matcher-service

Through BFF:

- `job(id)` -> `JobGraphQLResolver`
- `matchesForJob(jobId)` -> `CandidateGraphQLResolver`

This is exactly the kind of “one frontend screen, two backend services” example to use in interviews.

## 12.7 Screening area

Files:

- `frontend/hr-admin-dashboard/src/app/store/screening/screening.actions.ts`
- `frontend/hr-admin-dashboard/src/app/store/screening/screening.store.effects.ts`
- `frontend/hr-admin-dashboard/src/app/store/screening/screening.store.reducer.ts`

What exists clearly in code:

- load all sessions
- load one screening session
- advance screening

Backend chain:

- `MY_SCREENING_SESSIONS_QUERY`
- `SCREENING_SESSION_QUERY`
- `ADVANCE_SCREENING_MUTATION`

through:

- `ScreeningGraphQLResolver`

to:

- screening-bot-service

This area is more store-driven than some detail pages.

## 12.8 Notifications page

File:

- `frontend/hr-admin-dashboard/src/app/features/notifications/notifications-list/notifications-list.component.ts`

It supports:

- initial query load
- live subscription updates
- mark one notification read
- mark all read
- paging in memory

Live update path:

1. frontend subscribes with current user id
2. gateway validates user id against JWT
3. RabbitMQ event enters gateway
4. gateway pushes GraphQL subscription event over websocket
5. Angular component upserts new notification into local list

Files:

- `frontend/hr-admin-dashboard/src/app/features/notifications/notifications-list/notifications-list.component.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/realtime/GatewaySubscriptionBroker.java`

## 13. How NgRx state is used in HR portal

The app mixes:

1. **NgRx for app-wide state and workflows**
2. **Apollo direct queries for page-local data**

### 13.1 Auth state

NgRx-heavy:

- login
- register
- refresh
- logout
- current user
- storage persistence

Files:

- `store/auth/*`

### 13.2 Candidates state

NgRx handles:

- candidate page loading
- selected candidate id
- loading/error states

Files:

- `store/candidates/*`

### 13.3 Jobs state

NgRx handles:

- jobs list
- load job
- create/update/delete/publish

Files:

- `store/jobs/*`

### 13.4 Screening state

NgRx handles:

- sessions list
- selected session
- advancing screening state

Files:

- `store/screening/*`

### 13.5 Direct Apollo in components

Some components query Apollo directly instead of going through NgRx:

- analytics
- candidate detail
- job detail
- shell notifications
- notifications list

That is worth mentioning in interviews because it shows a hybrid approach:

> “The app uses NgRx for long-lived workflow state, but some screens use Apollo queries directly when the data is mostly local to that component.”

## 14. Real-time / subscription architecture

### 14.1 Frontend side

Configured in:

- `frontend/hr-admin-dashboard/src/app/app.config.ts`

Mechanics:

- websocket client from `graphql-ws`
- Apollo `split(...)`
- subscriptions go to `/graphql-ws`

### 14.2 Gateway side

Files:

- `services/api-gateway/src/main/java/org/vinod/sha/gateway/config/GraphQLContextInterceptor.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/realtime/GatewaySubscriptionBroker.java`

What happens:

1. gateway receives websocket `connection_init`
2. extracts auth token
3. stores auth context for subscription resolvers
4. maintains sinks keyed by:
   - screening session id
   - job id
   - user id
5. listens to RabbitMQ events
6. republishes them to websocket subscribers

### 14.3 Why Redis appears here

`GatewaySubscriptionBroker` also has Redis backplane support so realtime events can be shared across multiple gateway instances.

That is a scale-out design detail worth mentioning for senior-level interviews.

## 15. End-to-end HR flows you should know

### 15.1 HR login

1. login form dispatches NgRx action
2. effect runs `LOGIN_MUTATION`
3. gateway forwards to auth-service
4. auth-service validates and returns tokens
5. reducer stores:
   - `sha_token`
   - `sha_refresh_token`
   - `sha_expires_at`
   - `sha_user`
6. effect navigates to dashboard

### 15.2 HR dashboard analytics

1. analytics component runs `DASHBOARD_METRICS_QUERY`
2. gateway calls job + screening metrics services
3. gateway merges response
4. Angular renders KPI cards, funnel bars, skills table

### 15.3 HR candidate review

1. candidates list dispatches `loadCandidates`
2. effect runs `CANDIDATES_QUERY`
3. gateway fetches resume-parser-service data
4. table renders list
5. user clicks candidate
6. candidate detail page fetches `candidate(id)`

### 15.4 HR job posting

1. form builds `CreateJobInput`
2. NgRx dispatches `createJob`
3. jobs effect calls `CREATE_JOB_MUTATION`
4. gateway `createJob` forwards to job-analyzer-service
5. state updates and app returns to jobs page

### 15.5 HR job-to-match view

1. job detail page fetches job details
2. same page fetches `matchesForJob(jobId)`
3. gateway fans out to job-analyzer-service and candidate-matcher-service
4. UI shows a combined hiring view on one screen

### 15.6 HR notifications

1. shell and notifications page query `myNotifications`
2. app subscribes to `newNotification(userId)`
3. gateway validates token/user match
4. gateway receives RabbitMQ domain events
5. subscription updates Angular UI live

## 16. How session/state differs between HR and candidate portals

This is a strong comparison answer.

### Common design

Both portals:

- use the gateway as BFF
- use JWT access token + refresh token
- use OAuth portal-aware redirecting
- rely on frontend-held auth state
- use server-side session only for OAuth handshake state

### HR portal specifics

- fully implemented NgRx auth lifecycle is visible
- localStorage keys are explicit
- Apollo websocket auth wiring is explicit
- guards and role-based route enforcement are explicit

### Candidate portal specifics

- architecture is documented and partly implemented
- OAuth callback pages are visible
- page-level GraphQL operations are visible
- some core bootstrap/store files are missing in this checkout

## 17. What to read in order for interview prep

### Must-read path

1. `frontend/README.md`
2. `frontend/hr-admin-dashboard/package.json`
3. `frontend/hr-admin-dashboard/src/app/app.config.ts`
4. `frontend/hr-admin-dashboard/src/app/app.routes.ts`
5. `frontend/hr-admin-dashboard/src/app/store/auth/auth.reducer.ts`
6. `frontend/hr-admin-dashboard/src/app/store/auth/auth.effects.ts`
7. `frontend/hr-admin-dashboard/src/app/core/interceptors/jwt.interceptor.ts`
8. `frontend/hr-admin-dashboard/src/app/features/auth/login/login.component.ts`
9. `frontend/hr-admin-dashboard/src/app/features/auth/oauth-callback/oauth-callback.component.ts`
10. `frontend/hr-admin-dashboard/src/app/features/shell/shell.component.ts`
11. `frontend/hr-admin-dashboard/src/app/graphql/queries/index.ts`

### Backend-side BFF mapping path

1. `services/api-gateway/src/main/resources/graphql/schema.graphqls`
2. `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`
3. `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/JobGraphQLResolver.java`
4. `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/CandidateGraphQLResolver.java`
5. `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/ScreeningGraphQLResolver.java`
6. `services/api-gateway/src/main/java/org/vinod/sha/gateway/config/GraphQLContextInterceptor.java`
7. `services/api-gateway/src/main/java/org/vinod/sha/gateway/realtime/GatewaySubscriptionBroker.java`
8. `services/auth-service/src/main/java/org/vinod/sha/auth/security/OAuth2AuthenticationSuccessHandler.java`

## 18. HR-portal interview summary

If you need to answer fast:

- The HR portal is an **Angular 17 standalone SPA** using **Angular Material**, **Apollo Angular**, **NgRx**, and **GraphQL WebSockets**.
- It talks to backend through a **GraphQL BFF gateway**, not directly to each microservice.
- Auth/session on the frontend is maintained with **NgRx + localStorage** using `sha_token`, `sha_refresh_token`, `sha_expires_at`, and `sha_user`.
- Silent refresh is handled by **NgRx effects** plus a **JWT interceptor**.
- OAuth is started directly against **auth-service**, but normal app data flows through the **gateway**.
- The gateway aggregates data from multiple services, especially for **dashboard analytics**, **job + matches views**, and **live notifications**.

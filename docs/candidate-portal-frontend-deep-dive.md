# Candidate Portal Frontend Deep Dive

This document is for interview prep and code-walkthrough study. It explains what the candidate portal uses on the frontend, how it talks to the backend through the BFF, how auth/session/state work, and which files you should read in order.

## 1. What frontend stack does the candidate portal use?

The repo-level frontend workspace documents the candidate portal as:

- **React + TypeScript**
- **Vite**
- **Apollo Client**
- **Redux Toolkit**
- **Ant Design**

Primary references:

- `frontend/README.md`
- `README.md`

Important caveat: in this checkout, the candidate portal is **only partially present**. The GraphQL operations and a few page components exist, but the main app bootstrap files that would usually prove routing, store wiring, and Apollo client initialization are not present in `frontend/candidate-portal/src/`. So this document is split into:

1. what is clearly implemented in the repo
2. what the architecture implies from the surrounding backend/BFF contracts
3. what is missing in this checkout

## 2. Candidate portal directory map

The implemented candidate portal files visible in this repo are:

- `frontend/candidate-portal/src/graphql/index.ts`
- `frontend/candidate-portal/src/graphql/operations.ts`
- `frontend/candidate-portal/src/graphql/mutations/auth.ts`
- `frontend/candidate-portal/src/graphql/mutations/resume.ts`
- `frontend/candidate-portal/src/graphql/mutations/screening.ts`
- `frontend/candidate-portal/src/graphql/queries/dashboard.ts`
- `frontend/candidate-portal/src/graphql/queries/jobs.ts`
- `frontend/candidate-portal/src/graphql/queries/notifications.ts`
- `frontend/candidate-portal/src/graphql/queries/screening.ts`
- `frontend/candidate-portal/src/graphql/types.ts`
- `frontend/candidate-portal/src/pages/Auth/OAuthCallbackPage.tsx`
- `frontend/candidate-portal/src/pages/AuthCallback/AuthCallbackPage.tsx`
- `frontend/candidate-portal/src/pages/GovtJobs/GovtJobsPage.tsx`
- `frontend/candidate-portal/nginx.conf`

What is notably **missing** from this checkout:

- `src/main.tsx`
- `src/App.tsx`
- `src/store/index.ts`
- `src/store/authSlice.ts`
- `src/apolloClient.ts`
- route definitions
- layout/dashboard/interview/login/register pages

Those missing files matter because `OAuthCallbackPage.tsx`, `AuthCallbackPage.tsx`, and `GovtJobsPage.tsx` import Redux store types and actions that are not present here, for example:

- `../../store/authSlice`
- `../../store`

So for interviews, say this carefully:

> “The candidate portal is intended to use React + Apollo + Redux Toolkit, but this repo snapshot only contains partial frontend source. I can still explain the end-to-end flow because the GraphQL operations, OAuth callback pages, and the gateway BFF contracts are present.”

## 3. High-level architecture: how the candidate portal talks to backend

The intended flow is:

```text
Candidate Browser
  -> Candidate React app
  -> /graphql and /api through frontend proxy / nginx
  -> API Gateway (BFF)
  -> Gateway GraphQL resolvers
  -> Downstream microservices
     - auth-service
     - job-analyzer-service
     - screening-bot-service
     - candidate-matcher-service
     - resume-parser-service
  -> databases / RabbitMQ / Redis
```

References:

- `frontend/README.md`
- `services/api-gateway/src/main/resources/application.yaml`
- `services/api-gateway/src/main/resources/graphql/schema.graphqls`
- `FLOW.md`

### Why this is a BFF

The gateway is not just a dumb router. It exposes a **frontend-shaped GraphQL contract** and internally calls multiple backend services using REST/WebClient.

Examples:

- `AuthGraphQLResolver` calls `auth-service`
- `JobGraphQLResolver` calls `job-analyzer-service`
- `ScreeningGraphQLResolver` calls `screening-bot-service`, `job-analyzer-service`, and `candidate-matcher-service`
- `CandidateGraphQLResolver` calls `resume-parser-service` and `candidate-matcher-service`

References:

- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/JobGraphQLResolver.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/ScreeningGraphQLResolver.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/CandidateGraphQLResolver.java`

That is the interview-friendly explanation of the BFF pattern in this project:

> “Frontend does not call each microservice directly. It talks mostly to the gateway GraphQL API, and the gateway aggregates, normalizes, authorizes, and fans out requests to the underlying services.”

## 4. Which backend entrypoints does candidate portal use?

From `frontend/README.md`:

- Candidate app local URL: `http://localhost:5173`
- GraphQL through candidate app proxy: `http://localhost:5173/graphql`
- Backend gateway target: `http://localhost:8000`

Gateway config:

- GraphQL HTTP endpoint: `/graphql`
- GraphQL WebSocket endpoint: `/graphql-ws`
- REST gateway routes also exist under `/api/...`

Reference:

- `services/api-gateway/src/main/resources/application.yaml`

### Practical meaning

In dev, the candidate frontend should call:

- **GraphQL queries/mutations** -> `/graphql`
- **GraphQL subscriptions** -> `/graphql-ws`
- possibly some REST endpoints via `/api/...` if implemented elsewhere

The frontend workspace README explicitly says both apps consume backend contracts through:

- `/graphql`
- `/api`

## 5. Candidate portal auth flow

The repo shows two layers of auth:

1. **GraphQL login/register/refresh** for username/password
2. **OAuth redirect flow** for Google/LinkedIn

### 5.1 GraphQL auth operations used by candidate portal

File:

- `frontend/candidate-portal/src/graphql/mutations/auth.ts`

Operations:

- `login(username, password)`
- `register(username, email, password, role)`
- `refreshToken(refreshToken)`

All return:

- `token`
- `refreshToken`
- `expiresIn`
- `user { id username email role }`

This contract matches gateway auth BFF:

- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`

The gateway resolver converts GraphQL auth calls into REST calls to:

- `POST http://auth-service:8001/api/auth/login`
- `POST http://auth-service:8001/api/auth/register`
- `POST http://auth-service:8001/api/auth/refresh`

Actual auth-service endpoints are defined in:

- `services/auth-service/src/main/java/org/vinod/sha/auth/controller/AuthController.java`

### 5.2 OAuth flow for candidate portal

Frontend callback pages:

- `frontend/candidate-portal/src/pages/Auth/OAuthCallbackPage.tsx`
- `frontend/candidate-portal/src/pages/AuthCallback/AuthCallbackPage.tsx`

These files reveal how OAuth is completed on the frontend:

1. backend redirects browser back with query params
2. frontend reads:
   - `accessToken`
   - `refreshToken`
   - `expiresIn`
   - optional `role`, `username`, `error`
3. frontend decodes JWT payload in the browser
4. frontend dispatches auth state into Redux
5. frontend navigates to `/dashboard`

The backend side of this flow is implemented in auth-service:

- `services/auth-service/src/main/java/org/vinod/sha/auth/security/OAuth2AuthenticationSuccessHandler.java`
- `services/auth-service/src/main/resources/application.yaml`

Configured redirect targets:

- candidate success redirect: `http://localhost:5173/auth/callback`
- HR success redirect: `http://localhost:4200/auth/callback`

### 5.3 How portal-aware OAuth routing works

This project has an important interview-worthy detail: it does **portal-aware OAuth routing**.

It is implemented through:

- `PortalAwareAuthorizationRequestResolver`
- `PortalAwareAuthorizationRequestRepository`
- `OAuth2AuthenticationSuccessHandler`

References:

- `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestResolver.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestRepository.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/security/OAuth2AuthenticationSuccessHandler.java`

How it works:

1. frontend starts OAuth with a `?portal=` hint
2. backend stores that hint in the **server-side session**
3. after OAuth callback, backend reads the stored portal
4. backend redirects user to the correct frontend callback URL

Important nuance:

- this uses **session state on the auth-service side**
- not frontend local state
- not cookies written manually by custom code

The older `OAuth2PortalOriginFilter` is now a no-op and explicitly says cookie-based tracking was replaced by session/state-based tracking:

- `services/auth-service/src/main/java/org/vinod/sha/auth/security/OAuth2PortalOriginFilter.java`

## 6. How candidate session/state is maintained

Even though the actual `authSlice.ts` file is missing in this checkout, the callback pages and workspace README give enough evidence to explain the design.

### 6.1 Browser-side auth/session state

Documented in `frontend/README.md`:

- access token persistence
- refresh token persistence
- token expiry tracking
- silent refresh before expiry

The callback pages dispatch:

- `setAuthFromOAuthCallback(payload)`
- `logout()`

from:

- `../../store/authSlice`

That strongly implies candidate auth state lives in **Redux Toolkit state**, backed by browser storage.

The expected browser-stored values mirror the HR portal approach:

- access token
- refresh token
- expiry time
- current user object

### 6.2 Access token usage

The frontend sends the JWT to the gateway in an `Authorization: Bearer <token>` header.

We know this because:

1. gateway resolvers use `@ContextValue(name = "Authorization")`
2. `GraphQLContextInterceptor` extracts the `Authorization` header for both HTTP and WebSocket GraphQL

Reference:

- `services/api-gateway/src/main/java/org/vinod/sha/gateway/config/GraphQLContextInterceptor.java`

That means candidate session is effectively:

1. **frontend owns tokens**
2. **gateway reads token from Authorization header**
3. **gateway derives current user / authorization context from the token**

### 6.3 `me` / current-user behavior

The gateway exposes:

- `me`

through:

- `AuthGraphQLResolver.me(...)`

This resolver parses the JWT from the incoming `Authorization` header and reconstructs the user object.

Reference:

- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`

That is an important interview point:

> “The frontend does not need to call auth-service directly for every page load. It can hydrate current-user state through the gateway `me` GraphQL query using the stored access token.”

## 7. Candidate screens: what exists and how they are created

Because the app shell files are missing, only a few screens can be described from actual code.

### 7.1 OAuth callback screens

Files:

- `frontend/candidate-portal/src/pages/Auth/OAuthCallbackPage.tsx`
- `frontend/candidate-portal/src/pages/AuthCallback/AuthCallbackPage.tsx`

These are React function components built with:

- `react-router-dom` for navigation and URL parsing
- `react-redux` for dispatching auth state
- `antd` for UI widgets (`Card`, `Alert`, `Spin`, `Typography`)

Screen responsibility:

- read callback query params
- validate callback payload
- decode JWT to derive user data
- normalize role
- write auth state to Redux
- redirect to dashboard

Interesting nuance:

- `AuthCallbackPage.tsx` explicitly maps all roles to `CANDIDATE` for candidate portal behavior, while preserving the backend JWT claims separately

That is useful in interviews because it shows **UI-role shaping** at the portal level.

### 7.2 Government jobs page

File:

- `frontend/candidate-portal/src/pages/GovtJobs/GovtJobsPage.tsx`

This is the clearest implemented candidate screen. It shows the typical React screen construction pattern used here:

#### UI libraries used

- `antd`
- `@ant-design/icons`
- React hooks
- Apollo hooks
- Redux selector
- React Router navigation

#### State inside the page

Local component state:

- `search`
- `searchVar`
- `applied`
- `applying`
- `sessionByJob`

Global auth state:

- `useSelector((s: RootState) => s.auth.user)`

#### Data fetching pattern

The page uses:

- `useQuery(GOVT_JOBS_QUERY)`
- `useMutation(START_SCREENING_MUTATION)`

Files:

- `frontend/candidate-portal/src/graphql/queries/jobs.ts`
- `frontend/candidate-portal/src/graphql/mutations/screening.ts`

This is the typical frontend-to-BFF pattern:

1. page mounts
2. Apollo sends GraphQL query to `/graphql`
3. gateway `jobs(...)` resolver handles it
4. gateway calls `job-analyzer-service`
5. normalized response comes back as `GraphQLPage<Job>`
6. React screen renders cards

#### What happens on Apply

When the user applies:

1. optional external job URL opens
2. page reads candidate `user.id` from auth state
3. page calls `startScreening(candidateId, jobId)`
4. gateway `startScreening` mutation forwards to screening-bot-service
5. response returns a screening session id
6. frontend navigates to:
   - `/interview?sessionId=<id>`

That is your end-to-end application flow for candidate side.

## 8. Candidate data operations available in GraphQL

Read these files together:

- `frontend/candidate-portal/src/graphql/index.ts`
- `frontend/candidate-portal/src/graphql/operations.ts`
- `frontend/candidate-portal/src/graphql/types.ts`

### Candidate-side operations visible in this repo

#### Auth

- `LOGIN_MUTATION`
- `REGISTER_MUTATION`
- `REFRESH_TOKEN_MUTATION`

File:

- `frontend/candidate-portal/src/graphql/mutations/auth.ts`

#### Resume

- `UPLOAD_RESUME_MUTATION`

File:

- `frontend/candidate-portal/src/graphql/mutations/resume.ts`

Note the mismatch:

- frontend mutation shape is `uploadResume(fileName, contentBase64, contentType)`
- gateway schema shows `uploadResume(candidateId: ID!, file: Upload!): Candidate!`

Reference:

- `services/api-gateway/src/main/resources/graphql/schema.graphqls`

This is a good interview observation:

> “The candidate frontend contracts appear partially out of sync with the gateway schema in this checkout, so I would verify the latest branch or synced contracts before changing resume upload.”

#### Jobs

- `JOBS_QUERY`
- `GOVT_JOBS_QUERY`

File:

- `frontend/candidate-portal/src/graphql/queries/jobs.ts`

These hit gateway query:

- `jobs(page, size, search, source)`

BFF implementation:

- `JobGraphQLResolver.jobs(...)`

#### Dashboard

- `DASHBOARD_METRICS_QUERY` using `myCandidateDashboard`

File:

- `frontend/candidate-portal/src/graphql/queries/dashboard.ts`

BFF implementation:

- `ScreeningGraphQLResolver.myCandidateDashboard(...)`

This resolver is particularly interview-worthy because it aggregates across services:

1. fetches **my screening sessions** from screening-bot-service
2. fetches **my matches/applications** from candidate-matcher-service
3. fetches **open jobs count** from job-analyzer-service
4. merges them into one candidate-specific dashboard payload

That is classic BFF aggregation.

#### Screening

- `START_SCREENING_MUTATION`
- `SUBMIT_SCREENING_RESPONSE_MUTATION`
- `SCREENING_SESSION_QUERY`

Files:

- `frontend/candidate-portal/src/graphql/mutations/screening.ts`
- `frontend/candidate-portal/src/graphql/queries/screening.ts`

BFF implementation:

- `ScreeningGraphQLResolver.startScreening(...)`
- `ScreeningGraphQLResolver.submitScreeningResponse(...)`
- `ScreeningGraphQLResolver.screeningSession(...)`

#### Notifications

- `MY_NOTIFICATIONS_QUERY`
- `NEW_NOTIFICATION_SUBSCRIPTION`

File:

- `frontend/candidate-portal/src/graphql/queries/notifications.ts`

Gateway subscription implementation:

- `SubscriptionGraphQLResolver.newNotification(...)`
- `GatewaySubscriptionBroker`

## 9. Candidate real-time flow

The GraphQL subscription side is implemented in the gateway.

Relevant files:

- `services/api-gateway/src/main/java/org/vinod/sha/gateway/config/GraphQLContextInterceptor.java`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/realtime/GatewaySubscriptionBroker.java`

### How candidate notifications work end-to-end

1. candidate frontend subscribes to `newNotification(userId)`
2. subscription goes to `/graphql-ws`
3. gateway validates that the subscribed `userId` matches the authenticated JWT user id
4. backend events arrive through RabbitMQ
5. gateway converts them into GraphQL subscription payloads
6. gateway pushes them to subscribed frontend clients

Example event origins:

- `CandidateMatchedEvent`
- `ScreeningCompletedEvent`

Gateway then emits:

- match notifications
- screening notifications
- screening session updates

This is a strong interview answer for:

> “How does the portal get live updates?”

## 10. End-to-end candidate flows you should study

### 10.1 Candidate login with username/password

1. React login page would call `LOGIN_MUTATION`
2. candidate frontend sends GraphQL mutation to `/graphql`
3. gateway `AuthGraphQLResolver.login(...)` calls auth-service REST `/login`
4. auth-service validates credentials and creates:
   - access token
   - refresh token
   - user payload
5. gateway normalizes auth payload
6. frontend stores token/session state in Redux + browser storage
7. future GraphQL calls send `Authorization` header

Files:

- `frontend/candidate-portal/src/graphql/mutations/auth.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/controller/AuthController.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/service/AuthService.java`

### 10.2 Candidate OAuth login

1. frontend redirects browser to auth-service OAuth endpoint with `?portal=candidate`
2. Spring Security stores OAuth request and portal hint in server session
3. provider authenticates user
4. auth-service success handler creates JWTs and decides redirect target
5. browser is redirected to candidate callback page with tokens in query string
6. callback page dispatches `setAuthFromOAuthCallback`
7. candidate is navigated to dashboard

Files:

- `frontend/candidate-portal/src/pages/Auth/OAuthCallbackPage.tsx`
- `frontend/candidate-portal/src/pages/AuthCallback/AuthCallbackPage.tsx`
- `services/auth-service/src/main/java/org/vinod/sha/auth/security/OAuth2AuthenticationSuccessHandler.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestResolver.java`
- `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestRepository.java`

### 10.3 Candidate browse jobs

1. page calls `GOVT_JOBS_QUERY`
2. GraphQL request hits gateway `/graphql`
3. gateway `jobs(...)` resolver constructs REST URI
4. gateway calls job-analyzer-service
5. gateway normalizes job data shape
6. React renders cards with status, skills, salary, source, company

Files:

- `frontend/candidate-portal/src/pages/GovtJobs/GovtJobsPage.tsx`
- `frontend/candidate-portal/src/graphql/queries/jobs.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/JobGraphQLResolver.java`

### 10.4 Candidate apply and start screening/interview flow

1. user clicks Apply
2. frontend calls `startScreening(candidateId, jobId)`
3. gateway forwards to screening-bot-service `/sessions`
4. screening-bot-service creates a screening session
5. session id returns to frontend
6. frontend navigates to `/interview?sessionId=<id>`
7. interview screen would query `screeningSession(id)` and submit stage answers with `submitScreeningResponse(...)`

Files:

- `frontend/candidate-portal/src/pages/GovtJobs/GovtJobsPage.tsx`
- `frontend/candidate-portal/src/graphql/mutations/screening.ts`
- `frontend/candidate-portal/src/graphql/queries/screening.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/ScreeningGraphQLResolver.java`

### 10.5 Candidate dashboard

1. dashboard page calls `myCandidateDashboard`
2. gateway extracts user id from JWT
3. gateway fetches:
   - my sessions
   - my matches
   - open jobs count
4. gateway merges data into dashboard metrics
5. React dashboard renders summary cards / activity

Files:

- `frontend/candidate-portal/src/graphql/queries/dashboard.ts`
- `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/ScreeningGraphQLResolver.java`

## 11. How frontend screens are built in this portal

From the implemented React pages, the screen-building pattern is:

1. **Function component**
2. **Local UI state with `useState`**
3. **Navigation with `react-router-dom`**
4. **Server data with Apollo hooks**
5. **Auth/global identity from Redux**
6. **Ant Design components for layout and form controls**

Concrete example:

- `GovtJobsPage.tsx`

Pattern inside that file:

- query -> `useQuery`
- mutation -> `useMutation`
- global current user -> `useSelector`
- navigation -> `useNavigate`
- page-local flags -> `useState`
- rendering -> Ant Design `Card`, `Input`, `Button`, `Tag`, `Alert`

That is a good interview answer for:

> “How are frontend screens created in this project?”

## 12. What to read in order for interview prep

### Fast path

1. `frontend/README.md`
2. `frontend/candidate-portal/src/graphql/types.ts`
3. `frontend/candidate-portal/src/graphql/mutations/auth.ts`
4. `frontend/candidate-portal/src/graphql/queries/jobs.ts`
5. `frontend/candidate-portal/src/graphql/mutations/screening.ts`
6. `frontend/candidate-portal/src/pages/GovtJobs/GovtJobsPage.tsx`
7. `frontend/candidate-portal/src/pages/AuthCallback/AuthCallbackPage.tsx`
8. `services/api-gateway/src/main/resources/graphql/schema.graphqls`
9. `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/AuthGraphQLResolver.java`
10. `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/JobGraphQLResolver.java`
11. `services/api-gateway/src/main/java/org/vinod/sha/gateway/graphql/ScreeningGraphQLResolver.java`
12. `services/api-gateway/src/main/java/org/vinod/sha/gateway/config/GraphQLContextInterceptor.java`

### Deep path

After the above, read:

1. `services/auth-service/src/main/java/org/vinod/sha/auth/service/AuthService.java`
2. `services/auth-service/src/main/java/org/vinod/sha/auth/security/OAuth2AuthenticationSuccessHandler.java`
3. `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestResolver.java`
4. `services/auth-service/src/main/java/org/vinod/sha/auth/security/PortalAwareAuthorizationRequestRepository.java`
5. `services/api-gateway/src/main/java/org/vinod/sha/gateway/realtime/GatewaySubscriptionBroker.java`

## 13. Candidate-portal interview summary

If someone grills you, the crisp summary is:

- The candidate portal is intended to be **React + TypeScript + Vite + Apollo + Redux Toolkit + Ant Design**.
- It follows a **BFF pattern** by talking mainly to the **GraphQL gateway** instead of directly to each microservice.
- The gateway exposes frontend-friendly GraphQL operations and internally calls auth, jobs, screening, matching, and resume services.
- Candidate auth state is maintained in **frontend token state** plus **browser persistence**, while OAuth portal routing uses **server-side session state** inside auth-service.
- Implemented candidate pages in this repo show React hooks, Apollo queries/mutations, Redux auth access, and route navigation.
- The candidate portal source is **incomplete in this checkout**, so you can explain the real architecture confidently while also noting which app bootstrap files are missing.

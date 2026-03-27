# Frontend Workspace

This folder contains the two frontend applications for Smart Hiring Assistant:

- `candidate-portal` (React + Vite + Apollo + Redux Toolkit)
- `hr-admin-dashboard` (Angular + Material + Apollo + NgRx)

Both apps consume API contracts through the gateway (`/graphql`, `/api`) and are intended to be built and deployed independently.

## Apps At A Glance

| App | Path | Tech Stack | Default Local Port |
| --- | --- | --- | --- |
| Candidate Portal | `frontend/candidate-portal` | React, TypeScript, Ant Design, Apollo Client, Redux Toolkit | `5173` |
| HR Admin Dashboard | `frontend/hr-admin-dashboard` | Angular, TypeScript, Angular Material, Apollo Angular, NgRx | `4200` |

## Prerequisites

- Node.js 20+
- npm 10+
- API Gateway running locally (for proxied `/graphql` and `/api` calls)

## Quick Start

### 1) Candidate Portal (React)

```bash
cd frontend/candidate-portal
npm install
npm run dev
```

Build:

```bash
cd frontend/candidate-portal
npm run build
```

### 2) HR Admin Dashboard (Angular)

```bash
cd frontend/hr-admin-dashboard
npm install
npm start
```

Build:

```bash
cd frontend/hr-admin-dashboard
npm run build
```

## API Integration

Both apps use local proxy settings during development:

- Candidate portal proxy: `frontend/candidate-portal/vite.config.ts`
- HR dashboard proxy: `frontend/hr-admin-dashboard/proxy.conf.json`

Expected local target:

- Gateway: `http://localhost:8000`

## Authentication Notes

Both frontends now include:

- Access token + refresh token persistence
- Token expiry tracking
- Silent refresh before expiry (best effort)
- Candidate role guard for candidate portal protected routes

Main auth files:

- Candidate: `frontend/candidate-portal/src/store/authSlice.ts`
- Candidate Apollo refresh/retry: `frontend/candidate-portal/src/apolloClient.ts`
- HR auth state/effects: `frontend/hr-admin-dashboard/src/app/store/auth/`

## Responsive UI Notes

- Candidate shell supports desktop sidebar collapse and mobile drawer.
- HR dashboard shell supports side-nav on desktop and overlay drawer on smaller screens.
- Core pages are adjusted for tablet/phone layout behavior.

## Contract Boundary

Frontend should treat backend as contract-only integration:

- GraphQL operations and shared frontend GraphQL types are under:
  - `frontend/candidate-portal/src/graphql/`
  - `frontend/hr-admin-dashboard/src/app/graphql/`
- Source-of-truth contract workspace is at repo root:
  - `contracts/`

If contracts are changed, sync and validate from repo root:

```bash
bash scripts/sync-contracts.sh
bash scripts/sync-contracts.sh --check
```

## CI / Deployment Model

CI is split so frontends are built independently from backend services:

- Frontend workflow: `.github/workflows/frontend-apps.yml`
- Backend workflow: `.github/workflows/backend-services.yml`
- Contract workflow: `.github/workflows/contracts.yml`

## Troubleshooting

### Node modules/type resolution issues

```bash
cd frontend/candidate-portal
rm -rf node_modules package-lock.json
npm install
```

```bash
cd frontend/hr-admin-dashboard
rm -rf node_modules package-lock.json
npm install
```

### Proxy/API errors

- Verify gateway is running on `localhost:8000`
- Verify `proxy.conf.json` / `vite.config.ts` target configuration
- Check browser network tab for `/graphql` and `/api` failures


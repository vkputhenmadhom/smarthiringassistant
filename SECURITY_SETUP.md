# Security Setup

Secrets, sign-in, accounts and the platform's security model. For first-time setup steps, see
[STARTUP_GUIDE.md](STARTUP_GUIDE.md).

## Secrets

All secrets live in `.env` at the repository root. It is git-ignored and excluded from Docker build
contexts by `.dockerignore`; `.env.example` lists every variable with placeholders. Keep it readable only
by you (`chmod 600 .env`).

| Secret | How to choose it |
|---|---|
| `JWT_SECRET` | At least 32 random bytes: `openssl rand -base64 48`. auth-service signs with it and the gateway verifies with it; the HMAC strength (HS256/384/512) follows the key length. Rotating it signs everyone out. |
| `POSTGRES_PASSWORD`, `MONGO_PASSWORD`, `RABBITMQ_PASSWORD`, `REDIS_PASSWORD`, `GRAFANA_ADMIN_PASSWORD` | Unique random values. Compose refuses to start if any is missing. They are applied when a volume is first created. |
| `RESUME_ENCRYPTION_KEY` | At least 32 characters: `openssl rand -base64 48`. Encrypts resume data (see below). **Back it up**: data encrypted with a lost key can't be recovered. |
| `GOOGLE_CLIENT_SECRET` | From Google Cloud Console (below). |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | From the provider's console. Only ai-integration-service receives them. |

In Kubernetes the same values come from the `smart-hiring-secrets` Secret (synced from AWS Secrets Manager
by External Secrets in the overlays). Keys: `jwt-secret`, `postgres-password`, `rabbitmq-password`,
`redis-password`, `mongodb-uri`, `google-client-id`, `google-client-secret`, `resume-encryption-key`, `openai-api-key`, and the
mail/Twilio keys. See `infrastructure/kubernetes/base/secret-template.yaml`.

## Resume data encryption

resume-parser encrypts uploaded resume files and the extracted name, email, phone, location and summary
with AES-256-GCM, using a key derived from `RESUME_ENCRYPTION_KEY`. The service refuses to start without
a key of at least 32 characters, and rejects the old public default.

**Changing the key** (rotation):

1. Set the new key in `RESUME_ENCRYPTION_KEY` and put the old one in `RESUME_ENCRYPTION_PREVIOUS_KEYS`
   (comma-separated if several).
2. Restart resume-parser. At startup it re-encrypts everything still under a previous key and logs, e.g.,
   `files Result[reEncrypted=11, current=0, unreadable=0]`.
3. When a restart logs `Nothing is encrypted under a previous key any more`, remove
   `RESUME_ENCRYPTION_PREVIOUS_KEYS`.

**Upgrading from earlier releases:** they used the public default `change-me-with-32-plus-char-secret`.
If your database holds resumes from before this change, use that value as the previous key once, as above.

If a stored value can't be decrypted with any configured key, the service logs a warning instead of
returning garbled data; check that the right previous key is configured.

## Google sign-in

1. In Google Cloud Console create an OAuth client (type *Web application*).
2. Add the authorized redirect URI:
   - `http://localhost:8001/api/auth/login/oauth2/code/google` (local)
   - `https://<your-auth-host>/api/auth/login/oauth2/code/google` (production)
3. Put the client ID and secret in `.env` as `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.

The OAuth flow goes to auth-service directly (port 8001), not through the gateway: the request and the
callback must reach the same server session. Each portal adds `?portal=candidate` or `?portal=hr-admin`
to the login URL; auth-service keeps it with the OAuth `state` and redirects back to the matching portal
(`/auth/callback` with `accessToken`, `refreshToken`, `expiresIn`, `provider`, `role`), or with
`?error=account_pending` / `account_rejected` for HR accounts that aren't approved.

LinkedIn is configured in auth-service but has no credentials by default.

## Accounts and approval

| Account | How it is created | Can sign in |
|---|---|---|
| Candidate (`JOB_SEEKER`) | Registration or Google sign-in from the candidate portal | Immediately |
| HR (`ADMIN`, `RECRUITER`, `HIRING_MANAGER`) | Registration or Google sign-in from the HR dashboard | Only after an administrator approves it |
| First administrator | Google sign-in with an email listed in `BOOTSTRAP_ADMIN_EMAILS` | Immediately, as `ADMIN` |

- Pending and rejected accounts get no tokens; login and token refresh are refused with a clear message.
- Administrators approve or reject on the HR dashboard's **Approvals** page and can change the role when
  approving (GraphQL `pendingUsers` / `approveUser` / `rejectUser`, backed by auth-service `/admin/users`).
- `BOOTSTRAP_ADMIN_EMAILS` is trusted only for emails Google reports as verified, so nobody can claim an
  admin address by registering it with a password.

## How requests are authorized

- **API gateway** (the only public entry point besides OAuth):
  - verifies every bearer token's signature and expiry; forged or expired tokens get 401
  - public paths: `/api/auth/**`, `/graphql`, `/graphql-ws`, `/graphiql`, health/info/prometheus actuator
  - every other REST route needs a valid token; `/api/notifications/**` needs `ADMIN`
- **GraphQL** is authorized per top-level field, over HTTP and WebSocket:
  - public: `login`, `register`, `refreshToken`, `jobs`, `job`
  - HR roles only: job management, candidates, matching, screening administration, dashboard metrics
  - `ADMIN` only: account approval
  - everything else needs any valid token
- **Internal services** publish no ports in Docker Compose; only the gateway, auth-service (OAuth) and
  the frontends are reachable from the host. ai-integration-service is internal-only, so its per-user rate
  limit trusts the calling service.
- **Rate limiting:** Redis-backed per-IP limits at the gateway; per-user limits on AI completions.
- **CORS:** allow-list via `APP_CORS_ALLOWED_ORIGINS` (default `http://localhost:4200,http://localhost:5173`).
  Set your real frontend origins in production.

## Container hardening

- Backend images run as an unprivileged `app` user; the application jar is read-only to it.
- Compose drops all Linux capabilities and sets `no-new-privileges` for every backend service.
- JVM heap is sized from the container memory limit, and the JVM exits on OutOfMemoryError so Docker
  restarts it.
- Infrastructure images are pinned to explicit versions.

## Known gaps

- **Frontends run nginx as root** (needed to bind port 80); an unprivileged nginx image on port 8080
  would remove that.
- **Kustomize** doesn't pass `BOOTSTRAP_ADMIN_EMAILS`; Helm does (`services.authService.bootstrapAdminEmails`).
- **Tokens are kept in `localStorage`** by the frontends, so an XSS bug could read them.

## Checklist before deploying

- [ ] `JWT_SECRET` and every infrastructure password are unique random values
- [ ] `APP_CORS_ALLOWED_ORIGINS` lists only your real frontend origins
- [ ] Google redirect URI points at your production auth host
- [ ] `BOOTSTRAP_ADMIN_EMAILS` contains only intended administrators
- [ ] `RESUME_ENCRYPTION_KEY` is a unique random value and is backed up
- [ ] TLS terminates in front of the gateway and auth-service

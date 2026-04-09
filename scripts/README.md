# Smoke Scripts

## GraphQL WS Auth Smoke

`smoke-ws-auth-init.sh` validates GraphQL WebSocket `connection_init` auth key compatibility by testing:

- `Authorization`
- `authorization`
- `authToken`

For each key, it verifies the server acknowledges the connection and accepts a protected `newNotification(userId)` subscription without an auth error.

### Inputs

- `AUTH_TOKEN` (required): JWT (with or without `Bearer ` prefix)
- `USER_ID` (required): user id that matches the token subject/id claim
- `GRAPHQL_WS_URL` (optional): default derived from `GATEWAY_BASE_URL` + `/graphql-ws`
- `GATEWAY_BASE_URL` (optional): default `http://localhost:8000`
- `TIMEOUT_MS` (optional): default `3500`

### Run

```bash
cd "/Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant"
AUTH_TOKEN="<jwt>" USER_ID="<user-id>" ./scripts/smoke-ws-auth-init.sh
```

Or via npm in `scripts/`:

```bash
cd "/Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant/scripts"
AUTH_TOKEN="<jwt>" USER_ID="<user-id>" npm run smoke:ws-auth
```


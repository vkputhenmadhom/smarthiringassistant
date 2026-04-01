# LinkedIn Demo Script (One Command)

This guide runs a complete, recordable flow with a clean PASS/FAIL summary:

- candidate register
- candidate login
- JWT validation
- protected resume upload/parse
- matching
- screening (create -> respond -> advance -> decision)
- notification proof points

## Prerequisites

- Docker Desktop running
- Services up (`docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build`)
- `.env` present with `JWT_SECRET` set

## Run

From project root:

```bash
chmod +x scripts/linkedin-demo.sh
./scripts/linkedin-demo.sh
```

## Optional: start stack from script

```bash
./scripts/linkedin-demo.sh --start-stack
```

## Optional endpoint overrides

```bash
AUTH_BASE_URL=http://localhost:8001/api/auth \
RESUME_BASE_URL=http://localhost:8002/api/resumes \
MATCH_BASE_URL=http://localhost:8003/api/matches \
SCREEN_BASE_URL=http://localhost:8006/api/screening \
NOTIFY_BASE_URL=http://localhost:8007/api/notifications \
./scripts/linkedin-demo.sh
```

## Expected result

At the end, the script prints:

- a per-check PASS/FAIL list
- totals `PASS=<n> FAIL=<n>`

Exit code behavior:

- `0` if all checks pass
- non-zero if any check fails


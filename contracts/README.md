# Contracts Workspace

This directory is the source of truth for API contracts shared between frontends and backend services.

## Structure

- `graphql/gateway/schema.graphqls` - Gateway GraphQL contract used by both frontends.
- `openapi/` - Generated or curated OpenAPI documents per service.
- `grpc/` - gRPC contract snapshots or references.
- `events/` - Event contracts (AsyncAPI/schema docs).

## Workflow

1. Edit contract files in `contracts/` first.
2. Sync runtime copies into services using:

```bash
bash scripts/sync-contracts.sh
```

3. Validate sync status in CI or locally:

```bash
bash scripts/sync-contracts.sh --check
```

4. Update contract versions and compatibility notes in `contracts/VERSIONING.md`.

## Why this exists

This workspace decouples frontend/backend development by making contract changes explicit and reviewable before service implementation details.


# Contract Versioning Policy

## Goals

- Allow frontend and backend releases to move independently.
- Detect and communicate breaking API changes early.
- Keep compatibility expectations explicit.

## Version Tracks

- `contract/graphql/gateway` - GraphQL schema compatibility.
- `contract/openapi/<service>` - REST API contract compatibility per service.
- `contract/grpc/<package>` - gRPC/proto compatibility.
- `contract/events/<domain>` - Event payload compatibility.

## SemVer Guidance

- `MAJOR`: Breaking change to existing fields/types/operations.
- `MINOR`: Backward-compatible additions.
- `PATCH`: Non-functional contract clarifications/docs/examples.

## Breaking Change Rules

GraphQL examples considered breaking:

- Removing a field or operation.
- Changing a field type incompatibly.
- Making optional args/fields required.

## Release Coordination

1. Contract PR merged first.
2. Backend implementation can follow independently.
3. Frontend migration can follow independently.
4. CI contract checks enforce compatibility between source contract and runtime schema copies.

## Change Log Format

For each contract bump, include:

- Version: `contract/graphql/gateway@X.Y.Z`
- Date
- Change summary
- Breaking? yes/no
- Migration notes


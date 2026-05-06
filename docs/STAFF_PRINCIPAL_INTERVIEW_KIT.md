# Staff/Principal Interview Kit - Smart Hiring Assistant

This document contains domain-wise interview questions, model answers, and panel scripts for Staff and Principal engineering loops based on the Smart Hiring Assistant stack.

## Scope and Stack

- Frontend: React, TypeScript, Apollo Client, Redux Toolkit, realtime subscription UX
- API Gateway: Spring GraphQL, subscriptions, authn/authz boundaries, Redis backplane
- Microservices: Spring Boot, RabbitMQ event-driven workflows, service contracts
- Database: PostgreSQL, Redis, document-style data patterns where applicable
- Deployment: Kubernetes/OpenShift patterns, AWS-aligned IAM/secrets/serverless integrations

## Interview Calibration Rubric

- `1 - Weak`: Surface-level knowledge, no production-grade reasoning
- `2 - Partial`: Correct concepts but weak trade-offs or operational details
- `3 - Strong`: Production-ready design and clear failure-mode handling
- `4 - Exceptional`: Cross-team strategy, governance, and long-term architecture direction

### Evaluation Dimensions

- Architecture depth
- Trade-off quality
- Reliability and failure handling
- Security and least-privilege posture
- Observability and SLO fluency
- Execution realism
- Leadership and influence

---

## 1) Frontend (Questions + Model Answers)

### Q1. How would you design frontend state for local UI state, server state, and realtime updates?
**Model answer:**
- Keep UI-only state in component/hooks.
- Keep business workflow/session state in Redux Toolkit.
- Keep server entities in Apollo normalized cache.
- Route subscription events into Apollo first, and only mirror to Redux when domain workflow state changes.
- Enforce a no-duplication rule for entity ownership to prevent drift and race bugs.

### Q2. What is your auth/token lifecycle strategy in an SPA using GraphQL and REST?
**Model answer:**
- Use short-lived access token + rotating refresh token.
- Implement silent refresh with mutex to avoid refresh storms.
- Queue/replay failed requests on 401 after successful refresh.
- Fail closed: clear session and force re-auth if refresh fails.
- Add audit telemetry for token refresh failures and suspicious retries.

### Q3. How do you prevent duplicate/missed realtime UI events?
**Model answer:**
- Include event IDs and timestamps in payloads.
- Make reducers idempotent using dedupe keys.
- Reconnect with last-seen cursor where possible.
- Use fallback poll for reconciliation after prolonged disconnect.
- Prefer at-least-once delivery with idempotent client handling.

### Q4. How do you keep large realtime lists performant?
**Model answer:**
- Use virtualization for long lists.
- Memoize expensive selectors and component subtrees.
- Batch subscription-driven updates to reduce re-renders.
- Split bundles by route and lazy-load heavy screens.
- Track p95 interaction latency and Core Web Vitals in production.

### Q5. What test strategy is interview-ready for this frontend?
**Model answer:**
- Unit tests for reducers/hooks/utilities.
- Integration tests for GraphQL, auth refresh, and error boundaries.
- Contract tests for GraphQL operations to catch schema drift.
- Small set of deterministic E2E golden paths for release confidence.
- Gate releases on flaky-test thresholds and failure trend visibility.

---

## 2) API Gateway (Questions + Model Answers)

### Q1. How do you enforce user/tenant isolation in a GraphQL gateway?
**Model answer:**
- Authenticate at request/subscription entry.
- Authorize at resolver/field level with deny-by-default.
- Never trust client-provided subject IDs; derive from verified token claims.
- Standardize auth context propagation to downstream services.
- Back policy checks with contract and security tests.

### Q2. What failure modes exist in Redis backplane + RabbitMQ eventing?
**Model answer:**
- Duplicate, delayed, out-of-order, and dropped events are expected.
- Design consumers and gateway emits to be idempotent.
- Use retry + DLQ for poison messages.
- Monitor lag, emit failures, and subscription delivery health.
- Define reliability SLOs and escalation thresholds.

### Q3. How do you enforce "users subscribe only to their own notifications"?
**Model answer:**
- Validate JWT signature/claims server-side.
- Extract authenticated user ID from trusted claims only.
- Compare claim identity to requested subscription key.
- Reject mismatches with explicit access-denied outcomes.
- Add negative tests for spoofed headers and token tampering.

### Q4. How would you debug "subscriptions connected but no events"?
**Model answer:**
- Trace path: event producer -> broker -> gateway listener -> sink emit -> websocket send.
- Add correlation IDs across this path.
- Inspect queue lag/consumer health and Redis pub/sub delivery metrics.
- Verify authorization and routing key alignment.
- Use runbooks with stepwise diagnosis and rollback options.

### Q5. How do you evolve gateway ownership from single team to platform model?
**Model answer:**
- Introduce schema governance and deprecation policy.
- Define ownership boundaries by domain.
- Require backward-compatible releases and consumer contract tests.
- Roll out canary-based schema changes for safety.
- Create a lightweight API review council to reduce fragmentation.

---

## 3) Microservices (Questions + Model Answers)

### Q1. How do you decompose hiring workflows without chatty service calls?
**Model answer:**
- Align services to bounded contexts.
- Keep only UX-critical paths synchronous.
- Offload non-critical steps to asynchronous event flows.
- Use coarse-grained APIs to reduce call amplification.
- Periodically review service coupling and call graph complexity.

### Q2. How do you make event consumers idempotent and replay-safe?
**Model answer:**
- Require event IDs/idempotency keys in contracts.
- Persist processed keys with TTL or durable dedupe storage.
- Use upserts and side-effect guards.
- Keep retries safe and route poison messages to DLQ.
- Provide replay tooling for backfills and incident recovery.

### Q3. When do you pick sync vs async communication?
**Model answer:**
- Sync for immediate user response dependencies.
- Async for decoupling, burst handling, and throughput isolation.
- Define explicit timeout, retry, and compensation behaviors.
- Document consistency expectations per workflow.
- Reassess when latency/SLO pressure changes.

### Q4. What RabbitMQ reliability patterns do you expect at Staff/Principal level?
**Model answer:**
- Durable queues/exchanges and explicit acks.
- Controlled prefetch and consumer concurrency tuning.
- Retry queues with exponential backoff + DLQ.
- Lag-based autoscaling and alerting.
- Chaos testing for broker outages and partition events.

### Q5. How do you secure service-to-service communication?
**Model answer:**
- Use workload identities and least-privilege access.
- Rotate secrets and eliminate long-lived credentials.
- Enforce network policies and ingress restrictions.
- Validate provenance of deployed artifacts.
- Back controls with periodic audit and policy-as-code checks.

---

## 4) Database (Questions + Model Answers)

### Q1. How do you choose PostgreSQL vs Redis vs document store?
**Model answer:**
- PostgreSQL for transactional consistency and relational queries.
- Redis for ephemeral cache, pub/sub, and low-latency transient state.
- Document store for flexible payloads and variable schema patterns.
- Decisions are driven by access patterns, consistency, and lifecycle.
- Revisit choices when scale or query profile changes.

### Q2. How do you design schema/indexes for matching and screening workflows?
**Model answer:**
- Start with top read/write query shapes.
- Add targeted composite indexes for frequent filters/sorts.
- Avoid over-indexing on write-heavy tables.
- Measure slow queries continuously.
- Tie index changes to measurable SLO or throughput gains.

### Q3. How do you handle concurrent event-driven writes safely?
**Model answer:**
- Use optimistic locking/versioning for conflicting updates.
- Use idempotent upserts with deterministic merge behavior.
- Wrap critical paths in transactions where needed.
- Add reconciliation jobs for eventual consistency drift.
- Keep replay and audit capability for post-incident fixes.

### Q4. What Redis operational concerns matter most?
**Model answer:**
- Connection pool sizing and timeout tuning.
- Memory management and eviction policy impact.
- Hot key/subscriber imbalance detection.
- Replication/failover behavior under load.
- Metrics for pub/sub lag, drop risk, and reconnect churn.

### Q5. What is your low-downtime migration strategy?
**Model answer:**
- Use expand-contract migration pattern.
- Release code that handles both old/new schemas.
- Backfill data asynchronously where needed.
- Remove legacy schema only after read/write cutover validation.
- Keep rollback path and migration observability.

---

## 5) Deployment Strategy (Questions + Model Answers)

### Q1. How do you run containerized microservices and serverless components together?
**Model answer:**
- Keep steady-state services on Kubernetes.
- Use serverless for bursty or isolated stateless workloads.
- Standardize contracts, telemetry, and security controls across runtimes.
- Define ownership and release cadence per runtime type.
- Govern by cost/perf/reliability scorecards.

### Q2. How do you manage config/secrets securely across environments?
**Model answer:**
- Non-sensitive config in ConfigMaps.
- Secrets in external secrets manager with rotation.
- Prefer workload identity to static credentials.
- Enforce least privilege and scoped access policies.
- Audit access and rotation compliance continuously.

### Q3. What progressive delivery strategy fits this platform?
**Model answer:**
- Canary or blue-green for gateway and critical services.
- Health and SLO gates for promotion.
- Automatic rollback on burn-rate/error threshold breach.
- Contract checks before rollout.
- Limit blast radius with staged traffic shifts.

### Q4. Which SLOs/SLIs should drive operations?
**Model answer:**
- API availability, p95/p99 latency, error rates.
- Queue lag and consumer success rate.
- Subscription delivery success and reconnect rate.
- Deployment failure rate and MTTR.
- Use error budgets to govern release velocity.

### Q5. How do you handle environment promotion and compliance?
**Model answer:**
- Immutable artifacts and signed builds.
- IaC as source of truth with drift detection.
- Policy checks and security gates in CI/CD.
- Controlled promotion from dev -> stage -> prod.
- Generate auditable deployment evidence automatically.

---

## Deep-Dive Scenario Questions + Model Answers

### Scenario 1: Notifications delayed 30-60 seconds at peak
**Model answer:**
- Validate whether lag is in broker, consumer, gateway sink, or websocket delivery.
- Check queue depth, consumer throughput, prefetch/concurrency.
- Measure Redis publish/subscribe latency and connection churn.
- Tune hot path and scale constrained components.
- Add capacity alarms and load tests to prevent recurrence.

### Scenario 2: GraphQL schema change causes intermittent breakage
**Model answer:**
- Roll back breaking schema behavior quickly.
- Add backward-compatible field/version path.
- Validate with consumer contract tests before re-release.
- Introduce schema governance and deprecation windows.
- Communicate impact and migration path across teams.

### Scenario 3: Over-permissive IAM and secret sprawl found in audit
**Model answer:**
- Inventory all roles/policies/secrets and remove wildcards.
- Migrate workloads to role-based identity.
- Enforce rotation and ownership tagging.
- Add policy-as-code checks in CI.
- Track remediation with time-bound risk register.

### Scenario 4: 40% cost spike after scaling events
**Model answer:**
- Attribute cost by service/resource and workload pattern.
- Right-size compute and optimize autoscaling thresholds.
- Reduce inefficient inter-service traffic and hot queries.
- Improve cache effectiveness where safe.
- Set budget alerts and architecture review guardrails.

### Scenario 5: Need to support 10x traffic in 6 months
**Model answer:**
- Build quarter-wise plan for bottleneck removal and scale testing.
- Define SLOs and capacity targets per domain.
- Invest in platform reliability and observability first.
- Align team ownership to high-impact services.
- Run regular game days and readiness checkpoints.

---

## 60-Minute Panel Scripts

### A) Staff Loop (60 min)
- 0-5 min: Intro and role framing
- 5-15 min: Frontend Q1 + API Gateway Q3
- 15-30 min: Microservices Q2 + Q4
- 30-40 min: Database Q2 + Q5
- 40-52 min: Deployment Q3 + Q4
- 52-58 min: Scenario 1 deep-dive
- 58-60 min: Candidate questions and close

**Staff decision signal:** Strong execution depth, pragmatic trade-offs, and production reliability ownership across domains.

### B) Principal Loop (60 min)
- 0-5 min: Charter and scope framing
- 5-15 min: Frontend Q1 (org standardization probe) + Gateway Q5
- 15-30 min: Microservices Q1 + Q3 (platform strategy probes)
- 30-40 min: Database Q1 + Q3 (portfolio data strategy)
- 40-52 min: Deployment Q1 + Q5
- 52-58 min: Scenario 5 (10x growth strategy)
- 58-60 min: Candidate questions and close

**Principal decision signal:** Org-level technical direction, governance design, and cross-team execution model.

---

## 90-Minute Panel Scripts

### A) Staff Loop (90 min)
- 0-10 min: Intro + architecture context
- 10-25 min: Frontend Q2/Q3 + Gateway Q2
- 25-45 min: Microservices Q2/Q3/Q4
- 45-60 min: Database Q2/Q3/Q4
- 60-75 min: Deployment Q2/Q3/Q4
- 75-85 min: Scenario 1 or 2 incident drill
- 85-90 min: Candidate Q&A and close

**Staff decision signal:** Can lead high-complexity implementation and operational response across 1-2 teams.

### B) Principal Loop (90 min)
- 0-10 min: Intro + strategic charter exercise
- 10-25 min: Gateway Q5 + Microservices Q1
- 25-40 min: Deployment Q4 + Scenario 5
- 40-55 min: Security/governance: Gateway Q1 + Deployment Q2 + Scenario 3
- 55-70 min: Data/platform evolution: Database Q1 + Q5
- 70-82 min: Cross-team influence exercise (execution across 4-6 teams)
- 82-90 min: Candidate Q&A and close

**Principal decision signal:** Defines durable architecture and operating model; drives multi-team alignment and outcomes.

---

## Interviewer Scorecard Template

- Architecture quality (`1-4`):
- Trade-off quality (`1-4`):
- Reliability/failure handling (`1-4`):
- Security posture (`1-4`):
- Observability/SLO fluency (`1-4`):
- Execution realism (`1-4`):
- Leadership/influence (`1-4`):
- Final recommendation: `No Hire` / `Leaning No` / `Leaning Yes` / `Strong Yes`

## Notes for Interviewers

- Keep questions anchored in candidate-owned examples first, then move to hypotheticals.
- Ask for constraints before allowing solutions.
- Probe for rollback, incident handling, and non-happy-path behavior.
- Favor evidence of impact over tool-name listing.


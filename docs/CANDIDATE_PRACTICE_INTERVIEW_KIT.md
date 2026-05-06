# Candidate Practice Kit - Staff/Principal (Smart Hiring Assistant)

This is the candidate practice version of the interview prep kit.
It is designed for self-study and mock practice, with domain-ordered questions and strong sample answers.

## How To Use This Document

- Practice in this order: Frontend -> API Gateway -> Microservices -> Database -> Deployment.
- For each question, speak your answer in 3-5 minutes before reading the sample answer.
- After each domain, write one real project example (from this repo) that proves your answer.
- Run at least one timed mock using the 60-minute or 90-minute practice scripts at the end.

## Level Expectations

### Staff-level signal

- Solves ambiguous system problems across multiple components.
- Makes practical trade-offs and explains production failure handling.
- Demonstrates delivery ownership across 1-2 teams.

### Principal-level signal

- Defines architecture and operating model across multiple teams.
- Creates standards and governance, not just point solutions.
- Connects technical decisions to org outcomes, reliability, and cost.

---

## 1) Frontend Practice (React + TS + Apollo + Redux + Realtime)

### Q1. How would you design state boundaries for local UI state, server state, and realtime updates?
**Strong sample answer:**
I separate state by responsibility: component state for view-only concerns, Redux for cross-screen workflow state, and Apollo cache for server entities. Subscription updates should write into Apollo first, because it already normalizes entity data and avoids duplicate stores. Redux should only track business process state that is not naturally represented as GraphQL entities. I enforce ownership rules so each data type has one source of truth. This avoids drift and race conditions when realtime updates arrive during local edits.

### Q2. What is your token lifecycle strategy for SPA auth with GraphQL/REST?
**Strong sample answer:**
I use short-lived access tokens and rotating refresh tokens. The client has a refresh mutex so parallel 401s do not trigger token refresh storms. Failed requests are queued and replayed after a successful refresh. If refresh fails or token validation is suspicious, I fail closed and require sign-in again. I also emit telemetry for refresh failure rates to detect auth instability and possible abuse patterns.

### Q3. How do you avoid missed or duplicate realtime notifications in UI?
**Strong sample answer:**
I assume at-least-once delivery, so deduplication is mandatory. Each event carries a deterministic event ID and timestamp, and reducers are idempotent. On websocket reconnect, I attempt catch-up using last-seen cursor or timestamp. For long disconnects, I reconcile with a fallback query/poll endpoint. This gives practical correctness even when broker or network behavior is imperfect.

### Q4. How do you keep large realtime lists performant?
**Strong sample answer:**
I use list virtualization, memoized selectors, and batched update processing to reduce rerenders. I split bundles by route and lazy-load heavy features. I also cap update frequency in extreme event bursts to protect interaction responsiveness. In production I watch p95 UI interaction latency and Core Web Vitals, then prioritize fixes using real user impact rather than synthetic microbenchmarks.

### Q5. What frontend testing strategy is credible for Staff/Principal interviews?
**Strong sample answer:**
I keep a layered strategy: unit tests for reducers/hooks/utilities, integration tests for auth + GraphQL flow, contract tests for operation/schema compatibility, and a minimal but stable set of E2E golden paths. The goal is confidence with fast feedback, not maximum test count. I also track flaky tests as reliability debt and gate releases when instability crosses thresholds.

---

## 2) API Gateway Practice (GraphQL + Subscriptions + Security + Realtime Backplane)

### Q1. How do you enforce user/tenant isolation in GraphQL gateway flows?
**Strong sample answer:**
Authentication is validated at request/subscription entry and authorization is enforced in resolver logic with deny-by-default behavior. I never trust user identifiers from request payloads for ownership checks; I derive identity from verified token claims. For subscriptions, the requested channel key must match claims-based identity. I back this with negative tests (spoofing/tampering) and policy regression tests in CI.

### Q2. What are key failure modes in Redis backplane + RabbitMQ event ingestion?
**Strong sample answer:**
I expect duplicates, delays, out-of-order delivery, and occasional drops during incidents. So the gateway and clients must handle idempotency and ordering best-effort. I add retry and DLQ policies for poison messages and monitor queue lag, listener failures, and sink emit errors. Reliability is managed with explicit SLOs and alert thresholds, not assumptions about perfect delivery.

### Q3. How would you debug "subscriptions connected but no events delivered"?
**Strong sample answer:**
I trace the full path: producer event -> broker -> gateway consumer -> sink emit -> websocket push -> frontend handler. I attach correlation IDs and inspect lag/throughput at each stage. I validate routing keys, auth checks, and subscription keys for mismatches. Then I confirm backpressure behavior and dropped emit metrics. I keep a runbook so on-call response is deterministic.

### Q4. How do you evolve a single-team gateway into a platform capability?
**Strong sample answer:**
I introduce schema governance, backward compatibility standards, and clear ownership by domain. Teams can evolve independently but within contract and deprecation policies. I require consumer contract tests and canary rollout for risky changes. This shifts the gateway from ad hoc integration to a managed product with predictable evolution.

### Q5. How do you secure subscription auth flows specifically?
**Strong sample answer:**
I validate signature, issuer, audience, expiry, and required claims server-side. I reject weak or missing claims and avoid parsing unsigned payload data for authorization decisions. I protect against replay by combining short token TTL and strict refresh controls. Finally, I audit failed subscription auth attempts and anomalies to catch attacks early.

---

## 3) Microservices Practice (Spring Boot + Events + Contracts)

### Q1. How do you split services to avoid chatty architecture?
**Strong sample answer:**
I align services with bounded contexts and business ownership. User-critical read/write paths stay synchronous only where necessary; secondary workflow steps move to asynchronous events. I design coarse-grained APIs and avoid deep synchronous call chains that multiply latency and failure risk. I regularly review coupling with service dependency maps.

### Q2. How do you implement idempotent event consumers?
**Strong sample answer:**
Every event must include a stable idempotency key. Consumers persist processed keys and guard side effects with upserts or state checks. Retries are safe by design, and poison messages are routed to DLQ with observability. I treat replay as normal operations capability, not emergency-only behavior.

### Q3. When should a workflow step be sync vs async?
**Strong sample answer:**
Sync is for immediate UX/business response dependencies; async is for decoupling, smoothing bursts, and independent scaling. I decide using latency tolerance, consistency requirements, and blast radius. For async paths I define compensation and timeout behavior explicitly. I document consistency expectations so product and engineering align.

### Q4. What RabbitMQ reliability controls would you describe in an interview?
**Strong sample answer:**
Durable queues/exchanges, explicit acknowledgments, controlled prefetch, and consumer concurrency tuning. Retry queues with exponential backoff and DLQ for non-recoverable failures. Queue lag and consumer health drive autoscaling and alerts. I include failure testing (broker restart/network partition) to validate real resilience.

### Q5. How do you secure service-to-service traffic and secrets?
**Strong sample answer:**
I use workload identity and least-privilege access, remove static long-lived credentials, and enforce network boundaries by default. Secrets are centrally managed and rotated. I verify artifact provenance in CI/CD. Security controls are policy-as-code so they scale with teams.

---

## 4) Database Practice (PostgreSQL + Redis + Data Consistency)

### Q1. How do you choose between PostgreSQL, Redis, and document stores?
**Strong sample answer:**
PostgreSQL for transactional consistency and relational query needs; Redis for cache/pub-sub/ephemeral low-latency data; document store for flexible high-variance payloads. The decision is driven by access patterns, consistency, and lifecycle constraints. I revisit decisions as usage evolves rather than locking in early assumptions.

### Q2. How do you design schema and indexes for hiring workflows?
**Strong sample answer:**
I start from production query patterns, then add targeted indexes for common filters/sorts. I avoid over-indexing write-heavy tables and validate gains using query metrics. I include migration plans that keep old and new access paths functional during rollout. Index decisions must be tied to measurable latency or throughput improvement.

### Q3. How do you protect against concurrent event-driven write conflicts?
**Strong sample answer:**
I use optimistic locking/version checks where collisions are likely. Writes are idempotent with deterministic merge behavior. Critical multi-step changes run in transactions. I add reconciliation jobs and replay support for eventual consistency repair after incidents.

### Q4. What Redis operational topics should candidates be ready to discuss?
**Strong sample answer:**
Connection pool sizing, timeouts, memory/eviction policies, hot key behavior, replication/failover, and pub/sub delivery lag. I also discuss monitoring for reconnect churn and capacity limits under burst traffic. Operational awareness is as important as API usage.

### Q5. How do you perform low-downtime schema migration?
**Strong sample answer:**
I use expand-contract: add new schema elements first, deploy backward-compatible code, backfill asynchronously, then remove legacy paths only after safe cutover. I keep observability around migration progress and maintain rollback options. This avoids brittle big-bang migrations.

---

## 5) Deployment Strategy Practice (K8s/OpenShift + AWS Patterns + CI/CD)

### Q1. How do you run container and serverless components in one platform?
**Strong sample answer:**
I use Kubernetes for steady-state services and serverless for bursty/stateless workloads. I standardize contracts, observability, and security controls regardless of runtime. I define clear ownership and release patterns by workload type. Runtime choice is guided by latency, cost, and operational complexity.

### Q2. How do you manage configuration and secrets safely across environments?
**Strong sample answer:**
Non-secret config is externalized via ConfigMaps (or equivalent), secrets come from centralized secret management with rotation, and workloads use role-based identity rather than static credentials. I enforce least privilege and audit access continuously. Environment promotion includes secret and policy validation gates.

### Q3. What rollout strategy would you use for critical services?
**Strong sample answer:**
Canary or blue/green with health and SLO gates. I include contract checks before production rollout and auto-rollback on burn-rate/error threshold violations. Traffic is shifted in stages to limit blast radius. Post-release verification is scripted, not manual guesswork.

### Q4. Which SLOs/SLIs matter most here?
**Strong sample answer:**
Availability, p95/p99 latency, error rate, queue lag, subscription success rate, deployment success, and MTTR. Error budgets determine release velocity and prioritization. I use SLOs to drive engineering behavior, not just dashboards.

### Q5. How do you handle compliance and promotion controls?
**Strong sample answer:**
Use immutable artifacts, signed builds, IaC with drift detection, and policy checks in CI/CD. Promotion from dev to stage to prod must be controlled and auditable. Compliance evidence should be generated automatically from pipeline and runtime metadata.

---

## Scenario Practice (High-Value Staff/Principal Prompts)

### Scenario 1: Realtime notifications delayed by 30-60s
**Strong sample answer (framework):**
1. Localize bottleneck (broker lag vs gateway sink vs websocket).
2. Validate queue depth, consumer throughput, prefetch/concurrency.
3. Check Redis latency and connection churn.
4. Scale/tune constrained layers.
5. Add alarms, load tests, and capacity thresholds to prevent recurrence.

### Scenario 2: GraphQL change causes intermittent client breakage
**Strong sample answer (framework):**
1. Mitigate quickly with rollback or compatibility patch.
2. Restore backward compatibility.
3. Add/expand contract tests.
4. Apply schema governance and deprecation policy.
5. Communicate timeline and migration across teams.

### Scenario 3: Audit finds over-permissive IAM and secret sprawl
**Strong sample answer (framework):**
1. Inventory and classify risk.
2. Remove wildcards and tighten scopes.
3. Migrate to role-based workload identity.
4. Enforce rotation and ownership standards.
5. Add policy-as-code and compliance dashboards.

### Scenario 4: 40% cost spike after scaling event
**Strong sample answer (framework):**
1. Attribute cost by workload/resource.
2. Right-size compute and autoscaling.
3. Reduce inefficient network/query patterns.
4. Increase cache efficiency where correct.
5. Add budget alerts and architectural guardrails.

### Scenario 5: Need 10x scale in 6 months
**Strong sample answer (framework):**
1. Capacity model and bottleneck ranking.
2. Quarter-by-quarter roadmap.
3. Reliability and observability investments first.
4. Ownership alignment for top-risk domains.
5. Game days and readiness checkpoints.

---

## Timed Candidate Practice Scripts

### 60-Minute Staff Practice
- 0-5 min: Context and constraints
- 5-15 min: Frontend Q1 + Gateway Q3
- 15-30 min: Microservices Q2 + Q4
- 30-40 min: Database Q2 + Q5
- 40-52 min: Deployment Q3 + Q4
- 52-58 min: Scenario 1
- 58-60 min: Self-review notes

### 60-Minute Principal Practice
- 0-5 min: Strategic framing
- 5-15 min: Frontend Q1 + Gateway Q4
- 15-30 min: Microservices Q1 + Q3
- 30-40 min: Database Q1 + Q3
- 40-52 min: Deployment Q1 + Q5
- 52-58 min: Scenario 5
- 58-60 min: Self-review notes

### 90-Minute Staff Practice
- 0-10 min: Architecture walkthrough
- 10-25 min: Frontend Q2/Q3 + Gateway Q2
- 25-45 min: Microservices Q2/Q3/Q4
- 45-60 min: Database Q2/Q3/Q4
- 60-75 min: Deployment Q2/Q3/Q4
- 75-85 min: Scenario 1 or 2
- 85-90 min: Self-review notes

### 90-Minute Principal Practice
- 0-10 min: Strategic architecture framing
- 10-25 min: Gateway Q4 + Microservices Q1
- 25-40 min: Deployment Q4 + Scenario 5
- 40-55 min: Security/governance (Gateway Q1 + Deployment Q2 + Scenario 3)
- 55-70 min: Data/platform strategy (Database Q1 + Q5)
- 70-82 min: Multi-team execution plan exercise
- 82-90 min: Self-review notes

---

## Candidate Self-Evaluation Rubric

Score each area from `1` to `4`:

- Architecture depth
- Trade-off clarity
- Reliability/failure-mode handling
- Security posture
- Observability and SLO fluency
- Cost and scalability judgment
- Communication and leadership signal

### Interpretation

- `<= 16`: Need stronger fundamentals and concrete examples.
- `17-22`: Good mid-senior baseline; deepen cross-domain reasoning.
- `23-26`: Strong Staff-level readiness.
- `27+`: Strong Principal-level readiness if cross-team strategy is clear.


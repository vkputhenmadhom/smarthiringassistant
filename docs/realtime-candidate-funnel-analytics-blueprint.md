# Real-time Candidate Funnel Analytics Blueprint

This blueprint turns the phased analytics plan into concrete repo modules for SmartHiringAssistant.

## Implementation order

1. canonical analytics events
2. Kafka bridge
3. Flink live metrics
4. analytics query service
5. HR dashboard integration
6. anomaly detection
7. Spark history
8. Beam shared transforms

## Added modules

- `shared/analytics-events`
- `services/analytics-bridge-service`
- `services/analytics-query-service`
- `streaming/flink-funnel-job`
- `batch/spark-hiring-analytics`
- `pipelines/beam-shared-transforms`

## Kafka topics

- `sha.analytics.raw.v1`
- `sha.analytics.screening.v1`
- `sha.analytics.funnel.v1`
- `sha.analytics.alerts.v1`
- `sha.analytics.dead-letter.v1`

## Bridge inputs

Initial RabbitMQ source queues:

- `candidate.matched.queue`
- `screening.completed.queue`

Near-term additions:

- `user.registered.queue`
- `user.authenticated.queue`
- `resume.parsed.queue`

## First Flink job

Current implementation is a bootstrap consumer that:

- reads canonical events from Kafka
- deserializes them
- prints normalized event summaries

This is the foundation for:

- live funnel counts
- screening intelligence
- anomaly detection
- Redis/PostgreSQL sinks

## First source-service mapping

| Service | Event types |
| --- | --- |
| `auth-service` | `candidate.registered`, `candidate.login` |
| `resume-parser-service` | `resume.uploaded`, `resume.parsed`, `resume.parse.failed` |
| `job-analyzer-service` | `job.published` |
| `candidate-matcher-service` | `candidate.matched` |
| `screening-bot-service` | `screening.started`, `screening.stage.completed`, `screening.completed` |
| `notification-service` | `notification.sent` |

## First code delivered

This commit adds:

- canonical analytics event DTOs
- topic constants
- event schema + sample payloads
- analytics bridge service skeleton
- RabbitMQ-to-Kafka mapping for:
  - candidate matched
  - screening completed
- first Flink module bootstrap
- query/spark/beam module placeholders

## Recommended next code step

Implement:

1. Kafka topic provisioning
2. `candidate.registered` and `resume.parsed` bridge mappings
3. first windowed Flink aggregates
4. gateway + HR dashboard live funnel query path

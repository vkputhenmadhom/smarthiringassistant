# Analytics Event Types

This folder defines the canonical analytics event envelope used by the analytics bridge,
Flink live-metrics pipeline, and future Spark/Beam jobs.

## Topics

- `sha.analytics.raw.v1`
- `sha.analytics.screening.v1`
- `sha.analytics.funnel.v1`
- `sha.analytics.alerts.v1`
- `sha.analytics.dead-letter.v1`

## Initial event types

- `candidate.registered`
- `candidate.login`
- `resume.uploaded`
- `resume.parsed`
- `resume.parse.failed`
- `job.published`
- `candidate.matched`
- `screening.started`
- `screening.stage.completed`
- `screening.completed`
- `interview.scheduled`
- `notification.sent`

## Source service mapping

| Service | Canonical events |
| --- | --- |
| `auth-service` | `candidate.registered`, `candidate.login` |
| `resume-parser-service` | `resume.uploaded`, `resume.parsed`, `resume.parse.failed` |
| `job-analyzer-service` | `job.published` |
| `candidate-matcher-service` | `candidate.matched` |
| `screening-bot-service` | `screening.started`, `screening.stage.completed`, `screening.completed` |
| `notification-service` | `notification.sent` |

## First Flink job outputs

- live funnel counts
- live stage pass/fail counts
- stage average score
- stage average duration
- rolling funnel conversions
- stuck screening session alerts

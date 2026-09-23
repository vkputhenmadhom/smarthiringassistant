# First Flink Job Design

This module is the first live analytics job for SmartHiringAssistant.

## Phase 1 scope

- consume canonical analytics events from `sha.analytics.raw.v1`
- validate event deserialization
- establish Flink runtime wiring
- prepare for keyed/windowed metrics

## Next implementation steps

1. key by `candidateId` for journey reconstruction
2. key by `jobId` for job-level funnel metrics
3. key by `stage` for stage performance metrics
4. sink live aggregates to Redis/PostgreSQL

## First derived metrics planned

- registrations
- resumes parsed
- candidate matches
- screenings started
- screenings completed
- stage pass/fail counts
- average final score

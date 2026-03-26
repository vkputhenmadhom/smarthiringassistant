# Phase 3 Demo Pack (Job Analyzer + Screening Bot)

This folder contains:
- `SmartHiringAssistant-Phase3.postman_collection.json`
- `curl-demo.sh`

## Run services

```bash
./gradlew :services:job-analyzer-service:bootRun
./gradlew :services:screening-bot-service:bootRun
```

## Run demo script

```bash
bash docs/demo/phase3/curl-demo.sh
```

## Dashboard-ready metrics

Prometheus scrape targets:
- `http://localhost:8005/actuator/prometheus`
- `http://localhost:8006/actuator/prometheus`

Recommended Grafana queries:

- Stage pass rate (per stage):
```promql
sum(rate(screening_stage_pass_total[5m])) by (stage)
/
(
  sum(rate(screening_stage_pass_total[5m])) by (stage)
  +
  sum(rate(screening_stage_fail_total[5m])) by (stage)
)
```

- Average screening score:
```promql
rate(screening_final_score_sum[5m]) / rate(screening_final_score_count[5m])
```

- Average salary confidence:
```promql
rate(job_analyzer_salary_confidence_sum[5m]) / rate(job_analyzer_salary_confidence_count[5m])
```

- Analyze request throughput:
```promql
sum(rate(job_analyzer_analyze_requests_total[5m]))
```

- Screening completion split:
```promql
sum(rate(screening_sessions_completed_total[5m])) by (decision)
```


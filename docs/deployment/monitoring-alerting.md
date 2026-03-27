# Monitoring and Alerting (Prometheus + Grafana)

This project includes a production-oriented local monitoring baseline:

- Prometheus scrape jobs for backend services
- Prometheus alert rules for availability, latency, errors, and JVM heap pressure
- Alertmanager for routing alerts
- Grafana auto-provisioned datasource and dashboards

## Files

- `infrastructure/monitoring/prometheus.yml`
- `infrastructure/monitoring/alert-rules.yml`
- `infrastructure/monitoring/alertmanager.yml`
- `infrastructure/monitoring/grafana/provisioning/datasources/prometheus.yaml`
- `infrastructure/monitoring/grafana/provisioning/dashboards/dashboards.yaml`
- `infrastructure/monitoring/grafana/dashboards/platform-overview.json`
- `infrastructure/monitoring/grafana/dashboards/alerts-health.json`

## Start stack

Production-threshold mode (default monitoring config):

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant
docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build
```

Staging-threshold mode (staging monitoring override):

```bash
cd /Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.monitoring-staging.yml up -d --build
```

## Access UIs

- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3000` (default: `admin` / `hiring_password`)

## Verify metrics and alerts

```bash
# Check Prometheus targets and active alerts
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {scrapeUrl: .scrapeUrl, health: .health}'
curl -s http://localhost:9090/api/v1/alerts | jq '.data.alerts[] | {name: .labels.alertname, state: .state, job: .labels.job}'
```

## Alerts included

- **Prod thresholds** (tighter)
  - `ServiceDown`
  - `HighErrorRateProd`
  - `HighLatencyP95Prod`
  - `JVMHeapPressureProd`
- **Staging thresholds** (looser)
  - `ServiceDownStaging`
  - `HighErrorRateStaging`
  - `HighLatencyP95Staging`
  - `JVMHeapPressureStaging`

Prometheus target labels include `environment` (currently set to `prod` in `prometheus.yml`).
If you run a staging stack with the same config, switch target labels to `staging` so staging thresholds/receivers are used.

This repo provides `infrastructure/monitoring/prometheus.staging.yml` and `docker-compose.monitoring-staging.yml`
to activate staging labels/thresholds without editing the default `prometheus.yml`.

## Grafana dashboards included

- `Smart Hiring - Platform Overview`
- `Smart Hiring - Alerts & Health`

## Production notes

- Alertmanager routes are environment-aware:
  - `environment=prod,severity=critical` -> PagerDuty + Slack
  - `environment=prod` -> Slack (`#smart-hiring-prod-alerts`)
  - `environment=staging` -> Slack (`#smart-hiring-staging-alerts`)
- Replace placeholders in `infrastructure/monitoring/alertmanager.yml`:
  - `global.slack_api_url`
  - `pagerduty_configs[].routing_key`
- Ensure backend services expose `/actuator/prometheus` (or context-path prefixed equivalent).
- For Kubernetes, prefer ServiceMonitor resources and cluster alert routing in your Prometheus Operator setup.


#!/usr/bin/env bash
set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8000}"
AUTH_BASE_URL="${AUTH_BASE_URL:-$GATEWAY_BASE_URL/api/auth}"
GRAPHQL_URL="${GRAPHQL_URL:-$GATEWAY_BASE_URL/graphql}"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

register_json="$work_dir/register.json"
register_resp="$work_dir/register_resp.json"
create_job_json="$work_dir/create_job.json"
create_job_resp="$work_dir/create_job_resp.json"
publish_job_json="$work_dir/publish_job.json"
publish_job_resp="$work_dir/publish_job_resp.json"
job_query_json="$work_dir/job_query.json"
job_query_resp="$work_dir/job_query_resp.json"
jobs_query_json="$work_dir/jobs_query.json"
jobs_query_resp="$work_dir/jobs_query_resp.json"
dashboard_query_json="$work_dir/dashboard_query.json"
dashboard_query_resp="$work_dir/dashboard_query_resp.json"

stamp="$(date +%s)"
username="hrgraphql${stamp}"
password="SmokePass123!"
email="${username}@example.com"
job_title="Smoke HR GraphQL Job ${stamp}"

cat > "$register_json" <<JSON
{"username":"$username","email":"$email","password":"$password","confirmPassword":"$password","firstName":"Smoke","lastName":"HR","role":"RECRUITER"}
JSON

echo "[1/6] Registering recruiter smoke user: $username"
register_code="$(curl -s -o "$register_resp" -w "%{http_code}" -H "Content-Type: application/json" --data-binary @"$register_json" "$AUTH_BASE_URL/register")"
echo "register_code=$register_code"
if [[ "$register_code" != "200" && "$register_code" != "201" ]]; then
  echo "register_response=$(cat "$register_resp")"
  exit 1
fi

token="$(python3 - <<PY
import json
payload = json.load(open("$register_resp"))
print(payload.get("accessToken", ""))
PY
)"
if [[ -z "$token" ]]; then
  echo "Missing accessToken in register response"
  cat "$register_resp"
  exit 1
fi

cat > "$create_job_json" <<JSON
{"query":"mutation CreateJob(\$input: CreateJobInput!){ createJob(input: \$input) { id title status type skills salaryCurrency } }","variables":{"input":{"title":"$job_title","description":"Build reliable Java, Spring Boot, and Kubernetes services.","department":"Engineering","location":"Remote","type":"FULL_TIME","skills":["Java","Spring Boot","Kubernetes"],"salaryMin":120000,"salaryMax":150000,"salaryCurrency":"USD"}}}
JSON

echo "[2/6] Creating job through GraphQL"
create_code="$(curl -s -o "$create_job_resp" -w "%{http_code}" -H "Content-Type: application/json" -H "Authorization: Bearer $token" --data-binary @"$create_job_json" "$GRAPHQL_URL")"
echo "create_job_code=$create_code"
if [[ "$create_code" != "200" ]]; then
  echo "create_job_response=$(cat "$create_job_resp")"
  exit 1
fi

job_id="$(python3 - <<PY
import json, sys
payload = json.load(open("$create_job_resp"))
if payload.get("errors"):
    print("GRAPHQL_ERRORS", file=sys.stderr)
    print(json.dumps(payload["errors"]), file=sys.stderr)
    sys.exit(1)
print(payload.get("data", {}).get("createJob", {}).get("id", ""))
PY
)"
if [[ -z "$job_id" ]]; then
  echo "Missing job id in createJob response"
  cat "$create_job_resp"
  exit 1
fi
echo "job_id=$job_id"

cat > "$publish_job_json" <<JSON
{"query":"mutation PublishJob(\$id: ID!){ publishJob(id: \$id) { id status postedAt } }","variables":{"id":"$job_id"}}
JSON

echo "[3/6] Publishing job"
publish_code="$(curl -s -o "$publish_job_resp" -w "%{http_code}" -H "Content-Type: application/json" -H "Authorization: Bearer $token" --data-binary @"$publish_job_json" "$GRAPHQL_URL")"
echo "publish_job_code=$publish_code"
if [[ "$publish_code" != "200" ]]; then
  echo "publish_job_response=$(cat "$publish_job_resp")"
  exit 1
fi

cat > "$job_query_json" <<JSON
{"query":"query Job(\$id: ID!){ job(id: \$id) { id title status type skills salaryCurrency postedAt } }","variables":{"id":"$job_id"}}
JSON

echo "[4/6] Querying created job"
job_query_code="$(curl -s -o "$job_query_resp" -w "%{http_code}" -H "Content-Type: application/json" -H "Authorization: Bearer $token" --data-binary @"$job_query_json" "$GRAPHQL_URL")"
echo "job_query_code=$job_query_code"
if [[ "$job_query_code" != "200" ]]; then
  echo "job_query_response=$(cat "$job_query_resp")"
  exit 1
fi

cat > "$jobs_query_json" <<JSON
{"query":"query Jobs { jobs(page:0,size:20){ totalElements content { id title status type skills salaryCurrency } } }"}
JSON

echo "[5/6] Querying jobs page"
jobs_query_code="$(curl -s -o "$jobs_query_resp" -w "%{http_code}" -H "Content-Type: application/json" -H "Authorization: Bearer $token" --data-binary @"$jobs_query_json" "$GRAPHQL_URL")"
echo "jobs_query_code=$jobs_query_code"
if [[ "$jobs_query_code" != "200" ]]; then
  echo "jobs_query_response=$(cat "$jobs_query_resp")"
  exit 1
fi

cat > "$dashboard_query_json" <<JSON
{"query":"query DashboardMetrics { dashboardMetrics { totalJobs openJobs topSkillsInDemand { skill count } recentActivity { type description timestamp } } }"}
JSON

echo "[6/6] Querying dashboard metrics"
dashboard_code="$(curl -s -o "$dashboard_query_resp" -w "%{http_code}" -H "Content-Type: application/json" -H "Authorization: Bearer $token" --data-binary @"$dashboard_query_json" "$GRAPHQL_URL")"
echo "dashboard_code=$dashboard_code"
if [[ "$dashboard_code" != "200" ]]; then
  echo "dashboard_response=$(cat "$dashboard_query_resp")"
  exit 1
fi

python3 - <<PY
import json
import sys

job_title = "$job_title"
job_id = "$job_id"

create_payload = json.load(open("$create_job_resp"))
publish_payload = json.load(open("$publish_job_resp"))
job_payload = json.load(open("$job_query_resp"))
jobs_payload = json.load(open("$jobs_query_resp"))
dashboard_payload = json.load(open("$dashboard_query_resp"))

for name, payload in [
    ("createJob", create_payload),
    ("publishJob", publish_payload),
    ("job", job_payload),
    ("jobs", jobs_payload),
    ("dashboardMetrics", dashboard_payload),
]:
    if payload.get("errors"):
        print(f"{name} GraphQL errors: {json.dumps(payload['errors'])}", file=sys.stderr)
        sys.exit(1)

job = job_payload["data"]["job"]
if job["id"] != job_id or job["title"] != job_title:
    print(f"Unexpected job payload: {json.dumps(job)}", file=sys.stderr)
    sys.exit(1)
if job["status"] != "OPEN":
    print(f"Expected OPEN job status, got {job['status']}", file=sys.stderr)
    sys.exit(1)
if not job.get("postedAt"):
    print("Expected postedAt after publishJob", file=sys.stderr)
    sys.exit(1)

jobs = jobs_payload["data"]["jobs"]["content"]
matching_jobs = [item for item in jobs if item.get("id") == job_id]
if not matching_jobs:
    print(f"Created job {job_id} not found in jobs query", file=sys.stderr)
    sys.exit(1)

metrics = dashboard_payload["data"]["dashboardMetrics"]
if metrics["totalJobs"] < 1 or metrics["openJobs"] < 1:
    print(f"Unexpected dashboard job counters: {json.dumps(metrics)}", file=sys.stderr)
    sys.exit(1)
if not any(activity.get("description", "").find(job_title) >= 0 for activity in metrics.get("recentActivity", [])):
    print(f"Expected recentActivity to mention created job: {json.dumps(metrics.get('recentActivity', []))}", file=sys.stderr)
    sys.exit(1)

print("Smoke test passed")
print(f"created_job_id={job_id}")
print(f"created_job_title={job_title}")
print(f"dashboard_totalJobs={metrics['totalJobs']}")
print(f"dashboard_openJobs={metrics['openJobs']}")
PY


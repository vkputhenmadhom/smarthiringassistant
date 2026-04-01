#!/usr/bin/env bash
set -euo pipefail

AUTH_BASE_URL="${AUTH_BASE_URL:-http://localhost:8000/api/auth}"
SCREENING_BASE_URL="${SCREENING_BASE_URL:-http://localhost:8006/api/screening}"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

register_recruiter_json="$work_dir/register_recruiter.json"
register_job_seeker_json="$work_dir/register_job_seeker.json"
register_recruiter_resp="$work_dir/register_recruiter_resp.json"
register_job_seeker_resp="$work_dir/register_job_seeker_resp.json"
unauth_resp="$work_dir/unauth_resp.txt"
recruiter_my_resp="$work_dir/recruiter_my_resp.txt"
job_seeker_my_resp="$work_dir/job_seeker_my_resp.json"

stamp="$(date +%s)"
recruiter_username="scope_rec_${stamp}"
job_seeker_username="scope_js_${stamp}"
password="SmokePass123!"
foreign_candidate_id="9${stamp: -6}"
own_a="scope-own-a-${stamp}"
own_b="scope-own-b-${stamp}"
foreign_job="scope-foreign-${stamp}"

cat > "$register_recruiter_json" <<JSON
{"username":"$recruiter_username","email":"${recruiter_username}@example.com","password":"$password","confirmPassword":"$password","firstName":"Scope","lastName":"Recruiter","role":"RECRUITER"}
JSON

cat > "$register_job_seeker_json" <<JSON
{"username":"$job_seeker_username","email":"${job_seeker_username}@example.com","password":"$password","confirmPassword":"$password","firstName":"Scope","lastName":"JobSeeker","role":"JOB_SEEKER"}
JSON

echo "[1/5] Registering recruiter and job seeker"
recruiter_register_code="$(curl -s -o "$register_recruiter_resp" -w "%{http_code}" -H "Content-Type: application/json" --data-binary @"$register_recruiter_json" "$AUTH_BASE_URL/register")"
job_seeker_register_code="$(curl -s -o "$register_job_seeker_resp" -w "%{http_code}" -H "Content-Type: application/json" --data-binary @"$register_job_seeker_json" "$AUTH_BASE_URL/register")"
echo "recruiter_register_code=$recruiter_register_code"
echo "job_seeker_register_code=$job_seeker_register_code"
if [[ "$recruiter_register_code" != "200" && "$recruiter_register_code" != "201" ]]; then
  echo "recruiter_register_response=$(cat "$register_recruiter_resp")"
  exit 1
fi
if [[ "$job_seeker_register_code" != "200" && "$job_seeker_register_code" != "201" ]]; then
  echo "job_seeker_register_response=$(cat "$register_job_seeker_resp")"
  exit 1
fi

read -r recruiter_token job_seeker_token job_seeker_user_id <<EOF
$(python3 - <<PY
import json
rec = json.load(open("$register_recruiter_resp"))
js = json.load(open("$register_job_seeker_resp"))
print(
    rec.get("accessToken", ""),
    js.get("accessToken", ""),
    js.get("user", {}).get("id", ""),
)
PY
)
EOF

if [[ -z "$recruiter_token" || -z "$job_seeker_token" || -z "$job_seeker_user_id" ]]; then
  echo "Missing auth artifacts"
  echo "recruiter_response=$(cat "$register_recruiter_resp")"
  echo "job_seeker_response=$(cat "$register_job_seeker_resp")"
  exit 1
fi

echo "[2/5] Seeding screening sessions"
while read -r job_id candidate_id; do
  seed_code="$(curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" --data "{\"candidateId\":$candidate_id,\"jobId\":\"$job_id\"}" "$SCREENING_BASE_URL/sessions")"
  if [[ "$seed_code" != "200" ]]; then
    echo "Failed to seed session for job_id=$job_id candidate_id=$candidate_id code=$seed_code"
    exit 1
  fi
done <<EOF
$own_a $job_seeker_user_id
$own_b $job_seeker_user_id
$foreign_job $foreign_candidate_id
EOF

echo "[3/5] Verifying unauthenticated /sessions/my is rejected"
unauth_code="$(curl -s -o "$unauth_resp" -w "%{http_code}" "$SCREENING_BASE_URL/sessions/my")"
echo "unauth_code=$unauth_code"
if [[ "$unauth_code" != "401" ]]; then
  echo "unauth_response=$(cat "$unauth_resp")"
  exit 1
fi

echo "[4/5] Verifying recruiter /sessions/my is forbidden"
recruiter_my_code="$(curl -s -o "$recruiter_my_resp" -w "%{http_code}" -H "Authorization: Bearer $recruiter_token" "$SCREENING_BASE_URL/sessions/my")"
echo "recruiter_my_code=$recruiter_my_code"
if [[ "$recruiter_my_code" != "403" ]]; then
  echo "recruiter_my_response=$(cat "$recruiter_my_resp")"
  exit 1
fi

echo "[5/5] Verifying JOB_SEEKER /sessions/my is scoped to own sessions"
job_seeker_my_code="$(curl -s -o "$job_seeker_my_resp" -w "%{http_code}" -H "Authorization: Bearer $job_seeker_token" "$SCREENING_BASE_URL/sessions/my")"
echo "job_seeker_my_code=$job_seeker_my_code"
if [[ "$job_seeker_my_code" != "200" ]]; then
  echo "job_seeker_my_response=$(cat "$job_seeker_my_resp")"
  exit 1
fi

python3 - <<PY
import json
import sys

payload = json.load(open("$job_seeker_my_resp"))
own_a = "$own_a"
own_b = "$own_b"
foreign_job = "$foreign_job"
job_ids = {item.get("jobId") for item in payload}

if own_a not in job_ids:
    print(f"Missing expected own job: {own_a}", file=sys.stderr)
    sys.exit(1)
if own_b not in job_ids:
    print(f"Missing expected own job: {own_b}", file=sys.stderr)
    sys.exit(1)
if foreign_job in job_ids:
    print(f"Foreign job leaked into scoped response: {foreign_job}", file=sys.stderr)
    sys.exit(1)
if len(payload) != 2:
    print(f"Expected exactly 2 scoped sessions, got {len(payload)}: {json.dumps(payload)}", file=sys.stderr)
    sys.exit(1)

print("Screening sessions scope smoke passed")
print(f"job_seeker_session_count={len(payload)}")
print(f"contains_own_a={own_a in job_ids}")
print(f"contains_own_b={own_b in job_ids}")
print(f"contains_foreign={foreign_job in job_ids}")
PY


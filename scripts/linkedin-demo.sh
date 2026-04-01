#!/usr/bin/env bash
set -u

# LinkedIn demo runner for Smart Hiring Assistant.
# Runs an end-to-end API smoke flow and prints a PASS/FAIL summary.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

START_STACK=0
if [[ "${1:-}" == "--start-stack" ]]; then
  START_STACK=1
fi

AUTH_BASE_URL="${AUTH_BASE_URL:-http://localhost:8001/api/auth}"
RESUME_BASE_URL="${RESUME_BASE_URL:-http://localhost:8002/api/resumes}"
MATCH_BASE_URL="${MATCH_BASE_URL:-http://localhost:8003/api/matches}"
SCREEN_BASE_URL="${SCREEN_BASE_URL:-http://localhost:8006/api/screening}"
NOTIFY_BASE_URL="${NOTIFY_BASE_URL:-http://localhost:8007/api/notifications}"

PASS_COUNT=0
FAIL_COUNT=0
RESULTS=()

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  RESULTS+=("PASS | $1")
}

fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  RESULTS+=("FAIL | $1")
}

check_dep() {
  if command -v "$1" >/dev/null 2>&1; then
    pass "Dependency available: $1"
  else
    fail "Dependency missing: $1"
  fi
}

http_code() {
  local method="$1"
  local url="$2"
  local body_file="$3"
  shift 3
  curl -s -o "$body_file" -w "%{http_code}" -X "$method" "$url" "$@"
}

record_http_expect() {
  local name="$1"
  local method="$2"
  local url="$3"
  local expected="$4"
  local body_file="$WORK_DIR/${name// /_}.out"
  local code
  code="$(http_code "$method" "$url" "$body_file" "${@:5}")"
  if [[ "$code" == "$expected" ]]; then
    pass "$name [$code]"
  else
    local body
    body="$(cat "$body_file" 2>/dev/null || true)"
    fail "$name [got $code expected $expected] body=${body:0:180}"
  fi
}

record_http_expect_any() {
  local name="$1"
  local method="$2"
  local url="$3"
  local expected_a="$4"
  local expected_b="$5"
  local body_file="$WORK_DIR/${name// /_}.out"
  local code
  code="$(http_code "$method" "$url" "$body_file" "${@:6}")"
  if [[ "$code" == "$expected_a" || "$code" == "$expected_b" ]]; then
    pass "$name [$code]"
  else
    local body
    body="$(cat "$body_file" 2>/dev/null || true)"
    fail "$name [got $code expected $expected_a/$expected_b] body=${body:0:180}"
  fi
}

echo "Running LinkedIn demo flow from: $REPO_ROOT"

check_dep curl
check_dep python3
check_dep docker

if [[ "$START_STACK" -eq 1 ]]; then
  echo "[setup] Starting full stack (this may take a while)..."
  (cd "$REPO_ROOT" && docker compose -f docker-compose.yml -f docker-compose.apps.yml up -d --build)
fi

echo "[check] Core service health"
record_http_expect_any "auth health" GET "$AUTH_BASE_URL/actuator/health" 200 401
record_http_expect "resume health" GET "$RESUME_BASE_URL/actuator/health" 200
record_http_expect "matcher health" GET "$MATCH_BASE_URL/actuator/health" 200
record_http_expect "screening health" GET "$SCREEN_BASE_URL/actuator/health" 200
record_http_expect "notification health" GET "$NOTIFY_BASE_URL/actuator/health" 200
record_http_expect "candidate portal" GET "http://localhost:5173" 200
record_http_expect "hr dashboard" GET "http://localhost:4200" 200

DEMO_USER="linkedin_demo_$(date +%s)"
DEMO_PASS="DemoPass123!"
DEMO_EMAIL="${DEMO_USER}@example.com"

REGISTER_JSON="$WORK_DIR/register.json"
LOGIN_JSON="$WORK_DIR/login.json"

cat > "$REGISTER_JSON" <<JSON
{"username":"$DEMO_USER","email":"$DEMO_EMAIL","password":"$DEMO_PASS","confirmPassword":"$DEMO_PASS","firstName":"LinkedIn","lastName":"Demo","role":"JOB_SEEKER"}
JSON

cat > "$LOGIN_JSON" <<JSON
{"username":"$DEMO_USER","password":"$DEMO_PASS"}
JSON

echo "[flow] Candidate register/login"
REGISTER_RESP="$WORK_DIR/register_resp.json"
LOGIN_RESP="$WORK_DIR/login_resp.json"
TOKEN_VALIDATE_RESP="$WORK_DIR/token_validate_resp.txt"

REG_CODE="$(http_code POST "$AUTH_BASE_URL/register" "$REGISTER_RESP" -H "Content-Type: application/json" --data-binary @"$REGISTER_JSON")"
if [[ "$REG_CODE" == "201" || "$REG_CODE" == "200" ]]; then
  pass "candidate register [$REG_CODE]"
else
  fail "candidate register [got $REG_CODE] body=$(cat "$REGISTER_RESP" | head -c 180)"
fi

LOGIN_CODE="$(http_code POST "$AUTH_BASE_URL/login" "$LOGIN_RESP" -H "Content-Type: application/json" --data-binary @"$LOGIN_JSON")"
if [[ "$LOGIN_CODE" == "200" ]]; then
  pass "candidate login [$LOGIN_CODE]"
else
  fail "candidate login [got $LOGIN_CODE] body=$(cat "$LOGIN_RESP" | head -c 180)"
fi

TOKEN="$(python3 - <<PY
import json
try:
    print(json.load(open("$LOGIN_RESP")).get("accessToken", ""))
except Exception:
    print("")
PY
)"

if [[ -n "$TOKEN" ]]; then
  pass "jwt token extracted"
else
  fail "jwt token missing from login response"
fi

if [[ -n "$TOKEN" ]]; then
  TV_CODE="$(http_code GET "$AUTH_BASE_URL/validate" "$TOKEN_VALIDATE_RESP" -H "Authorization: Bearer $TOKEN")"
  if [[ "$TV_CODE" == "200" ]]; then
    pass "jwt validate endpoint [$TV_CODE]"
  else
    fail "jwt validate endpoint [got $TV_CODE] body=$(cat "$TOKEN_VALIDATE_RESP" | head -c 180)"
  fi
fi

echo "[flow] Resume upload"
RESUME_FILE="$WORK_DIR/demo_resume.txt"
PARSE_RESP="$WORK_DIR/parse_resp.json"

cat > "$RESUME_FILE" <<TXT
LinkedIn Demo Candidate
Skills: Java, Spring Boot, Docker, Kubernetes, AWS
Experience: 5 years building microservices
TXT

if [[ -n "$TOKEN" ]]; then
  PARSE_CODE="$(http_code POST "$RESUME_BASE_URL/parse" "$PARSE_RESP" -H "Authorization: Bearer $TOKEN" -F "file=@$RESUME_FILE;type=text/plain")"
  if [[ "$PARSE_CODE" == "200" ]]; then
    pass "resume parse [$PARSE_CODE]"
  else
    fail "resume parse [got $PARSE_CODE] body=$(cat "$PARSE_RESP" | head -c 180)"
  fi
else
  fail "resume parse skipped (missing token)"
fi

echo "[flow] Candidate matching"
MATCH_RESP="$WORK_DIR/match_resp.json"
MATCH_CODE="$(http_code POST "$MATCH_BASE_URL/match" "$MATCH_RESP" -H "Content-Type: application/json" --data-binary '{"candidateId":1,"skills":["Java","Spring Boot","Docker","Kubernetes","AWS"],"experienceYears":5.0,"location":"Remote"}')"
if [[ "$MATCH_CODE" == "200" ]]; then
  pass "candidate matching [$MATCH_CODE]"
else
  fail "candidate matching [got $MATCH_CODE] body=$(cat "$MATCH_RESP" | head -c 180)"
fi

echo "[flow] Screening pipeline"
SESSION_RESP="$WORK_DIR/session_resp.json"
ADVANCE_RESP="$WORK_DIR/advance_resp.json"
DECISION_RESP="$WORK_DIR/decision_resp.json"
RESPONSE_RESP="$WORK_DIR/response_resp.json"

SESSION_CODE="$(http_code POST "$SCREEN_BASE_URL/sessions" "$SESSION_RESP" -H "Content-Type: application/json" --data-binary '{"candidateId":1,"jobId":"JOB-001"}')"
if [[ "$SESSION_CODE" == "200" ]]; then
  pass "screening create session [$SESSION_CODE]"
else
  fail "screening create session [got $SESSION_CODE] body=$(cat "$SESSION_RESP" | head -c 180)"
fi

SESSION_ID="$(python3 - <<PY
import json
try:
    data=json.load(open("$SESSION_RESP"))
    print(data.get("id", data.get("sessionId", "")))
except Exception:
    print("")
PY
)"

if [[ -n "$SESSION_ID" ]]; then
  pass "screening session id extracted"

  RESP_CODE="$(http_code POST "$SCREEN_BASE_URL/sessions/$SESSION_ID/responses" "$RESPONSE_RESP" -H "Content-Type: application/json" --data-binary '{"stage":"initial","response":"I have strong Java and cloud deployment experience."}')"
  if [[ "$RESP_CODE" == "200" ]]; then
    pass "screening submit response [$RESP_CODE]"
  else
    fail "screening submit response [got $RESP_CODE] body=$(cat "$RESPONSE_RESP" | head -c 180)"
  fi

  ADV_CODE="$(http_code POST "$SCREEN_BASE_URL/sessions/$SESSION_ID/advance" "$ADVANCE_RESP")"
  if [[ "$ADV_CODE" == "200" ]]; then
    pass "screening advance [$ADV_CODE]"
  else
    fail "screening advance [got $ADV_CODE] body=$(cat "$ADVANCE_RESP" | head -c 180)"
  fi

  DEC_CODE="$(http_code GET "$SCREEN_BASE_URL/sessions/$SESSION_ID/decision" "$DECISION_RESP")"
  if [[ "$DEC_CODE" == "200" ]]; then
    pass "screening decision [$DEC_CODE]"
  else
    fail "screening decision [got $DEC_CODE] body=$(cat "$DECISION_RESP" | head -c 180)"
  fi
else
  fail "screening session id missing"
fi

echo "[flow] Notification proof points"
MAILHOG_CODE="$(curl -s -o "$WORK_DIR/mailhog.out" -w "%{http_code}" http://localhost:8025 || true)"
if [[ "$MAILHOG_CODE" == "200" ]]; then
  pass "mailhog ui (optional) [$MAILHOG_CODE]"
else
  pass "mailhog ui (optional) skipped [code $MAILHOG_CODE]"
fi

printf "\n===== LinkedIn Demo Summary =====\n"
printf "%-6s | %s\n" "Status" "Check"
printf -- "-----------------------------------------------\n"
for line in "${RESULTS[@]}"; do
  printf "%s\n" "$line"
done
printf -- "-----------------------------------------------\n"
printf "PASS=%d FAIL=%d\n" "$PASS_COUNT" "$FAIL_COUNT"

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  exit 1
fi


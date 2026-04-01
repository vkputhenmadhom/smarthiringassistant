#!/usr/bin/env bash
set -euo pipefail

AUTH_BASE_URL="${AUTH_BASE_URL:-http://localhost:8001/api/auth}"
RESUME_BASE_URL="${RESUME_BASE_URL:-http://localhost:8002/api/resumes}"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

register_json="$work_dir/register.json"
login_json="$work_dir/login.json"
register_resp="$work_dir/register_resp.json"
login_resp="$work_dir/login_resp.json"
parse_resp="$work_dir/parse_resp.json"
resume_file="$work_dir/smoke_resume.txt"

username="smoke$(date +%s)"
password="SmokePass123!"
email="${username}@example.com"

cat > "$register_json" <<JSON
{"username":"$username","email":"$email","password":"$password","confirmPassword":"$password","firstName":"Smoke","lastName":"Tester","role":"JOB_SEEKER"}
JSON

cat > "$login_json" <<JSON
{"username":"$username","password":"$password"}
JSON

echo "[1/4] Registering smoke user: $username"
register_code="$(curl -s -o "$register_resp" -w "%{http_code}" -H "Content-Type: application/json" --data-binary @"$register_json" "$AUTH_BASE_URL/register")"
echo "register_code=$register_code"
if [[ "$register_code" != "201" && "$register_code" != "200" ]]; then
  echo "register_response=$(cat "$register_resp")"
  exit 1
fi

echo "[2/4] Logging in smoke user"
login_code="$(curl -s -o "$login_resp" -w "%{http_code}" -H "Content-Type: application/json" --data-binary @"$login_json" "$AUTH_BASE_URL/login")"
echo "login_code=$login_code"
if [[ "$login_code" != "200" ]]; then
  echo "login_response=$(cat "$login_resp")"
  exit 1
fi

token="$(python3 - <<PY
import json
print(json.load(open("$login_resp")).get("accessToken", ""))
PY
)"
if [[ -z "$token" ]]; then
  echo "Token missing in login response"
  echo "login_response=$(cat "$login_resp")"
  exit 1
fi

echo "[3/4] Calling protected resume parse endpoint"
cat > "$resume_file" <<'TXT'
John Doe
Java
Spring Boot
Docker
TXT

parse_code="$(curl -s -o "$parse_resp" -w "%{http_code}" -H "Authorization: Bearer $token" -F "file=@$resume_file;type=text/plain" "$RESUME_BASE_URL/parse")"
echo "parse_code=$parse_code"
if [[ "$parse_code" != "200" ]]; then
  echo "parse_response=$(cat "$parse_resp")"
  exit 1
fi

resume_id="$(python3 - <<PY
import json
print(json.load(open("$parse_resp")).get("resumeId", ""))
PY
)"

echo "[4/4] Smoke test passed"
echo "resume_id=$resume_id"


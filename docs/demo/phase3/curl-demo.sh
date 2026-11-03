#!/usr/bin/env bash
set -euo pipefail

BASE_JOBS="http://localhost:8005/api/jobs"
BASE_SCREEN="http://localhost:8006/api/screening"

echo "1) Analyze job description"
ANALYSIS=$(curl -sS -X POST "$BASE_JOBS/analyze" \
  -H "Content-Type: application/json" \
  -d '{
    "jobId":"job-101",
    "jobTitle":"Senior Java Engineer",
    "companyName":"Acme Corp",
    "location":"New York",
    "employmentType":"FULL_TIME",
    "jobDescription":"We need 5+ years with Java, Spring Boot, Docker, AWS, PostgreSQL and Microservices."
  }')

echo "$ANALYSIS"
ANALYSIS_ID=$(echo "$ANALYSIS" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
echo "analysisId=$ANALYSIS_ID"

echo "2) Extract skills directly"
curl -sS -X POST "$BASE_JOBS/extract-skills" \
  -H "Content-Type: application/json" \
  -d '{"text":"Experience with Java, Spring Boot, Kubernetes, AWS and GraphQL."}'
echo

echo "3) Estimate salary"
curl -sS -X POST "$BASE_JOBS/estimate-salary" \
  -H "Content-Type: application/json" \
  -d '{
    "jobTitle":"Senior Java Engineer",
    "location":"New York",
    "experienceYears":6,
    "requiredSkills":["Java","Spring Boot","AWS","Docker"]
  }'
echo

echo "4) Create screening session"
SESSION=$(curl -sS -X POST "$BASE_SCREEN/sessions" \
  -H "Content-Type: application/json" \
  -d '{"candidateId":99,"jobId":"job-101"}')

echo "$SESSION"
SESSION_ID=$(echo "$SESSION" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
echo "sessionId=$SESSION_ID"

echo "5) Submit stage response"
curl -sS -X POST "$BASE_SCREEN/sessions/$SESSION_ID/responses" \
  -H "Content-Type: application/json" \
  -d '{
    "stage":"initial",
    "response":"I have 7 years of Java/Spring experience. Example: reduced latency by 40% and improved API availability."
  }'
echo

echo "6) Advance session"
curl -sS -X POST "$BASE_SCREEN/sessions/$SESSION_ID/advance"
echo

echo "7) Get decision"
curl -sS "$BASE_SCREEN/sessions/$SESSION_ID/decision"
echo

echo "8) Metrics endpoints"
echo "Job Analyzer Prometheus: http://localhost:8005/actuator/prometheus"
echo "Screening Bot Prometheus: http://localhost:8006/actuator/prometheus"


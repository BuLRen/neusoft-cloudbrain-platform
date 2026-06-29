#!/usr/bin/env bash
# ai-catalog-service 本地/云侧冒烟测试
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8098}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [[ -z "${INTERNAL_AI_TOKEN:-}" ]]; then
  echo "ERROR: INTERNAL_AI_TOKEN 未设置（.env 或环境变量）"
  exit 1
fi

echo "=== drugs ai-search (stock fields) ==="
curl -sf -X POST "${BASE_URL}/api/physician/internal/drugs/ai-search" \
  -H "Authorization: Bearer ${INTERNAL_AI_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"drugKeywords":["阿莫西林"],"limit":1}' | grep -o '"stockQuantity":[0-9]*' | head -1 || true
echo ""

echo "=== drugs ai-search (W5 full payload) ==="
curl -sf -X POST "${BASE_URL}/api/physician/internal/drugs/ai-search" \
  -H "Authorization: Bearer ${INTERNAL_AI_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"drugKeywords":["阿莫西林","头孢克肟","布洛芬","对乙酰氨基酚"],"genericKeywords":[],"categoryKeywords":["抗生素","解热镇痛","西药"],"indicationKeywords":["急性上呼吸道感染","咽痛","发热","上感"],"negativeKeywords":[],"limit":10}' | head -c 800
echo ""

echo "=== diseases ai-search ==="
curl -sf -X POST "${BASE_URL}/api/physician/internal/diseases/ai-search" \
  -H "Authorization: Bearer ${INTERNAL_AI_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"diseaseKeywords":["肺炎"],"limit":3}' | head -c 400
echo ""

echo "OK: ai-catalog smoke passed (${BASE_URL})"

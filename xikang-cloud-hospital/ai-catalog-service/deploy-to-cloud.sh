#!/usr/bin/env bash
# 将 ai-catalog-service JAR 部署到云服务器（与 Dify 同机）
# 用法:
#   export REMOTE_SSH_USER=root REMOTE_SSH_HOST=43.139.102.203
#   export REMOTE_DEPLOY_PATH=/www/wwwroot/cz/xikang/ai-catalog
#   ./deploy-to-cloud.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$SCRIPT_DIR/target/ai-catalog-service-1.0.0.jar"

REMOTE_SSH_USER="${REMOTE_SSH_USER:-root}"
REMOTE_SSH_HOST="${REMOTE_SSH_HOST:-43.139.102.203}"
REMOTE_DEPLOY_PATH="${REMOTE_DEPLOY_PATH:-/www/wwwroot/cz/xikang/ai-catalog}"

if [[ ! -f "$JAR" ]]; then
  echo "Building ai-catalog-service..."
  (cd "$ROOT_DIR" && mvn -pl ai-catalog-service -am package -DskipTests -q)
fi

echo "Uploading JAR to ${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}:${REMOTE_DEPLOY_PATH}/"
ssh "${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}" "mkdir -p '${REMOTE_DEPLOY_PATH}'"
scp "$JAR" "${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}:${REMOTE_DEPLOY_PATH}/ai-catalog-service-1.0.0.jar"

echo "Restarting ai-catalog-service on remote..."
ssh "${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}" bash -s <<EOF
set -euo pipefail
cd '${REMOTE_DEPLOY_PATH}'
if [[ ! -f .env ]]; then
  echo "ERROR: ${REMOTE_DEPLOY_PATH}/.env 不存在，请先创建 DB_REMOTE_PASSWORD 与 INTERNAL_AI_TOKEN"
  exit 1
fi
pkill -f 'ai-catalog-service-1.0.0.jar' 2>/dev/null || true
sleep 1
nohup env NACOS_DISCOVERY_ENABLED=false java -jar ai-catalog-service-1.0.0.jar > ai-catalog.log 2>&1 &
sleep 3
curl -sf http://127.0.0.1:8098/actuator/health >/dev/null && echo "Health check OK" || echo "WARN: health check failed, see ai-catalog.log"
EOF

cat <<'NOTE'

部署后请手动完成:
1. Dify W4 HTTP 节点 URL → http://172.17.0.1:8098/api/physician/internal/diseases/ai-search
2. Dify W5 HTTP 节点 URL → http://172.17.0.1:8098/api/physician/internal/drugs/ai-search
3. Header 保持: Authorization: Bearer <INTERNAL_AI_TOKEN>
4. 云侧 physician-service 若仅用于 Dify HTTP 回调，可停止

NOTE

#!/usr/bin/env bash
# 加载 xikang-cloud-hospital/.env（数据库、AI / 第三方 API 等）
#
# 用法: source ./load-env.sh
# 切换数据库：编辑 .env 中 SPRING_PROFILES_ACTIVE=remote 或 local

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[WARN] 未找到 ${ENV_FILE}，跳过环境变量加载"
  return 0 2>/dev/null || exit 0
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "[INFO] 已加载 .env（SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-未设置}）"

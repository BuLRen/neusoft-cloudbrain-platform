#!/usr/bin/env bash
# 加载 xikang-cloud-hospital/.env 中的 AI / 第三方 API 密钥
# 数据库切换请编辑 config/database.yml，无需本脚本
#
# 用法: source ./load-env.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[WARN] 未找到 ${ENV_FILE}，跳过 AI 密钥加载"
  return 0 2>/dev/null || exit 0
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "[INFO] 已加载 .env（AI / 第三方 API 密钥）"
echo "[INFO] 数据库配置见 config/database.yml → spring.profiles.active"

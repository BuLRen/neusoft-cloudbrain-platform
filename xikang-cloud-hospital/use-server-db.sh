#!/usr/bin/env bash
# 已废弃：数据库切换请编辑 config/database.yml
# 本脚本仅转发至 load-env.sh（加载 AI 密钥）

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1090
source "${SCRIPT_DIR}/load-env.sh"

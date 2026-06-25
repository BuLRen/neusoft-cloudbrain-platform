#!/usr/bin/env bash
# =============================================================================
# 在远程服务器上修复 PostgreSQL 主键序列（导入快照后若 INSERT 报 duplicate key）
#
# 用法（SSH 登录服务器后）:
#   export PGPASSWORD='你的数据库密码'
#   bash fix-sequences-on-server.sh
#
# 可选环境变量:
#   PGHOST      默认 127.0.0.1
#   PGPORT      默认 5432
#   PGUSER      默认 xikang_hospital
#   PGDATABASE  默认 xikang_hospital
#   PSQL        psql 可执行文件路径（宝塔默认见下方）
# =============================================================================
set -euo pipefail

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-xikang_hospital}"
PGDATABASE="${PGDATABASE:-xikang_hospital}"

if [[ -z "${PGPASSWORD:-}" ]]; then
  echo "请先设置 PGPASSWORD，例如: export PGPASSWORD='你的密码'"
  exit 1
fi

if [[ -n "${PSQL:-}" ]]; then
  PSQL_BIN="${PSQL}"
elif command -v psql >/dev/null 2>&1; then
  PSQL_BIN="$(command -v psql)"
elif [[ -x /www/server/pgsql/bin/psql ]]; then
  PSQL_BIN="/www/server/pgsql/bin/psql"
else
  echo "未找到 psql。请安装 PostgreSQL 客户端，或设置 PSQL=/path/to/psql"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIX_SQL="${SCRIPT_DIR}/post-import-fix-sequences.sql"

if [[ ! -f "${FIX_SQL}" ]]; then
  echo "缺少 ${FIX_SQL}，请与 fix-sequences-on-server.sh 放在同一目录后重试。"
  exit 1
fi

echo "==> 连接: ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}"
echo "==> 使用: ${PSQL_BIN}"
echo ""
echo "==> 修复前 ai_medical_record_log 序列状态:"
"${PSQL_BIN}" -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -v ON_ERROR_STOP=1 -c "
SELECT
  (SELECT MAX(id) FROM ai_medical_record_log) AS max_id,
  (SELECT last_value FROM ai_medical_record_log_id_seq) AS seq_last;
"

echo ""
echo "==> 执行全库序列修复..."
"${PSQL_BIN}" -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -v ON_ERROR_STOP=1 -f "${FIX_SQL}"

echo ""
echo "==> 修复后 ai_medical_record_log 序列状态:"
"${PSQL_BIN}" -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -v ON_ERROR_STOP=1 -c "
SELECT
  (SELECT MAX(id) FROM ai_medical_record_log) AS max_id,
  (SELECT last_value FROM ai_medical_record_log_id_seq) AS seq_last,
  CASE
    WHEN (SELECT last_value FROM ai_medical_record_log_id_seq) >= COALESCE((SELECT MAX(id) FROM ai_medical_record_log), 0)
    THEN 'OK'
    ELSE '仍不一致，请检查'
  END AS status;
"

echo ""
echo "完成。可重新运行「初步诊断」工作流验证。"

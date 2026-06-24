#!/usr/bin/env bash
# =============================================================================
# 希康云医院 - 本地 Docker PostgreSQL → 服务器 PostgreSQL 迁移脚本
#
# 用法:
#   cp migrate-to-server.env.example migrate-to-server.env   # 首次配置
#   ./migrate-to-server.sh export      # 从本地导出
#   ./migrate-to-server.sh import      # 导入到服务器（使用最新 dump）
#   ./migrate-to-server.sh import FILE # 导入指定 dump
#   ./migrate-to-server.sh verify      # 对比本地与服务器表数量
#   ./migrate-to-server.sh upload      # SCP 上传最新 dump 到服务器
#   ./migrate-to-server.sh all         # export + 提示后续步骤
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/migrate-to-server.env"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

load_env() {
    if [[ ! -f "$ENV_FILE" ]]; then
        error "未找到配置文件: $ENV_FILE"
        echo "请先执行: cp migrate-to-server.env.example migrate-to-server.env"
        exit 1
    fi
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    DUMP_DIR="${DUMP_DIR:-${SCRIPT_DIR}/backups}"
}

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        error "缺少命令: $1"
        exit 1
    fi
}

latest_dump() {
    ls -t "${DUMP_DIR}"/xikang_hospital_*.sql 2>/dev/null | head -1 || true
}

psql_local() {
    PGPASSWORD="${LOCAL_PASSWORD}" psql -h "${LOCAL_HOST}" -p "${LOCAL_PORT}" -U "${LOCAL_USER}" "$@"
}

psql_remote() {
    PGPASSWORD="${REMOTE_PASSWORD}" psql -h "${REMOTE_HOST}" -p "${REMOTE_PORT}" -U "${REMOTE_USER}" "$@"
}

check_local() {
    info "检查本地数据库连接..."
    if [[ -n "${LOCAL_DOCKER_CONTAINER:-}" ]] && docker ps --format '{{.Names}}' | grep -qx "${LOCAL_DOCKER_CONTAINER}"; then
        docker exec "${LOCAL_DOCKER_CONTAINER}" pg_isready -U "${LOCAL_USER}" >/dev/null
        info "本地 Docker 容器 ${LOCAL_DOCKER_CONTAINER} 运行正常"
        return 0
    fi
    psql_local -d "${LOCAL_DB}" -c "SELECT 1" >/dev/null
    info "本地数据库 ${LOCAL_HOST}:${LOCAL_PORT}/${LOCAL_DB} 连接正常"
}

check_remote() {
    if [[ -z "${REMOTE_HOST:-}" || "${REMOTE_HOST}" == "你的服务器IP" ]]; then
        error "请在 migrate-to-server.env 中填写 REMOTE_HOST 等服务器连接信息"
        exit 1
    fi
    info "检查服务器数据库连接..."
    psql_remote -d postgres -c "SELECT 1" >/dev/null
    info "服务器数据库 ${REMOTE_HOST}:${REMOTE_PORT} 连接正常"
}

do_export() {
    require_cmd docker
    check_local
    mkdir -p "${DUMP_DIR}"

    local timestamp dump_file tmp_in_container="/tmp/xikang_export.sql"
    timestamp="$(date +%Y%m%d_%H%M%S)"
    dump_file="${DUMP_DIR}/xikang_hospital_${timestamp}.sql"

    info "开始导出 → ${dump_file}"

    if [[ -n "${LOCAL_DOCKER_CONTAINER:-}" ]] && docker ps --format '{{.Names}}' | grep -qx "${LOCAL_DOCKER_CONTAINER}"; then
        docker exec "${LOCAL_DOCKER_CONTAINER}" pg_dump \
            -U "${LOCAL_USER}" \
            -d "${LOCAL_DB}" \
            --no-owner \
            --no-acl \
            --encoding=UTF8 \
            -f "${tmp_in_container}"

        docker cp "${LOCAL_DOCKER_CONTAINER}:${tmp_in_container}" "${dump_file}"
        docker exec "${LOCAL_DOCKER_CONTAINER}" rm -f "${tmp_in_container}"
    else
        require_cmd pg_dump
        PGPASSWORD="${LOCAL_PASSWORD}" pg_dump \
            -h "${LOCAL_HOST}" \
            -p "${LOCAL_PORT}" \
            -U "${LOCAL_USER}" \
            -d "${LOCAL_DB}" \
            --no-owner \
            --no-acl \
            --encoding=UTF8 \
            -f "${dump_file}"
    fi

    # 写入迁移说明头
    local header_file="${dump_file}.header"
    cat > "${header_file}" <<EOF
-- =============================================================================
-- 希康云医院 数据库迁移导出
-- 导出时间: $(date '+%Y-%m-%d %H:%M:%S')
-- 来源    : ${LOCAL_HOST}:${LOCAL_PORT}/${LOCAL_DB}
-- 目标    : ${REMOTE_HOST:-<待填写>}:${REMOTE_PORT:-5432}/${REMOTE_DB:-xikang_hospital}
-- 说明    : pg_dump --no-owner --no-acl，适用于宝塔等外部 PostgreSQL
-- =============================================================================

SET timezone = 'Asia/Shanghai';

EOF
    cat "${header_file}" "${dump_file}" > "${dump_file}.tmp" && mv "${dump_file}.tmp" "${dump_file}"
    rm -f "${header_file}"

    local size tables
    size="$(du -h "${dump_file}" | cut -f1)"
    tables="$(grep -c '^CREATE TABLE' "${dump_file}" || true)"
    info "导出完成: ${dump_file} (${size}, 约 ${tables} 张表)"
    echo "${dump_file}"
}

do_import() {
    require_cmd psql
    check_remote

    local dump_file="${1:-}"
    if [[ -z "${dump_file}" ]]; then
        dump_file="$(latest_dump)"
    fi
    if [[ -z "${dump_file}" || ! -f "${dump_file}" ]]; then
        error "未找到 dump 文件，请先执行: ./migrate-to-server.sh export"
        exit 1
    fi

    info "使用 dump: ${dump_file}"
    warn "即将导入到 ${REMOTE_HOST}:${REMOTE_PORT}/${REMOTE_DB}"
    warn "若目标库已有同名表，导入可能失败。建议在空库上执行。"
    read -r -p "确认继续? [y/N] " confirm
    if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
        info "已取消"
        exit 0
    fi

    info "创建数据库（若不存在）..."
    psql_remote -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = '${REMOTE_DB}'" | grep -q 1 \
        || psql_remote -d postgres -c "CREATE DATABASE ${REMOTE_DB} ENCODING 'UTF8';"

    info "导入 SQL（可能需要 1-3 分钟）..."
    psql_remote -d "${REMOTE_DB}" -v ON_ERROR_STOP=1 -f "${dump_file}"

    info "修复序列..."
    psql_remote -d "${REMOTE_DB}" -f "${SCRIPT_DIR}/post-import-fix-sequences.sql"

    info "导入完成"
    do_verify_remote_summary
}

do_verify() {
    require_cmd psql
    check_local
    check_remote

    info "对比 public schema 表数量..."
    local local_count remote_count
    local_count="$(psql_local -d "${LOCAL_DB}" -Atc \
        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';")"
    remote_count="$(psql_remote -d "${REMOTE_DB}" -Atc \
        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';")"

    echo "  本地表数量  : ${local_count}"
    echo "  服务器表数量: ${remote_count}"

    if [[ "${local_count}" == "${remote_count}" ]]; then
        info "表数量一致"
    else
        warn "表数量不一致，请检查导入日志"
    fi
}

do_verify_remote_summary() {
    psql_remote -d "${REMOTE_DB}" -c \
        "SELECT schemaname, relname AS table, n_live_tup AS rows
         FROM pg_stat_user_tables
         ORDER BY relname
         LIMIT 20;"
}

do_upload() {
    local dump_file
    dump_file="$(latest_dump)"
    if [[ -z "${dump_file}" || ! -f "${dump_file}" ]]; then
        error "没有可上传的 dump，请先 export"
        exit 1
    fi
    if [[ -z "${REMOTE_SSH_HOST:-}" || "${REMOTE_SSH_HOST}" == "你的服务器IP" ]]; then
        error "请在 migrate-to-server.env 中配置 REMOTE_SSH_*"
        exit 1
    fi

    require_cmd scp
    info "上传到 ${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}:${REMOTE_SSH_PATH}/"
    ssh "${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}" "mkdir -p ${REMOTE_SSH_PATH}"
    scp "${dump_file}" "${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}:${REMOTE_SSH_PATH}/"
    info "上传完成。在服务器上执行:"
    echo ""
    echo "  psql -h 127.0.0.1 -p ${REMOTE_PORT} -U ${REMOTE_USER} -d ${REMOTE_DB} -f ${REMOTE_SSH_PATH}/$(basename "${dump_file}")"
    echo "  psql -h 127.0.0.1 -p ${REMOTE_PORT} -U ${REMOTE_USER} -d ${REMOTE_DB} -f ${REMOTE_SSH_PATH}/../post-import-fix-sequences.sql"
    echo ""
}

print_baota_guide() {
    cat <<'EOF'

=============================================================================
宝塔面板导入指引
=============================================================================
1. 软件商店安装「PostgreSQL 管理器」，创建数据库 xikang_hospital
2. 将 backups/ 下的 .sql 文件上传到服务器（如 /www/wwwroot/xikang-db/backups/）
3. 宝塔终端执行:

   export PGPASSWORD='你的密码'
   /www/server/pgsql/bin/psql -h 127.0.0.1 -p 5432 -U postgres -d xikang_hospital \
     -f /www/wwwroot/xikang-db/backups/xikang_hospital_YYYYMMDD_HHMMSS.sql

   /www/server/pgsql/bin/psql -h 127.0.0.1 -p 5432 -U postgres -d xikang_hospital \
     -f /path/to/post-import-fix-sequences.sql

4. 修改各微服务 application.yml:
     url: jdbc:postgresql://服务器IP:5432/xikang_hospital

5. 宝塔防火墙不要对公网开放 5432 端口

EOF
}

usage() {
    cat <<EOF
用法: $(basename "$0") <command>

命令:
  export [file]   从本地 Docker/PostgreSQL 导出（默认保存到 backups/）
  import [file]   导入到服务器（默认使用最新 dump）
  verify          对比本地与服务器表数量
  upload          SCP 上传最新 dump 到服务器
  all             导出并显示后续步骤

配置文件: migrate-to-server.env
EOF
}

main() {
    local cmd="${1:-}"
    shift || true

    case "${cmd}" in
        export)
            load_env
            do_export
            ;;
        import)
            load_env
            do_import "${1:-}"
            ;;
        verify)
            load_env
            do_verify
            ;;
        upload)
            load_env
            do_upload
            ;;
        all)
            load_env
            do_export
            print_baota_guide
            info "下一步: 填写 migrate-to-server.env 中的 REMOTE_* 后执行 ./migrate-to-server.sh import"
            ;;
        -h|--help|help|"")
            usage
            ;;
        *)
            error "未知命令: ${cmd}"
            usage
            exit 1
            ;;
    esac
}

main "$@"

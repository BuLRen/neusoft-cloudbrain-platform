#!/bin/bash
# =============================================================================
# schedule-service 一键部署脚本（宝塔 / OpenCloudOS）
# 用法：
#   1. 把 schedule-service-1.0.0.jar 和本脚本 deploy-schedule.sh
#      上传到服务器同一目录（推荐 /www/wwwroot/xikang/）
#   2. chmod +x deploy-schedule.sh
#   3. ./deploy-schedule.sh                 # 部署（后台启动）
#   4. ./deploy-schedule.sh logs            # 看日志
#   5. ./deploy-schedule.sh stop            # 停服务
#   6. ./deploy-schedule.sh status          # 看状态
# =============================================================================
set -e

# ============= 配置区（需要改路径就改这里）=============
APP_NAME="schedule-service"
JAR_FILE="schedule-service-1.0.0.jar"
WORK_DIR="/www/wwwroot/xikang"
JAVA_BIN="/www/server/java/jdk-17.0.8/bin/java"

PID_FILE="${WORK_DIR}/${APP_NAME}.pid"
LOG_FILE="${WORK_DIR}/${APP_NAME}.log"
JAR_PATH="${WORK_DIR}/${JAR_FILE}"

# ============= 启动参数（一行，不要换行）=============
JAVA_OPTS="-Xms256M -Xmx1024M -DNACOS_SERVER_ADDR=118.178.253.230:8848 -Dspring.cloud.nacos.discovery.server-addr=118.178.253.230:8848 -Dspring.cloud.nacos.config.server-addr=118.178.253.230:8848 -DDB_HOST=43.139.102.203 -DDB_PORT=5432 -DDB_NAME=xikang_hospital -DDB_USERNAME=xikang_hospital -DDB_PASSWORD=eA6CNaWFewsMGCyZ -DDIFY_API_KEY_SCHEDULE=app-hsl2jYiWAjliQDgJgQ55WTs8 -DDIFY_BASE_URL_SCHEDULE=http://118.178.253.230 -DDIFY_LEAVE_ADJUST_API_KEY=app-Bls0cJQPAaqSckiPYHoDefHi -DDIFY_CALLBACK_TOKEN=schedule-internal-2026"

# ============= 工具函数 =============
check_jar() {
    if [ ! -f "${JAR_PATH}" ]; then
        echo "[ERROR] jar 包不存在: ${JAR_PATH}"
        echo "        请先把 ${JAR_FILE} 上传到 ${WORK_DIR}/"
        exit 1
    fi
    SIZE=$(du -h "${JAR_PATH}" | awk '{print $1}')
    echo "[INFO] 找到 jar: ${JAR_PATH} (${SIZE})"
}

is_running() {
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat ${PID_FILE})
        if kill -0 ${PID} 2>/dev/null; then
            return 0
        fi
    fi
    # 兜底：按 jar 名查进程
    if pgrep -f "${JAR_FILE}" > /dev/null 2>&1; then
        return 0
    fi
    return 1
}

# ============= 命令处理 =============
case "$1" in
    start)
        check_jar
        if is_running; then
            echo "[WARN] ${APP_NAME} 已经在运行"
            exit 0
        fi
        echo "[INFO] 启动 ${APP_NAME}..."
        cd "${WORK_DIR}"
        nohup "${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR_PATH}" > "${LOG_FILE}" 2>&1 &
        echo $! > "${PID_FILE}"
        echo "[OK] 启动命令已执行, pid=$(cat ${PID_FILE})"
        echo "[INFO] 等待 8 秒，看启动日志..."
        echo "----------------------------------------"
        sleep 8
        tail -n 30 "${LOG_FILE}"
        echo "----------------------------------------"
        if is_running; then
            echo "[OK] ${APP_NAME} 进程存活，继续观察日志确认是否完全启动"
            echo "     命令: $0 logs"
        else
            echo "[FAIL] 进程已退出，看完整日志找原因:"
            echo "       tail -n 100 ${LOG_FILE}"
        fi
        ;;

    stop)
        echo "[INFO] 停止 ${APP_NAME}..."
        if [ -f "${PID_FILE}" ]; then
            PID=$(cat ${PID_FILE})
            kill ${PID} 2>/dev/null || true
            sleep 3
            kill -9 ${PID} 2>/dev/null || true
            rm -f "${PID_FILE}"
            echo "[OK] 已按 pid 文件停止"
        fi
        # 兜底：按 jar 名杀
        pkill -f "${JAR_FILE}" 2>/dev/null || true
        echo "[OK] 停止完成"
        ;;

    restart)
        $0 stop
        sleep 2
        $0 start
        ;;

    status)
        if is_running; then
            PID=$(cat ${PID_FILE} 2>/dev/null || pgrep -f "${JAR_FILE}")
            echo "[OK] ${APP_NAME} 运行中, pid=${PID}"
            # 顺便看下端口
            if command -v ss > /dev/null 2>&1; then
                ss -tlnp 2>/dev/null | grep 8095 || echo "[INFO] 端口 8095 暂未监听（可能还在启动中）"
            fi
            exit 0
        else
            echo "[FAIL] ${APP_NAME} 未运行"
            exit 1
        fi
        ;;

    logs)
        tail -f "${LOG_FILE}"
        ;;

    deploy)
        check_jar
        $0 stop
        sleep 2
        $0 start
        ;;

    *)
        echo "Usage: $0 {start|stop|restart|status|logs|deploy}"
        echo "  start   启动服务（后台）"
        echo "  stop    停止服务"
        echo "  restart 重启服务"
        echo "  status  查看运行状态"
        echo "  logs    实时查看日志（Ctrl+C 退出）"
        echo "  deploy  停掉旧进程 + 启动新进程（重新部署用这个）"
        exit 1
        ;;
esac

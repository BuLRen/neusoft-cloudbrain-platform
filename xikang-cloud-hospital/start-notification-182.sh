#!/bin/bash
# =============================================================================
# notification-service 启动脚本（部署到 182.92.193.45）
# 用法：
#   1. 把 notification-service-1.0.0.jar、.env.notification-182、本脚本放在同一目录
#   2. mv .env.notification-182 .env
#   3. chmod +x start-notification-182.sh
#   4. ./start-notification-182.sh start | stop | status | logs
# =============================================================================
set -e

APP_NAME="notification-service"
JAR_FILE="notification-service-1.0.0.jar"
PID_FILE="${APP_NAME}.pid"
LOG_FILE="${APP_NAME}.log"

# ---------- 关键：把 .env 加载到当前 shell 进程环境 ----------
if [ -f "./.env" ]; then
    set -a
    source ./.env
    set +a
    echo "[INFO] .env loaded, NACOS_SERVER_ADDR=${NACOS_SERVER_ADDR}"
else
    echo "[ERROR] .env not found in $(pwd)"
    exit 1
fi

# ---------- 命令处理 ----------
case "$1" in
    start)
        if [ -f "${PID_FILE}" ]; then
            OLD_PID=$(cat ${PID_FILE})
            if kill -0 ${OLD_PID} 2>/dev/null; then
                echo "[ERROR] ${APP_NAME} already running, pid=${OLD_PID}"
                exit 1
            fi
        fi
        nohup java -jar ${JAR_FILE} \
            --spring.profiles.active=remote \
            > ${LOG_FILE} 2>&1 &
        echo $! > ${PID_FILE}
        echo "[OK] ${APP_NAME} started, pid=$(cat ${PID_FILE}), log=${LOG_FILE}"
        sleep 3
        tail -n 20 ${LOG_FILE}
        ;;
    stop)
        if [ -f "${PID_FILE}" ]; then
            PID=$(cat ${PID_FILE})
            kill ${PID} 2>/dev/null || true
            sleep 2
            kill -9 ${PID} 2>/dev/null || true
            rm -f ${PID_FILE}
            echo "[OK] ${APP_NAME} stopped"
        else
            echo "[WARN] no pid file, nothing to stop"
        fi
        ;;
    status)
        if [ -f "${PID_FILE}" ]; then
            PID=$(cat ${PID_FILE})
            if kill -0 ${PID} 2>/dev/null; then
                echo "[OK] running, pid=${PID}"
                exit 0
            fi
        fi
        echo "[FAIL] not running"
        exit 1
        ;;
    logs)
        tail -f ${LOG_FILE}
        ;;
    *)
        echo "Usage: $0 {start|stop|status|logs}"
        exit 1
        ;;
esac

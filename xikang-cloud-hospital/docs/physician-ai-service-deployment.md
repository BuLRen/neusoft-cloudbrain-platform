# physician-ai-service 拆分部署指南

> 日期：2026-07-01  
> 状态：已实现

## 概述

`physician-service`（8092）中的 AI 能力已拆分为独立微服务 `physician-ai-service`（**8097**）。

| 服务 | 端口 | 职责 |
|------|------|------|
| `physician-service` | 8092 | 纯门诊业务（病历、处方、医技开单等） |
| `physician-ai-service` | 8097 | Dify 工作流 W1–W5、Agent 工具回调、Copilot 会话 |

## 路由与调用关系

- **前端**（经网关 8080）：`/api/physician/ai/**`、`/api/physician/copilot/**` → `physician-ai-service`；其余 `/api/physician/**` → `physician-service`
- **Dify Agent 回调**（直连，不经网关）：`http://<host>:8097/api/physician/agent/tools/**`，Header: `Authorization: Bearer <INTERNAL_AI_TOKEN>`
- **服务间**：`physician-ai-service` 通过 Feign 调用 `http://<host>:8092/api/physician/internal/**`（Header: `X-Internal-Token: <INTERNAL_AI_TOKEN>`）

## 环境变量

在 `xikang-cloud-hospital/.env` 中确保配置：

```bash
INTERNAL_AI_TOKEN=<与 Dify 自定义工具一致的 token>
DIFY_* / DEEPSEEK_*  # 迁移至 physician-ai-service 进程
PHYSICIAN_SERVICE_URL=http://localhost:8092   # physician-ai-service Feign 目标
NACOS_DISCOVERY_ENABLED=true                    # 若使用 Nacos + 网关 lb:// 路由
```

## 部署步骤（灰度切换）

### 1. 构建

```bash
cd xikang-cloud-hospital
mvn clean package -pl physician-ai-service,physician-service,gateway-service -am -DskipTests
```

产物：
- `physician-ai-service/target/physician-ai-service-1.0.0.jar`
- `physician-service/target/physician-service-1.0.0.jar`
- `gateway-service/target/gateway-service-1.0.0.jar`

### 2. 先部署 physician-ai-service（8097）

与现有 Java 服务相同方式（systemd / nohup），示例：

```bash
java -jar physician-ai-service-1.0.0.jar \
  --server.port=8097 \
  --spring.config.import=optional:file:./.env
```

健康检查：`curl http://localhost:8097/actuator/health`

### 3. 联调（Dify 未切换前）

```bash
# Agent 工具探活
curl -X POST "http://<host>:8097/api/physician/agent/tools/get-patient?registerId=1" \
  -H "Authorization: Bearer $INTERNAL_AI_TOKEN" \
  -H "Content-Type: application/json"
```

确认 Feign 能访问 physician-service 内部接口（physician-service 须已运行且配置相同 `INTERNAL_AI_TOKEN`）。

### 4. 更新 Dify 配置

在 Dify Agent 的**自定义工具（Custom API）**中，将 base URL 从：

```
http://<host>:8092
```

改为：

```
http://<host>:8097
```

工具路径不变（仍为 `/api/physician/agent/tools/...`），Authorization Header 不变。

### 5. 部署瘦身后的 physician-service（8092）

替换旧 jar 并重启，端口仍为 8092。此时 8092 不再暴露 `/api/physician/agent/tools/**` 与 `/api/physician/ai/**`。

### 6. 更新并重启 gateway-service

确保 `application.yml` 中 `/api/physician/ai/**` 与 `/api/physician/copilot/**` 路由到 `physician-ai-service`（已在代码中配置）。

### 7. 回滚预案

若 Dify 回调异常：
1. 将 Dify base URL 改回 `http://<host>:8092`
2. 回滚 physician-service 至拆分前 jar（仍含 AI 代码的旧版本）
3. 停止 physician-ai-service（可选）

## 数据库

AI 自有表（`ai_exam_suggestion`、`physician_ai_chat_*`、`agent_*` 等）仍位于同一 PostgreSQL 实例；`physician-ai-service` 直连读写 AI 表，核心临床表经 Feign 访问 `physician-service`。

## 验证清单

- [ ] W1–W5 流水线、初步诊断
- [ ] Copilot SSE 会话
- [ ] Dify Agent read / draft / commit 工具全链路
- [ ] 审计日志 `agent_tool_audit_log`、待确认 `agent_pending_confirmation`
- [ ] 前端医生工作站正常（病历、开单不受 AI 服务影响）

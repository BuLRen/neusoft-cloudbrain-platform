# CT 影像查看器本地启动说明

## 服务组成

| 服务 | 端口 | 说明 |
|------|------|------|
| `ct-viewer-algo` | 8106 | Python 内网图像算法 worker |
| `ct-viewer-service` | 8099 | Java 对外 API（存储 + 编排 + 鉴权 + 审计） |
| `gateway-service` | 8080 | 网关 `/api/ct-viewer/**` |
| Redis | 6379 | volume 元数据 TTL（可与 ct-viewer 分机，配置 `REDIS_HOST` 为远程地址） |
| PostgreSQL | 5432/3307 | 用户鉴权查询 + CT 影像审计日志 |

## 1. 启动 Python 算法 worker

```bash
cd xikang-cloud-hospital/ct-viewer-algo
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m app.main
```

## 2. 数据库迁移

执行审计表迁移（首次启用鉴权/审计前必须执行）：

```bash
psql "$DATABASE_URL" -f xikang-cloud-hospital/docker/init-db/migrate_032_ct_imaging_audit_log.sql
```

## 3. 启动 Java 服务

确保 Redis、PostgreSQL 已运行，然后在 IDE 或命令行启动 `ct-viewer-service`（端口 8099）。

```bash
cd xikang-cloud-hospital
mvn -pl ct-viewer-service -am spring-boot:run
```

## 4. 启动网关与前端

- 启动 `gateway-service`（8080）
- 启动 `medtech-service`（8093，负责检查单影像绑定同步）
- 前端：`cd xikang-hospital-frontend && npm install && npm run dev`
- 登录医技账号，进入「医技管理 → CT 影像查看」
- 登录管理员账号，进入「管理员 → CT 影像审计」查看访问日志

## 环境变量（根目录 `.env`）

- `CT_VIEWER_ALGO_URL=http://127.0.0.1:8106`（与 Python 同机）
- `AI_CT_SERVICE_URL=http://127.0.0.1:8105`
- `LUNG_NODULE_SEG_SERVICE_URL=http://127.0.0.1:8222`
- `CT_VIEWER_WORK_DIR=./data/ct-viewer`
- `CT_VIEWER_VOLUME_TTL=7200`（秒）
- `REDIS_HOST=<远程 Redis 主机>`（Redis 可与 AI 服务器分机）
- `NACOS_DISCOVERY_ENABLED=true`（注册到 Nacos，供 gateway `lb://ct-viewer-service`）
- `NACOS_DISCOVERY_IP=<gateway 可达的 AI 服务器 IP>`（跨机部署时在 AI 服务器 `.env` 设置，对应 `application.yml` 的 `discovery.ip`）
- `SPRING_PROFILES_ACTIVE=local`（或 `remote`）
- `INTERNAL_SERVICE_TOKEN=<与 medtech-service 共用的内部令牌>`
- `JWT_SECRET=<与 auth/gateway 一致>`

## 鉴权说明

- `/api/ct-viewer/**`（除 `/health`）要求 `medtech` 或 `admin` 角色 JWT。
- volume 访问规则：
  - **admin**：可访问全部 volume
  - **未绑定 volume**（演示页上传）：仅上传者本人可访问
  - **已绑定检查单**：同科室医技可访问
- `/api/ct-viewer/internal/**` 仅供 `medtech-service` 服务间调用，使用 `X-Internal-Token` 鉴权。

## 审计日志

- 表名：`ct_imaging_audit_log`
- 记录上传、查看、滤波、分析、导出、绑定/解绑及 `ACCESS_DENIED` 拒绝访问。
- 管理员查询接口：`GET /api/ct-viewer/audit/logs`
- 前端页面：管理员 → CT 影像审计

## 健康检查

```bash
curl http://localhost:8106/health
curl http://localhost:8099/api/ct-viewer/health
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/ct-viewer/health
```

## 演示验证清单

1. 同科室医技 A 上传并绑定检查单 → A 可查看/滤波/分析
2. 同科室医技 B → 可访问已绑定到本科室检查单的 volume
3. 其他科室医技 C → 访问同一 volumeId 返回 403，审计表有 `ACCESS_DENIED`
4. 演示页未绑定 volume → 仅上传者本人可访问
5. admin → 可访问任意 volume，并可打开审计查询页
6. physician / patient → 调用 `/api/ct-viewer/**` 返回 403
7. 绑定/解绑 → 审计表有 `BIND` / `UNBIND` 记录

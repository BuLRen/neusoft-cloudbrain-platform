# CT 影像查看器本地启动说明

## 服务组成

| 服务 | 端口 | 说明 |
|------|------|------|
| `ct-viewer-algo` | 8106 | Python 内网图像算法 worker |
| `ct-viewer-service` | 8099 | Java 对外 API（存储 + 编排） |
| `gateway-service` | 8080 | 网关 `/api/ct-viewer/**` |
| Redis | 6379 | volume 元数据 TTL |

## 1. 启动 Python 算法 worker

```bash
cd xikang-cloud-hospital/ct-viewer-algo
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m app.main
```

## 2. 启动 Java 服务

确保 Nacos、Redis 已运行，然后在 IDE 或命令行启动 `ct-viewer-service`（端口 8099）。

```bash
cd xikang-cloud-hospital
mvn -pl ct-viewer-service -am spring-boot:run
```

## 3. 启动网关与前端

- 启动 `gateway-service`（8080）
- 前端：`cd xikang-hospital-frontend && npm install && npm run dev`
- 登录医技账号，进入「医技管理 → CT 影像查看」

## 环境变量（根目录 `.env`）

- `CT_VIEWER_ALGO_URL=http://127.0.0.1:8106`
- `CT_VIEWER_WORK_DIR=./data/ct-viewer`
- `CT_VIEWER_VOLUME_TTL=7200`（秒）

## 健康检查

```bash
curl http://localhost:8106/health
curl http://localhost:8099/api/ct-viewer/health
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/ct-viewer/health
```

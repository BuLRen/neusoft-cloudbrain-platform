# ai-catalog-service 部署说明

Dify W4/W5 工作流 HTTP 节点专用：疾病库 + 药品库只读检索。

## 构建

```bash
cd xikang-cloud-hospital
mvn -pl ai-catalog-service -am package -DskipTests
```

产物：`ai-catalog-service/target/ai-catalog-service-1.0.0.jar`

## 云服务器部署（与 Dify 同机）

一键脚本（需 SSH 公钥登录云主机）：

```bash
export REMOTE_SSH_USER=root REMOTE_SSH_HOST=43.139.102.203
export REMOTE_DEPLOY_PATH=/www/wwwroot/cz/xikang/ai-catalog
./deploy-to-cloud.sh
```

或手动步骤：

1. 上传 JAR 与 `.env` 到云主机（例如 `/www/wwwroot/cz/xikang/ai-catalog/`）

2. `.env` 最少包含：

```bash
DB_REMOTE_PASSWORD=<远程 PostgreSQL 密码>
INTERNAL_AI_TOKEN=<与 Dify HTTP 节点 Header 一致>
```

3. 启动：

```bash
cd /path/to/ai-catalog
nohup java -jar ai-catalog-service-1.0.0.jar > ai-catalog.log 2>&1 &
```

服务监听 **8098**，无需 Nacos。

4. **下线云侧 physician-service**（若仅用于 Dify HTTP 回调）

5. **更新 Dify HTTP 节点 URL**（路径不变，仅改端口）：

| 工作流 | URL |
|--------|-----|
| W4 | `http://172.17.0.1:8098/api/physician/internal/diseases/ai-search` |
| W5 | `http://172.17.0.1:8098/api/physician/internal/drugs/ai-search` |

Header：`Authorization: Bearer <INTERNAL_AI_TOKEN>`

6. 防火墙：8098 不对公网开放，仅本机/Docker 网桥访问。

## 冒烟测试

```bash
./smoke-test.sh
# 或指定云侧（SSH 隧道 / 云主机上执行）: ./smoke-test.sh http://127.0.0.1:8098
```

手动 curl：
curl -X POST http://127.0.0.1:8098/api/physician/internal/drugs/ai-search \
  -H "Authorization: Bearer $INTERNAL_AI_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"drugKeywords":["阿莫西林"],"limit":5}'
```

```bash
curl -X POST http://127.0.0.1:8098/api/physician/internal/diseases/ai-search \
  -H "Authorization: Bearer $INTERNAL_AI_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"diseaseKeywords":["肺炎"],"limit":10}'
```

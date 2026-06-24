# 本地数据库 → 服务器迁移指南

将本地 Docker PostgreSQL（`localhost:3307/xikang_hospital`）迁移到宝塔或其他服务器 PostgreSQL。

## 快速开始

```bash
cd xikang-cloud-hospital/docker

# 1. 配置（填写服务器 IP、密码等）
cp migrate-to-server.env.example migrate-to-server.env

# 2. 确保本地数据库已启动
docker compose up -d postgres

# 3. 从本地导出
chmod +x migrate-to-server.sh
./migrate-to-server.sh export

# 4. 导入到服务器（需本机可连服务器 5432，或在服务器上手动导入）
./migrate-to-server.sh import

# 5. 验证
./migrate-to-server.sh verify
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `migrate-to-server.sh` | 主导出/导入脚本 |
| `migrate-to-server.env.example` | 配置模板 |
| `migrate-to-server.env` | 实际配置（勿提交 git） |
| `post-import-fix-sequences.sql` | 导入后修复自增序列 |
| `backups/xikang_hospital_*.sql` | 导出的 dump 文件 |

## 连接信息对照

| 项目 | 本地（开发） | 服务器（宝塔典型） |
|------|-------------|-------------------|
| 主机 | `localhost` | 服务器 IP 或 `127.0.0.1` |
| 端口 | `3307` | `5432` |
| 数据库 | `xikang_hospital` | `xikang_hospital` |
| 用户 | `postgres` | 宝塔中创建的用户 |
| 密码 | `postgres123` | 自行设置 |

迁移完成后，修改各微服务 `application.yml`：

```yaml
url: jdbc:postgresql://服务器IP:5432/xikang_hospital
username: postgres
password: 你的服务器密码
```

## 项目连接服务器数据库

数据库配置统一在 **`config/database.yml`**，改一行即可切换，**IDE 直接 Run 生效，无需 shell 命令**。

详见 [config/README.md](../config/README.md)。

```yaml
# config/database.yml 顶部
spring:
  profiles:
    active: remote   # local = 本地 Docker，remote = 服务器
```

远程密码：`cp config/database-secrets.yml.example config/database-secrets.yml` 后填写。

## 宝塔手动导入（无法从本机直连服务器时）

1. `./migrate-to-server.sh export` 生成 `backups/xikang_hospital_*.sql`
2. 通过宝塔文件管理上传到服务器
3. 宝塔终端执行：

```bash
/www/server/pgsql/bin/psql -h 127.0.0.1 -p 5432 -U postgres -d xikang_hospital \
  -f /www/wwwroot/xikang-db/backups/xikang_hospital_YYYYMMDD_HHMMSS.sql

/www/server/pgsql/bin/psql -h 127.0.0.1 -p 5432 -U postgres -d xikang_hospital \
  -f /www/wwwroot/xikang-db/post-import-fix-sequences.sql
```

## 注意事项

- 目标库建议为**空库**；已有表时标准 pg_dump 导入会报「already exists」
- 导出使用 `--no-owner --no-acl`，兼容宝塔非 superuser 账号
- 服务器 PostgreSQL 建议 **16.x**，与本地版本一致
- **不要**对公网开放 5432；生产环境请修改默认密码
- 若已有快照 `init-db/xikang_hospital_snapshot_20260624.sql` 且数据未变，也可直接上传该文件导入，无需重新 export

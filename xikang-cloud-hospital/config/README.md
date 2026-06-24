# 数据库配置说明

全项目微服务共用数据库配置，**改 `config/database.yml` 一行即可切换本地/远程**。

## 切换步骤

1. 打开 `config/database.yml`
2. 修改顶部的 `spring.profiles.active`：
   - `local` → 本地 Docker（`localhost:3307`）
   - `remote` → 服务器 PostgreSQL
3. **重启**所有正在运行的微服务

## 远程库密码（二选一）

### 方式 A：`.env`（推荐）

在 `xikang-cloud-hospital/.env` 中添加：

```bash
DB_REMOTE_PASSWORD=你的宝塔数据库密码
```

各服务启动时会通过 `EnvLoader` 自动加载，无需其他命令。

### 方式 B：`database-secrets.yml`

```bash
cp config/database-secrets.yml.example config/database-secrets.yml
# 编辑 database-secrets.yml，填写 password
```

`database-secrets.yml` 已加入 `.gitignore`，请勿提交。

## 配置加载顺序

1. `classpath:config/database.yml`（common 模块，保底）
2. `config/database.yml`（项目根目录，**覆盖开关与连接地址**）
3. `config/database-secrets.yml`（可选，覆盖远程密码）

## 常见错误

| 报错 | 原因 | 处理 |
|------|------|------|
| `password authentication failed for user "xikang_hospital"` | `active: remote` 但未配置密码 | 在 `.env` 设置 `DB_REMOTE_PASSWORD` |
| 仍连 localhost:3307 | `active` 仍为 `local` | 改为 `remote` 并重启 |
| 本地库连接失败 | Docker 未启动 | `docker compose up -d postgres` |

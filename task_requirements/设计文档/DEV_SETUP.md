# 熙康云医院 - 开发环境搭建指南

> 版本：v1.0
> 日期：2026-05-25

---

## 1. 开发环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | LTS 版本 |
| Maven | 3.9+ | 构建工具 |
| Docker Desktop | 最新版 | 容器化 |
| VS Code / IDEA | - | 开发工具 |
| Git | - | 版本控制 |

---

## 2. Docker 环境启动

### 2.1 启动容器

```bash
cd xikang-cloud-hospital/docker
docker-compose up -d
```

### 2.2 验证服务状态

```bash
# 检查容器状态
docker-compose ps

# 验证 PostgreSQL (端口 3307)
psql -h localhost -p 3307 -U postgres -d xikang_hospital

# 验证 Nacos (端口 8848)
curl http://localhost:8848/nacos
```

### 2.3 Nacos 控制台

- 地址：http://localhost:8848/nacos
- 账号：nacos
- 密码：nacos

### 2.4 停止服务

```bash
docker-compose down
```

---

## 3. 数据库信息

| 项目 | 值 |
|------|------|
| 主机 | localhost |
| 端口 | 3307 |
| 数据库名 | xikang_hospital |
| 用户名 | postgres |
| 密码 | postgres123 |

---

## 4. 项目导入与编译

### 4.1 克隆项目

```bash
git clone https://github.com/BuLRen/neusoft-cloudbrain-platform.git
cd neusoft-cloudbrain-platform/xikang-cloud-hospital
```

### 4.2 编译项目

```bash
mvn clean compile -DskipTests
```

### 4.3 启动服务

按顺序启动：

```bash
# 1. 先启动 auth-service (依赖项)
cd auth-service
mvn spring-boot:run &

# 2. 再启动其他服务
cd ../gateway-service
mvn spring-boot:run &

# ... 其他服务同理
```

或使用 IDE 直接运行各服务的 Application 主类。

---

## 5. 服务端口一览

| 服务 | 端口 | 访问地址 |
|------|------|----------|
| gateway-service | 8080 | http://localhost:8080 |
| auth-service | 8081 | http://localhost:8081 |
| registration-service | 8091 | http://localhost:8091 |
| physician-service | 8092 | http://localhost:8092 |
| medtech-service | 8093 | http://localhost:8093 |
| pharmacy-service | 8094 | http://localhost:8094 |
| ai-gateway-service | 8100 | http://localhost:8100 |
| ai-triage-service | 8101 | http://localhost:8101 |
| ai-consult-service | 8102 | http://localhost:8102 |
| ai-diagnosis-service | 8103 | http://localhost:8103 |
| ai-pharmacy-service | 8104 | http://localhost:8104 |

| 组件 | 端口 | 说明 |
|------|------|------|
| PostgreSQL | 3307 | 数据库 |
| Nacos | 8848 | 服务发现/配置中心 |

---

## 6. 团队协作开发指南

### 6.1 分工方案

| 人员 | 负责模块 | 职责 |
|------|----------|------|
| 人员A | 诊疗流程 | 医生工作站、病历、检查/检验/处置申请、结果查看、确诊、处方、医生端 AI 嵌入 |
| 人员B | 入口与支撑流程 | 患者端、AI 导诊/预问诊/随访、挂号收费、医技执行、药房、管理员支撑页面 |

详细分工见：
- DEV_GUIDE_A.md（人员A开发指南）
- DEV_GUIDE_B.md（人员B开发指南）

### 6.2 并行开发策略

**阶段一：共同确定基础约定**

两人先共同确定项目结构、路由、登录鉴权、Layout、Axios 封装、API 类型、通用组件和 AI 结果卡片，避免后续 Cursor Agent 生成两套风格。

**阶段二：按业务链路并行开发**

围绕最小门诊闭环并行开发：
- 人员A负责医生接诊、病历、申请、结果查看、确诊、开方。
- 人员B负责导诊、预问诊、挂号、收费、医技执行、发药、随访。

**阶段三：联调**

每完成一个主流程节点就及时联调：
- 患者导诊、挂号、预问诊后，人员A确认医生端可以读取。
- 人员A开立检查/检验/处置申请后，人员B确认执行端可以读取并录入结果。
- 人员A开立处方后，人员B确认收费和药房发药可以继续流转。
- AI 能力按“先保证传统流程可运行，再补充 AI 增强”的顺序接入。

### 6.3 共享数据库

所有服务共用一个 PostgreSQL 数据库（xikang_hospital），
数据库表归属和跨模块读写规则见 DEV_GUIDE_A.md 与 DEV_GUIDE_B.md。

### 6.4 Git 工作流

```bash
# 1. 拉取最新代码
git pull origin master

# 2. 创建自己的分支
git checkout -b feature/xxx

# 3. 开发并提交
git add .
git commit -m "feat: xxx"

# 4. 推送到自己的分支
git push origin feature/xxx

# 5. 提 PR 合并到 master
```

---

## 7. 常见问题

### Q: Nacos 启动失败？
```bash
# 检查端口占用
netstat -ano | findstr 8848

# 停止占用进程或更改端口
```

### Q: 数据库连接失败？
```bash
# 确认 PostgreSQL 容器运行中
docker-compose ps postgres

# 进入容器检查
docker exec -it xikang-postgres psql -U postgres
```

### Q: 服务注册不上 Nacos？
```bash
# 检查 Nacos 是否正常运行
curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName=auth-service

# 检查 bootstrap.yml 中的 nacos 地址配置
```

---

## 8. 开发提醒

1. **先启动 Nacos 和 PostgreSQL**，再启动业务服务
2. **服务启动顺序**：PostgreSQL → Nacos → auth-service → gateway-service → 其他服务
3. **AI 服务**：需要配置 API Key 才能正常调用大模型
4. **联调前**：确保双方都基于最新的接口文档开发

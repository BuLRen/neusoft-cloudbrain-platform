# 熙康云医院（东软云医院 · AI 融合版）

> 基于微服务架构的智慧门诊信息系统，在传统 HIS 门诊流程之上融合 AI 导诊、预问诊、辅助诊断、智能排班、CT 影像分析与临床 Copilot 等能力。

---

## 目录

- [项目简介](#项目简介)
- [核心业务流程](#核心业务流程)
- [系统架构](#系统架构)
- [仓库结构](#仓库结构)
- [技术栈](#技术栈)
- [后端微服务](#后端微服务)
- [AI 与算法服务](#ai-与算法服务)
- [前端应用](#前端应用)
- [数据库](#数据库)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [端口一览](#端口一览)
- [相关文档](#相关文档)

---

## 项目简介

熙康云医院是一套面向门诊场景的医院信息系统（HIS），采用**前后端分离 + 微服务**架构。系统在保留传统门诊业务逻辑、数据库主体与角色权限的前提下，将 AI 能力以「可插拔」方式嵌入各业务环节，形成 **「传统 HIS + AI 智能中台」** 的融合架构。

### 设计原则

| 原则 | 说明 |
|------|------|
| 业务不变，AI 嵌入 | 原有门诊流程与数据模型保持不变，AI 作为辅助决策层叠加 |
| 高内聚低耦合 | 每个微服务独立负责一个业务域，通过 REST API 通信 |
| AI 能力可插拔 | AI 服务独立部署，未配置时传统流程仍可正常运行 |
| 渐进式扩展 | 支持新增服务而不影响现有模块 |

### 用户角色

| 角色 | 说明 |
|------|------|
| **患者** | AI 导诊、预问诊、挂号、就诊记录、处方与账单、随访 |
| **挂号收费员** | 窗口挂号、报到、收费、退费 |
| **门诊医生** | 待诊接诊 → 病历 → 检查检验申请 → 结果查看 → 确诊 → 处方；集成 AI Copilot |
| **医技人员** | 检查 / 检验 / 处置执行、结果录入、CT 影像查看 |
| **药房人员** | 发药、退药、库存与药品字典管理 |
| **随访人员** | 疗效评估、医患沟通、随访记录 |
| **管理员** | 分诊台、智能排班、人员与基础数据、运营中心、支付账单、CT 审计 |

---

## 核心业务流程

系统覆盖完整的门诊闭环：

```
患者导诊/挂号 → 预问诊 → 医生接诊 → 开立检查/检验/处置
    → 缴费 → 医技执行与结果录入 → 医生查看结果 → 门诊确诊
    → 开立处方 → 收费 → 药房发药 → 用药随访
```

AI 能力贯穿其中，例如：

- **导诊阶段**：症状分析、科室推荐、语音问诊（RAG 知识库）
- **预问诊阶段**：多轮对话采集病史，生成问诊摘要供医生参考
- **诊疗阶段**：初步诊断（Dify W1–W4 工作流）、检查推荐、结果解读、辅助确诊、智能荐药
- **管理阶段**：AI 月度排班、请假处理、号源预警
- **影像阶段**：CT DICOM/NRRD 加载、滤波、伪影检测、3D 体渲染

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            用户层                                        │
│   患者端  │  挂号收费  │  门诊医生  │  医技  │  药房  │  随访  │  管理员   │
└─────┬──────────┬──────────┬──────────┬──────────┬──────────┬───────────┘
      │          │          │          │          │          │
      ▼          ▼          ▼          ▼          ▼          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  前端展示层   xikang-hospital-frontend  (Vue 3 + Vite, :5173)            │
│  路由 / 权限 / Pinia 状态 / Axios API / Element Plus / ECharts / vtk.js │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ HTTP /api → 代理到网关
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  网关层   gateway-service (:8080)                                      │
│  JWT 鉴权 │ 路由转发 │ CORS │ 负载均衡 (Nacos) │ 长超时 (AI 任务)        │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
       ┌───────────────────────┼───────────────────────┐
       ▼                       ▼                       ▼
┌──────────────┐    ┌──────────────────────┐    ┌──────────────────┐
│  基础服务     │    │  核心业务服务          │    │  AI 服务          │
│  auth        │    │  registration          │    │  ai-triage        │
│              │    │  physician             │    │  ai-consult       │
│              │    │  physician-ai          │    │  ai-diagnosis     │
│              │    │  medtech               │    │  ai-pharmacy      │
│              │    │  pharmacy              │    │  ai-gateway       │
│              │    │  payment               │    │  ai-catalog *     │
│              │    │  schedule              │    │                   │
│              │    │  ct-viewer             │    │                   │
└──────────────┘    └──────────────────────┘    └──────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Python 算法服务（内网 HTTP，不经网关）                                    │
│  ai-ct-service (:8105)  │  ct-viewer-algo (:8106)  │  glucose-prediction  │
└─────────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  基础设施   PostgreSQL (:3307)  │  Nacos (:8848)  │  Redis (:6379)       │
└─────────────────────────────────────────────────────────────────────────┘

* ai-catalog-service 供 Dify 工作流 HTTP 节点直连，不经过 API 网关
```

### 请求流转

1. 浏览器访问 `http://localhost:5173`，Vite 将 `/api` 代理到 `gateway-service:8080`
2. 网关校验 JWT，按路径前缀路由到对应微服务（`lb://service-name`）
3. 业务服务通过 MyBatis 访问 PostgreSQL；AI 服务调用大模型 API 或 Dify 工作流
4. 影像类请求由 `ct-viewer-service` 转发至 Python 算法 worker

---

## 仓库结构

```
neusoft-cloudbrain-platform/
├── xikang-cloud-hospital/          # 后端微服务（Maven 多模块）
│   ├── common/                     # 公共库：统一响应、异常、JWT 工具
│   ├── gateway-service/            # API 网关
│   ├── auth-service/               # 认证鉴权、患者账户
│   ├── registration-service/       # 挂号、叫号、分诊台
│   ├── physician-service/          # 门诊医师业务
│   ├── physician-ai-service/       # 医师 AI（Dify W1–W5、Copilot）
│   ├── medtech-service/            # 医技执行、随访、CT 推理
│   ├── pharmacy-service/           # 药房管理
│   ├── payment-service/            # 支付订单
│   ├── schedule-service/           # 排班与 Dify 智能排班
│   ├── ct-viewer-service/          # CT 影像查看与审计
│   ├── ai-gateway-service/         # AI 路由网关
│   ├── ai-triage-service/          # AI 导诊
│   ├── ai-consult-service/         # AI 预问诊
│   ├── ai-diagnosis-service/       # AI 诊断
│   ├── ai-pharmacy-service/        # AI 药学
│   ├── ai-catalog-service/         # Dify 疾病/药品库检索
│   ├── ai-ct-service/              # CT 伪影检测（Python / FastAPI）
│   ├── ct-viewer-algo/             # CT 图像处理 worker（Python）
│   ├── glucose-ml/                 # 血糖预测模型训练
│   ├── glucose-prediction-service/ # 血糖推理服务（Python）
│   ├── ct-dicom-viewer/            # 独立 CT DICOM 查看器（参考实现）
│   ├── docker/                     # Docker Compose、数据库迁移脚本
│   └── docs/                       # 后端专项文档
│
├── xikang-hospital-frontend/       # 主前端（Vue 3 + TypeScript + Vite）
│   └── src/
│       ├── app/                    # 路由、布局、权限、全局状态
│       ├── shared/                 # API 层、通用组件、工具
│       └── modules/                # 业务模块（按角色划分）
│           ├── patient/            # 患者端
│           ├── registration/       # 挂号收费
│           ├── physician/          # 门诊诊疗
│           ├── medtech/            # 医技 + CT 查看器 + 随访
│           ├── pharmacy/           # 药房
│           └── admin/              # 管理员
│
├── wechat_code/                    # 微信小程序（脚手架阶段）
├── task_requirements/              # 需求与设计文档
├── docs/                           # 项目级补充文档
└── public/                         # 公共 SQL 脚本
```

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 3.2.4 | 应用框架 |
| Spring Cloud | 2023.0.0 | 微服务生态 |
| Spring AI | 1.0.0 | 大模型集成 |
| MyBatis | 3.5.16 | ORM |
| PostgreSQL | 16 | 主数据库 |
| Nacos | 2.4.3 | 服务发现与配置 |
| JWT (jjwt) | 0.12.5 | 认证 Token |
| Redis | — | 网关限流、会话缓存 |

### 前端

| 技术 | 用途 |
|------|------|
| Vue 3 + TypeScript | UI 框架 |
| Vite 8 | 构建工具 |
| Vue Router 4 | 路由与权限守卫 |
| Pinia | 状态管理 |
| Element Plus | 组件库 |
| Axios | HTTP 客户端 |
| ECharts | 数据可视化 |
| vtk.js + nrrd-js | CT 3D 体渲染 |

### AI / 算法

| 技术 | 用途 |
|------|------|
| DeepSeek / 通义千问 | 大语言模型 |
| Dify | 工作流编排（W1–W5、排班、Copilot） |
| FastAPI + PyTorch + MONAI | CT 伪影检测 |
| FastAPI + SimpleITK | CT 图像处理 |
| PyTorch + ONNX Runtime | 血糖预测 |
| PgVector | 导诊 RAG 知识库 |

---

## 后端微服务

Maven 父工程 `xikang-cloud-hospital/pom.xml` 管理 **16 个可运行服务模块** + `common` 公共库。

### 基础设施

| 服务 | 端口 | 职责 |
|------|------|------|
| `gateway-service` | 8080 | API 网关：路由、JWT 鉴权、CORS、负载均衡 |
| `auth-service` | 8081 | 登录/登出、Token 刷新、验证码、患者账户与余额 |

### 核心业务

| 服务 | 端口 | 职责 | 主要 API 前缀 |
|------|------|------|---------------|
| `registration-service` | 8091 | 挂号、退号、报到、叫号 SSE、分诊台、统计 | `/api/registration/**` |
| `physician-service` | 8092 | 病历、处方、检查检验开立、临床档案、叫号 | `/api/physician/**` |
| `medtech-service` | 8093 | 检查/检验/处置执行、CT 推理、随访全链路 | `/api/medtech/**` |
| `pharmacy-service` | 8094 | 药品目录、库存、发药、用药指导 | `/api/pharmacy/**` |
| `schedule-service` | 8095 | 排班计划、号源、请假、Dify 智能排班 | `/api/schedule/**` |
| `payment-service` | 8096 | 支付订单、费用项、内部 Feign 接口 | `/api/payment/**` |
| `physician-ai-service` | 8097 | Dify W1–W5 工作流、临床 Copilot、Agent 工具 | `/api/physician/ai/**` |
| `ct-viewer-service` | 8099 | CT DICOM/NRRD 加载、滤波、伪影分析、审计 | `/api/ct-viewer/**` |

### AI 服务

| 服务 | 端口 | 职责 | 主要 API 前缀 |
|------|------|------|---------------|
| `ai-gateway-service` | 8100 | AI 统一路由与状态查询 | `/api/ai/route/**` |
| `ai-triage-service` | 8101 | 智能导诊、科室推荐、RAG、语音问诊 | `/api/ai/triage/**` |
| `ai-consult-service` | 8102 | AI 预问诊（SSE 流式） | `/api/ai/consult/**` |
| `ai-diagnosis-service` | 8103 | 报告解读、检查分析、诊断建议 | `/api/ai/diagnosis/**` |
| `ai-pharmacy-service` | 8104 | 用药指导、处方审核、随访计划 | `/api/ai/pharmacy/**` |
| `ai-catalog-service` | 8098 | Dify HTTP 节点：疾病/药品库检索（**不经网关**） | `/api/physician/internal/**` |

### 统一响应格式

所有 Java 服务返回统一结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务异常 |
| 503 | AI 服务不可用 |

---

## AI 与算法服务

以下服务**不在 Maven 父 POM 中**，以独立 Python 进程运行，由 Java 服务通过 HTTP 内网调用。

| 服务 | 端口 | 技术栈 | 职责 |
|------|------|--------|------|
| `ai-ct-service` | 8105 | FastAPI + PyTorch + MONAI | 头部 CT 伪影检测（3D U-Net） |
| `ct-viewer-algo` | 8106 | FastAPI + SimpleITK | CT 格式转换、滤波、导出 |
| `glucose-prediction-service` | 可配置 | FastAPI + ONNX Runtime | 血糖 LSTM 预测推理 |
| `glucose-ml` | — | PyTorch | 离线模型训练管线 |
| `ct-dicom-viewer` | 8000 / 5174 | Flask + Vue 3 + vtk.js | 独立 CT 查看工作台（参考实现） |

### 医师 AI 工作流（Dify）

`physician-ai-service` 通过 Dify 编排临床工作流：

| 工作流 | 步骤 | 能力 |
|--------|------|------|
| 初步诊断 | — | 症状 → 初步判断与疾病列表 |
| W1 | 病历结构化 | 自由文本 → 标准病历字段 |
| W2 | 检查推荐 | 基于病历推荐检查/检验项目 |
| W3 | 结果解读 | 检查检验结果 → 临床印象与指标分析 |
| W4 | 门诊确诊 | 综合信息 → 诊断与 ICD 编码 |
| W5 | 智能荐药 | 诊断 → 处方药品推荐 |
| Copilot | AI 助手 | SSE 流式对话，支持 Agent 工具调用 |

未配置 Dify 时，系统自动回退到内置 `FallbackWorkflowEngine`。

---

## 前端应用

主前端位于 `xikang-hospital-frontend/`，按角色组织业务模块。

### 模块与路由

| 模块 | 路径前缀 | 主要页面 |
|------|----------|----------|
| **患者端** | `/patient/*` | 首页、AI 导诊、挂号、预问诊、就诊记录、处方、账单、随访 |
| **挂号收费** | `/registration` | 窗口挂号、收费、退费 |
| **门诊诊疗** | `/physician/*` | 待诊接诊 → 病历 → 申请 → 结果 → 确诊 → 处方、AI 助手 |
| **医技管理** | `/medtech/*` | 申请队列、检查/检验/处置执行、结果录入 |
| **CT 影像** | `/medtech/ct-exam`、`/physician/ct-exam` | vtk.js 3D 体渲染阅片 |
| **随访系统** | `/follow-up/*` | 疗效评估、医患沟通、随访记录 |
| **药房管理** | `/pharmacy/*` | 发药、库存、流水、药品字典、处方追溯 |
| **管理员** | `/admin/*` | 分诊台、智能排班、人员、基础资料、运营中心、支付账单、CT 审计 |
| **公共** | `/calling-board`、`/test-checkin` | 候诊叫号大屏、报到机（免登录） |

### 前端目录约定

```
src/
├── app/          # 路由 (routes.ts)、权限守卫、布局 (AppShell)、Pinia stores
├── shared/       # Axios 封装、API modules、通用组件、类型定义、工具函数
└── modules/      # 业务页面，按角色分目录，一个页面一个组件
```

### 权限模型

- 基于 JWT + 角色（`admin`、`physician`、`registration`、`medtech`、`pharmacy`、`patient`、`followup`）
- 路由 `meta.roles` 控制菜单可见性与页面访问
- 门诊诊疗步骤要求先选择患者（`requiresEncounter`）

### 开发命令

```bash
cd xikang-hospital-frontend
npm install
npm run dev          # 开发服务器 http://localhost:5173
npm run type-check   # TypeScript 类型检查
npm run build        # 生产构建
```

---

## 数据库

- **引擎**：PostgreSQL 16
- **库名**：`xikang_hospital`
- **迁移脚本**：`xikang-cloud-hospital/docker/init-db/`（30+ 个 `migrate_*.sql`）
- **初始化**：Docker Compose 首次启动时自动执行

### 主要数据域

| 域 | 核心表 |
|----|--------|
| 用户与患者 | `users`、`patient`、`employee` |
| 挂号 | `register`、`department`、`triage_desk_record` |
| 临床 | `medical_record`、`check_request`、`inspection_request`、`prescription` |
| 费用 | `expense_record`、`patient_balance_transaction` |
| 药房 | `drug_info`、`drug_stock`、`dispensing`、`medication_guide` |
| 排班 | `schedule_plan`、`doctor_schedule`、`leave_request` |
| AI 记录 | `ai_triage_record`、`ai_consultation_record`、`ai_diagnosis_suggestion` |
| 随访 | `follow_up_*` 系列表 |
| 医师 AI | `physician_ai_chat_session`、`agent_tool_audit_log` |
| CT 影像 | `ct_imaging_audit_log`、`check_request.imaging_*` |

---

## 快速开始

### 环境要求

| 软件 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| Docker Desktop | 最新版 |
| Python | 3.10+（算法服务，可选） |

### 1. 启动基础设施

```bash
cd xikang-cloud-hospital/docker
docker compose up -d
```

验证：

```bash
docker compose ps
# PostgreSQL: localhost:3307  (用户 postgres / 密码 postgres123)
# Nacos:      http://localhost:8848/nacos  (nacos / nacos)
```

### 2. 配置环境变量

```bash
cd xikang-cloud-hospital
cp .env.example .env
# 编辑 .env，至少配置数据库 profile 与 AI API Key
```

本地开发建议设置：

```env
SPRING_PROFILES_ACTIVE=local
DEEPSEEK_API_KEY=your-key
DIFY_ENABLED=true
DIFY_BASE_URL=http://your-dify-host
DIFY_API_KEY=app-xxx
```

### 3. 编译并启动后端

```bash
cd xikang-cloud-hospital
mvn clean install -DskipTests

# 推荐启动顺序
# 1. auth-service (8081)
# 2. gateway-service (8080)
# 3. 其他业务服务按需启动
cd auth-service && mvn spring-boot:run
```

也可在 IDE 中直接运行各服务的 `*Application.java` 主类。

### 4. 启动前端

```bash
cd xikang-hospital-frontend
npm install
npm run dev
```

访问 http://localhost:5173 ，API 请求通过 Vite 代理到 `http://localhost:8080`。

### 5. 启动算法服务（可选）

```bash
# CT 伪影检测
cd xikang-cloud-hospital/ai-ct-service
pip install -r requirements.txt
python -m app.main

# CT 图像处理
cd xikang-cloud-hospital/ct-viewer-algo
pip install -r requirements.txt
uvicorn app.main:app --port 8106
```

---

## 配置说明

### 中心配置文件

`xikang-cloud-hospital/.env`（从 `.env.example` 复制），各服务通过 `spring.config.import` 链式加载。

| 变量 | 说明 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `local`（Docker PG :3307）或 `remote`（远程 PG :5432） |
| `DB_*` / `DB_LOCAL_*` | 数据库连接信息 |
| `DEEPSEEK_API_KEY` / `BASE_URL` / `MODEL` | 大模型配置 |
| `DIFY_ENABLED` / `DIFY_BASE_URL` / `DIFY_API_KEY` | Dify 工作流 |
| `INTERNAL_AI_TOKEN` | 服务间内部鉴权 |
| `NACOS_DISCOVERY_ENABLED` | Nacos 注册开关 |

### 服务专属环境变量

| 变量 | 使用服务 |
|------|----------|
| `CT_VIEWER_ALGO_URL` | ct-viewer-service → ct-viewer-algo |
| `AI_CT_SERVICE_URL` | ct-viewer-service / medtech-service → ai-ct-service |
| `GLUCOSE_PREDICTION_BASE_URL` | medtech-service → 血糖推理 |
| `DIFY_API_KEY_SCHEDULE` | schedule-service 智能排班 |
| `TRIAGE_RAG_ENABLED` | ai-triage-service RAG 知识库 |

### 前端配置

| 文件 | 说明 |
|------|------|
| `.env.development` | `VITE_API_BASE_URL=/api` |
| `vite.config.ts` | 开发代理：`/api` → `localhost:8080`，超时 660s |

---

## 端口一览

| 端口 | 服务 / 组件 |
|------|---------------|
| 5173 | 主前端 (Vite dev) |
| 3307 | PostgreSQL (Docker 映射) |
| 6379 | Redis |
| 8080 | gateway-service |
| 8081 | auth-service |
| 8091 | registration-service |
| 8092 | physician-service |
| 8093 | medtech-service |
| 8094 | pharmacy-service |
| 8095 | schedule-service |
| 8096 | payment-service |
| 8097 | physician-ai-service |
| 8098 | ai-catalog-service（不经网关） |
| 8099 | ct-viewer-service |
| 8100 | ai-gateway-service |
| 8101 | ai-triage-service |
| 8102 | ai-consult-service |
| 8103 | ai-diagnosis-service |
| 8104 | ai-pharmacy-service |
| 8105 | ai-ct-service (Python) |
| 8106 | ct-viewer-algo (Python) |
| 8848 | Nacos |

---

## 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 系统架构设计 | `task_requirements/设计文档/01_系统架构设计文档.md` | 架构蓝图与技术决策 |
| 业务需求（AI 融合版） | `task_requirements/东软云医院系统设计文档（AI加入之后）.md` | 完整业务需求与原型 |
| 开发环境搭建 | `task_requirements/设计文档/DEV_SETUP.md` | 详细环境配置与联调指南 |
| 开发指南 A（医生端） | `task_requirements/设计文档/DEV_GUIDE_A.md` | 门诊诊疗模块开发 |
| 开发指南 B（支撑流程） | `task_requirements/设计文档/DEV_GUIDE_B.md` | 患者端、挂号、医技、药房 |
| API 接口文档 | `task_requirements/设计文档/02_API接口文档.md` | REST API 契约 |
| 数据库设计 | `task_requirements/设计文档/03_数据库详细设计文档.md` | 表结构与关系 |
| 后端微服务说明 | `xikang-cloud-hospital/README.md` | 服务模块与 Maven 命令 |
| 前端说明 | `xikang-hospital-frontend/README.md` | 前端目录约定与命令 |
| 医师 AI 部署 | `xikang-cloud-hospital/docs/physician-ai-service-deployment.md` | Dify 工作流部署 |
| 支付服务设计 | `xikang-cloud-hospital/docs/payment-service-design.md` | 支付联动方案 |

---

## 常用命令

```bash
# 编译整个后端
cd xikang-cloud-hospital && mvn clean install -DskipTests

# 编译单个模块及其依赖
mvn clean compile -pl physician-service -am

# 前端类型检查 + 构建
cd xikang-hospital-frontend && npm run build

# Docker 基础设施
cd xikang-cloud-hospital/docker && docker compose up -d
```

---

## 注意事项

1. **AI 服务依赖外部 API**：需配置 `DEEPSEEK_API_KEY` 或 Dify 相关变量，否则 AI 功能回退到内置模拟
2. **JWT 密钥**：生产环境务必更换 `auth-service` 的 JWT 密钥
3. **数据库配置**：`SPRING_PROFILES_ACTIVE=local` 使用 Docker PG（:3307），`remote` 使用远程数据库
4. **长超时**：网关与前端代理均配置 660s 超时，以覆盖 AI 长任务
5. **Nacos**：本地全栈开发建议开启 `NACOS_DISCOVERY_ENABLED=true`
6. **敏感信息**：`.env` 文件已加入 `.gitignore`，切勿提交真实密钥

---

*东软云医院 · 熙康云医院平台 — 传统 HIS + AI 智能中台*

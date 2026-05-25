# 人员A - 诊疗流程开发指南

> 版本：v2.0  
> 日期：2026-05-25  
> 分工原则：以业务主线为主，以端为辅

---

## 1. 开发概述

人员A负责门诊流程中“医生接诊到开方”的诊疗工作流，重点保证医生能够顺畅完成接诊、病历、检查/检验申请、结果查看、确诊和处方开立。

本分工不代表人员A的模块比其他模块更重要，只是按照业务链路划定主要负责范围。人员A和人员B应共同维护接口契约、通用组件、基础设施和端到端主流程。

### 1.1 负责范围

| 类型 | 负责内容 |
|------|----------|
| 主要后端服务 | `physician-service` |
| 主要前端模块 | 医生工作站、医生端 AI 嵌入组件 |
| 主要业务链路 | 医生接诊 → 写病历 → 开检查/检验/处置 → 查看结果 → 确诊 → 开处方 |
| 主要 AI 能力 | AI 预问诊摘要展示、AI 病历生成、AI 检查推荐、AI 结果分析展示、AI 诊断推荐、AI 处方审核提示 |
| 主要数据表 | `medical_record`、`medical_record_disease`、`disease`、`prescription`，以及医生创建的 `check_request`、`inspection_request`、`disposal_request` |

### 1.2 不单独负责的范围

以下内容由人员B主要负责，人员A按接口约定进行联调：

- 患者端 AI 导诊、预问诊、随访页面
- 挂号、退号、收费、退费、费用记录
- 检查/检验/处置执行端
- 药房发药、退药、药库管理
- 管理员支撑页面、医生排班、AI 分诊台

---

## 2. 页面与功能清单

### 2.1 医生工作站页面

| 页面 | 说明 | 优先级 |
|------|------|--------|
| 患者查看 | 查看当前医生待诊患者、患者基本信息、挂号信息、预问诊摘要 | P0 |
| 病历首页 | 书写主诉、现病史、既往史、体格检查、初步诊断 | P0 |
| 检查申请 | 查询检查项目，开立检查申请，可展示 AI 检查推荐 | P0 |
| 检验申请 | 查询检验项目，开立检验申请，可展示 AI 检验推荐 | P0 |
| 处置申请 | 开立处置项目 | P1 |
| 检查结果查看 | 查看检查结果和 AI 分析内容 | P0 |
| 检验结果查看 | 查看检验结果和 AI 分析内容 | P0 |
| 门诊确诊 | 选择疾病诊断，保存确诊结果，可展示 AI 诊断推荐 | P0 |
| 开立处方 | 查询药品，开立处方，可展示 AI 处方审核结果 | P0 |
| 看诊记录 | 查询历史就诊、历史病历、历史处方 | P1 |
| 费用查询 | 查看当前患者待缴费和已缴费状态 | P1 |

### 2.2 医生端 AI 嵌入点

| 嵌入位置 | AI 能力 | 调用方向 |
|----------|---------|----------|
| 患者查看 | 展示 AI 预问诊摘要 | 读取 `ai-consult-service` 结果 |
| 病历首页 | AI 自动生成病历草稿 | 调用 `/api/ai/consult/summary` 或病历生成接口 |
| 检查/检验申请 | AI 推荐检查/检验方案 | 调用 `/api/ai/diagnosis/suggest` |
| 检查/检验结果 | AI 结果分析展示 | 读取 `/api/ai/diagnosis/interpret` 结果 |
| 门诊确诊 | AI 辅助诊断推荐 | 调用 `/api/ai/diagnosis/suggest` |
| 开立处方 | AI 处方审核提示 | 调用 `/api/ai/pharmacy/review` |

---

## 3. 后端服务开发指南

### 3.1 `physician-service` 职责

`physician-service` 主要负责医生工作站相关能力：

- 获取医生待诊患者列表
- 获取患者挂号信息、基础信息、预问诊摘要
- 创建和更新电子病历
- 创建检查申请、检验申请、处置申请
- 查询检查/检验/处置结果
- 保存门诊确诊结果
- 查询药品并开立处方
- 查询历史看诊记录
- 对接医生端需要的 AI 能力

### 3.2 主要 API

以 `02_API接口文档.md` 为准，人员A重点实现或联调以下接口：

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/physician/patients` | 获取当前医生待诊患者列表 |
| GET | `/api/physician/patient-stats` | 获取看诊统计 |
| POST | `/api/physician/medical-record` | 创建病历 |
| PUT | `/api/physician/medical-record/{id}` | 更新病历 |
| GET | `/api/physician/medical-record` | 按挂号 ID 获取病历详情 |
| GET | `/api/physician/medical-technologies` | 查询医技项目列表 |
| POST | `/api/physician/check-request` | 提交检查申请 |
| POST | `/api/physician/inspection-request` | 提交检验申请 |
| POST | `/api/physician/disposal-request` | 提交处置申请 |
| GET | `/api/physician/check-results` | 获取检查结果 |
| GET | `/api/physician/inspection-results` | 获取检验结果 |
| POST | `/api/physician/diagnosis` | 提交确诊结果 |
| GET | `/api/physician/drugs` | 查询药品列表 |
| GET | `/api/physician/drugs/{id}` | 获取药品详情 |
| POST | `/api/physician/prescription` | 开立处方 |
| DELETE | `/api/physician/prescription/{id}` | 删除处方药品 |

### 3.3 数据表访问规则

| 表名 | 人员A操作 | 说明 |
|------|-----------|------|
| `medical_record` | INSERT / UPDATE / SELECT | 病历首页 |
| `medical_record_disease` | INSERT / DELETE / SELECT | 病历与诊断关联 |
| `disease` | SELECT | 疾病字典 |
| `prescription` | INSERT / UPDATE / SELECT | 医生开立处方，药房读取 |
| `check_request` | INSERT / SELECT | 医生创建，医技执行端更新 |
| `inspection_request` | INSERT / SELECT | 医生创建，医技执行端更新 |
| `disposal_request` | INSERT / SELECT | 医生创建，处置执行端更新 |

> 注意：检查、检验、处置申请由人员A负责创建，由人员B负责执行和结果录入。字段状态必须提前约定，避免一个页面写入后另一个页面无法识别。

---

## 4. 与人员B的接口协作

### 4.1 人员A依赖人员B的数据

| 来源 | 数据 | 用途 |
|------|------|------|
| `registration-service` | 挂号患者、挂号状态、患者基础信息 | 医生待诊列表和接诊 |
| `registration-service` | 费用状态、待缴费项目 | 医生端费用查询 |
| `medtech-service` | 检查/检验/处置执行状态、结果 | 医生查看结果和确诊 |
| `pharmacy-service` | 发药状态、退药状态 | 医生查看处方后续状态 |
| 患者端 AI | 预问诊摘要、主诉、病史、过敏史 | 病历首页和接诊判断 |
| AI 导诊 | 推荐科室、推荐医生、风险等级 | 患者查看和辅助判断 |

### 4.2 人员A提供给人员B的数据

| 去向 | 数据 | 用途 |
|------|------|------|
| `medtech-service` | 检查申请、检验申请、处置申请 | 执行端查看和录入结果 |
| `registration-service` | 待收费项目、费用明细 | 收费窗口缴费 |
| `pharmacy-service` | 处方明细、诊断结果、处方状态 | 药房发药和退药 |
| AI 随访 | 诊断结果、处方信息、用药说明 | 生成随访计划 |

### 4.3 必须提前约定的字段

- `registerId`：贯穿挂号、接诊、检查、收费、发药、随访
- `patientId`：患者唯一标识
- `physicianId`：医生唯一标识
- `checkRequestId` / `inspectionRequestId` / `disposalRequestId`
- `prescriptionId`
- 申请状态：待缴费、待执行、执行中、已完成、已作废
- 处方状态：待缴费、已缴费、待发药、已发药、已退药
- AI 结果状态：未调用、生成中、成功、失败、已过期

---

## 5. 推荐开发顺序

### 第0阶段：共同搭建基础

人员A和人员B共同完成：

- Vue3 + Element Plus 项目结构
- 路由和菜单结构
- 登录鉴权
- Layout 布局
- Axios 封装
- API 类型约定
- 通用表格、表单、弹窗组件
- AI 结果卡片组件
- 患者信息头部组件
- 后端统一响应、异常处理、基础配置

### 第1阶段：传统诊疗流程可运行

人员A优先完成：

1. 医生待诊患者列表
2. 患者详情与挂号信息展示
3. 病历首页保存
4. 检查/检验申请创建
5. 检查/检验结果查看
6. 门诊确诊
7. 开立处方

该阶段目标是让医生端可以接住人员B提供的挂号患者，并把申请、处方继续交给人员B负责的执行端和药房端。

### 第2阶段：补充医生端 AI 能力

在传统流程可演示后，再补充：

- AI 预问诊摘要展示
- AI 病历生成
- AI 检查/检验推荐
- AI 检查结果分析展示
- AI 诊断推荐
- AI 处方审核提示

### 第3阶段：完善体验和边界状态

- 表单校验
- 空状态和错误状态
- AI 服务不可用时的降级提示
- 历史记录查询
- 费用、发药、检查结果的跨模块状态展示

---

## 6. 共享文件修改规则

以下文件或模块属于共享区域，修改前必须和人员B沟通：

```txt
src/router/**
src/stores/**
src/api/request.*
src/layouts/**
src/components/common/**
src/types/**
common/**
gateway-service/**
auth-service/**
```

建议约定：

- 路由和菜单由一人统一维护，另一个人提交页面路径需求。
- 通用组件先写清楚 props、事件和示例，再给另一个人使用。
- API 类型字段一旦用于联调，不随意重命名。
- Cursor Agent 修改共享文件前，先说明改动范围。

---

## 7. 联调检查清单

人员A提交联调前，应确认：

- 医生登录后能看到自己负责的待诊患者
- 病历可以保存并按 `registerId` 查询
- 检查/检验/处置申请可以被人员B的执行端读取
- 检查/检验结果录入后医生端能看到
- 确诊后可以开处方
- 处方能被药房端读取
- AI 不可用时，医生端传统流程仍可继续

---

## 8. 验收标准

人员A负责的部分完成后，应能演示：

患者完成导诊、挂号和预问诊后，医生进入患者列表，查看患者信息和预问诊摘要，完成病历、检查/检验申请、查看结果、门诊确诊、处方开立，并在相关页面看到 AI 辅助内容。

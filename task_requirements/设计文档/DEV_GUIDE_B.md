# 人员B - 入口与支撑流程开发指南

> 版本：v2.0  
> 日期：2026-05-25  
> 分工原则：以业务主线为主，以端为辅

---

## 1. 开发概述

人员B负责门诊流程中“患者进入系统到收费、检查执行、发药、随访”的入口与支撑工作流，重点保证患者端、挂号收费、医技执行、药房和管理员支撑功能能够串成完整闭环。

人员A和人员B是平级协作关系，只是按照业务链路划定主要负责范围。人员B负责的流程决定了患者能否进入诊疗流程、医生申请能否被执行、处方能否完成发药以及后续随访能否形成闭环。

### 1.1 负责范围

| 类型 | 负责内容 |
|------|----------|
| 主要后端服务 | `registration-service`、`medtech-service`、`pharmacy-service` |
| 主要前端模块 | 患者端、挂号收费、医技检查/检验/处置执行端、药房、管理员支撑页面 |
| 主要业务链路 | 患者导诊 → 挂号 → 预问诊 → 收费 → 检查/检验执行 → 发药 → 随访 |
| 主要 AI 能力 | AI 智能导诊、AI 预问诊、AI 检查结果分析触发、AI 用药随访、AI 分诊台 |
| 主要数据表 | `register`、`regist_level`、`settle_category`、`department`、`scheduling`、`medical_technology`、`drug_info`、`ai_follow_up_plan`、`ai_follow_up_record` |

### 1.2 不单独负责的范围

以下内容由人员A主要负责，人员B按接口约定进行联调：

- 医生患者查看
- 病历首页
- 检查/检验/处置申请创建
- 检查/检验结果查看的医生端展示
- 门诊确诊
- 开立处方
- 医生端 AI 病历生成、AI 诊断推荐、AI 处方审核展示

---

## 2. 页面与功能清单

### 2.1 患者端页面

| 页面 | 说明 | 优先级 |
|------|------|--------|
| AI 智能导诊 | 患者输入症状，推荐科室、医生、风险等级 | P0 |
| AI 预问诊 | 患者完成问诊，生成结构化摘要供医生查看 | P0 |
| AI 用药随访 | 发药后患者按计划反馈用药和恢复情况 | P1 |

### 2.2 挂号收费页面

| 页面 | 说明 | 优先级 |
|------|------|--------|
| 窗口挂号 | 创建挂号记录，关联患者、科室、医生、排班 | P0 |
| 收费 | 对检查、检验、处置、处方等项目收费 | P0 |
| 费用记录 | 查询患者费用明细和缴费状态 | P0 |
| 退号 | 对符合条件的挂号记录退号 | P1 |
| 退费 | 对符合条件的费用记录退费 | P1 |

### 2.3 医技执行页面

| 页面 | 说明 | 优先级 |
|------|------|--------|
| 检查申请查看 | 查看医生开立的检查申请 | P0 |
| 检查患者录入 | 登记患者进入检查流程 | P0 |
| 检查结果录入 | 录入检查结果，可触发 AI 分析 | P0 |
| 检验申请查看 | 查看医生开立的检验申请 | P0 |
| 检验结果录入 | 录入检验结果，可触发 AI 分析 | P0 |
| 处置申请查看 | 查看医生开立的处置申请 | P1 |
| 处置录入 | 录入处置执行结果 | P1 |

### 2.4 药房页面

| 页面 | 说明 | 优先级 |
|------|------|--------|
| 药房发药 | 查看已缴费处方并确认发药 | P0 |
| 药房退药 | 对符合条件的处方进行退药处理 | P1 |
| 药库管理 | 药品信息、库存、价格维护 | P1 |
| 交易记录 | 查询发药、退药、库存交易记录 | P1 |

### 2.5 管理员支撑页面

| 页面 | 说明 | 优先级 |
|------|------|--------|
| 医生排班 | 维护医生出诊规则和号源相关信息 | P1 |
| AI 分诊台 | 查看 AI 导诊结果和分诊建议 | P1 |
| 基础数据管理 | 科室、员工、医生、药品、医技项目等基础数据 | P2 |

---

## 3. 后端服务开发指南

### 3.1 `registration-service` 职责

`registration-service` 主要负责患者进入门诊流程和费用流转：

- 患者挂号
- 退号
- 收费
- 退费
- 费用记录查询
- 科室、医生、排班等挂号相关基础数据查询
- 将 AI 导诊结果用于辅助挂号

重点接口：

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/registration/register` | 患者挂号 |
| PUT | `/api/registration/register/{id}/cancel` | 取消挂号 |
| GET | `/api/registration/patient-charges` | 查询患者待缴费项目 |
| POST | `/api/registration/charge` | 收费 |
| POST | `/api/registration/refund` | 退费 |
| GET | `/api/registration/expense-records` | 查询费用记录 |

### 3.2 `medtech-service` 职责

`medtech-service` 主要负责检查、检验、处置的执行：

- 读取医生开立的检查/检验/处置申请
- 登记患者进入执行流程
- 录入检查、检验、处置结果
- 更新申请状态
- 结果录入后触发或关联 AI 检查结果分析

重点接口：

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/medtech/check/applications` | 获取待检查患者列表 |
| PUT | `/api/medtech/check/start/{id}` | 开始检查 |
| PUT | `/api/medtech/check/result/{id}` | 录入检查结果 |
| GET | `/api/medtech/inspection/applications` | 获取待检验患者列表 |
| PUT | `/api/medtech/inspection/start/{id}` | 开始检验 |
| PUT | `/api/medtech/inspection/result/{id}` | 录入检验结果 |
| GET | `/api/medtech/disposal/applications` | 获取待处置患者列表 |
| PUT | `/api/medtech/disposal/start/{id}` | 开始处置 |
| PUT | `/api/medtech/disposal/result/{id}` | 录入处置结果 |

### 3.3 `pharmacy-service` 职责

`pharmacy-service` 主要负责药品、处方发药和随访出口：

- 药品信息维护
- 药品库存维护
- 获取待发药处方
- 确认发药
- 退药
- 交易记录查询
- 发药后触发 AI 用药随访计划

重点接口：

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/pharmacy/drugs` | 获取药品列表 |
| POST | `/api/pharmacy/drugs` | 新增药品 |
| PUT | `/api/pharmacy/drugs/{id}` | 更新药品信息 |
| DELETE | `/api/pharmacy/drugs/{id}` | 删除药品 |
| GET | `/api/pharmacy/pending` | 获取待发药患者列表 |
| PUT | `/api/pharmacy/dispense/{registerId}` | 确认发药 |
| PUT | `/api/pharmacy/return/{registerId}` | 退药 |
| GET | `/api/pharmacy/transactions` | 获取交易记录 |

---

## 4. AI 功能开发指南

人员B主要负责患者入口和流程出口上的 AI 能力，同时配合人员A完成医生端 AI 数据的联调。

### 4.1 AI 智能导诊

负责内容：

- 症状输入表单或对话界面
- 推荐科室、推荐医生、风险等级展示
- 导诊结果保存
- 导诊结果传给挂号页面

主要接口：

- `/api/ai/triage/analyze`
- `/api/ai/triage/department`

### 4.2 AI 预问诊

负责内容：

- 患者预问诊页面
- 多轮问答或结构化表单
- 预问诊摘要生成
- 主诉、现病史、既往史、过敏史等字段结构化
- 将摘要提供给人员A的医生患者查看和病历首页

主要接口：

- `/api/ai/consult/previsit`
- `/api/ai/consult/summary`

### 4.3 AI 检查结果分析

负责内容：

- 在检查/检验结果录入后触发 AI 分析
- 保存异常指标、风险等级、分析报告
- 将分析结果提供给人员A的医生端结果查看页面

主要接口：

- `/api/ai/diagnosis/interpret`
- `/api/ai/diagnosis/exam-analyze`

### 4.4 AI 用药随访

负责内容：

- 发药后生成随访计划
- 患者端展示随访任务
- 记录患者反馈
- 生成康复或用药风险评估

主要接口：

- `/api/ai/pharmacy/followup`
- `/api/ai/pharmacy/guide`

### 4.5 AI 分诊台

负责内容：

- 汇总导诊结果
- 展示推荐科室、推荐医生、风险等级
- 支撑管理员或挂号人员进行人工确认

---

## 5. 数据表访问规则

| 表名 | 人员B操作 | 说明 |
|------|-----------|------|
| `department` | SELECT / INSERT / UPDATE | 科室基础数据，按项目实现复杂度决定是否开放维护 |
| `scheduling` | SELECT / INSERT / UPDATE | 医生排班规则 |
| `register` | INSERT / UPDATE / SELECT | 挂号、状态流转、费用状态 |
| `regist_level` | SELECT | 挂号级别 |
| `settle_category` | SELECT | 结算类别 |
| `medical_technology` | SELECT / INSERT / UPDATE | 检查、检验、处置项目 |
| `check_request` | SELECT / UPDATE | 人员A创建，人员B执行和录入结果 |
| `inspection_request` | SELECT / UPDATE | 人员A创建，人员B执行和录入结果 |
| `disposal_request` | SELECT / UPDATE | 人员A创建，人员B执行和录入结果 |
| `drug_info` | SELECT / INSERT / UPDATE / DELETE | 药品信息和库存 |
| `prescription` | SELECT / UPDATE | 人员A创建，人员B发药状态更新 |
| `ai_follow_up_plan` | INSERT / UPDATE / SELECT | 随访计划 |
| `ai_follow_up_record` | INSERT / UPDATE / SELECT | 随访记录 |

> 注意：人员B会读取人员A创建的申请单和处方。字段状态必须提前约定，避免执行端和药房端无法识别医生端创建的数据。

---

## 6. 与人员A的接口协作

### 6.1 人员B依赖人员A的数据

| 来源 | 数据 | 用途 |
|------|------|------|
| `physician-service` | 检查申请、检验申请、处置申请 | 医技执行端查看和录入 |
| `physician-service` | 门诊确诊结果 | 收费、处方、随访上下文 |
| `physician-service` | 处方明细 | 药房发药、退药、随访 |
| 医生端 AI | 处方审核结论 | 药房端参考 |

### 6.2 人员B提供给人员A的数据

| 去向 | 数据 | 用途 |
|------|------|------|
| `physician-service` | 挂号患者、挂号状态、患者基础信息 | 医生患者列表 |
| `physician-service` | 预问诊摘要 | 医生接诊和病历书写 |
| `physician-service` | 检查/检验/处置结果 | 医生查看结果和确诊 |
| `physician-service` | 缴费状态、发药状态 | 医生查看流程进度 |
| 医生端 AI 组件 | 检查结果 AI 分析 | 医生端结果页展示 |

### 6.3 必须提前约定的字段

- `registerId`：贯穿挂号、接诊、检查、收费、发药、随访
- `patientId`：患者唯一标识
- `departmentId`：科室唯一标识
- `physicianId`：医生唯一标识
- `checkRequestId` / `inspectionRequestId` / `disposalRequestId`
- `prescriptionId`
- 费用状态：待缴费、已缴费、已退费
- 申请状态：待缴费、待执行、执行中、已完成、已作废
- 发药状态：待发药、已发药、已退药
- AI 结果状态：未调用、生成中、成功、失败、已过期

---

## 7. 推荐开发顺序

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

### 第1阶段：入口与执行流程可运行

人员B优先完成：

1. AI 导诊
2. 窗口挂号
3. AI 预问诊
4. 收费
5. 检查/检验申请查看
6. 检查/检验结果录入
7. 药房发药
8. AI 用药随访入口

该阶段目标是让患者能够进入流程，让人员A创建的申请和处方能够继续向后流转。

### 第2阶段：补充支撑功能

在主流程可演示后，再补充：

- 退号、退费
- 药房退药
- 药库管理
- 交易记录
- 医生排班
- AI 分诊台
- 基础数据管理
- AI 检查结果分析
- AI 随访计划生成和反馈记录

### 第3阶段：完善体验和边界状态

- 表单校验
- 空状态和错误状态
- AI 服务不可用时的降级提示
- 费用、检查、发药等状态过滤
- 管理员页面的数据维护体验

---

## 8. 共享文件修改规则

以下文件或模块属于共享区域，修改前必须和人员A沟通：

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
ai-gateway-service 的公共 DTO 和统一响应结构
AI 服务公共 Prompt 或公共解析工具
```

建议约定：

- 路由和菜单由一人统一维护，另一个人提交页面路径需求。
- 通用组件先写清楚 props、事件和示例，再给另一个人使用。
- API 类型字段一旦用于联调，不随意重命名。
- Cursor Agent 修改共享文件前，先说明改动范围。

---

## 9. 联调检查清单

人员B提交联调前，应确认：

- 患者可以完成 AI 导诊并进入挂号流程
- 挂号记录能被人员A的医生端读取
- 预问诊摘要能被人员A的医生端展示
- 收费后检查/检验/处方状态正确变化
- 医技端能读取人员A创建的检查/检验/处置申请
- 结果录入后人员A的医生端能看到
- 药房端能读取人员A创建的处方
- 发药后能生成或进入随访流程
- AI 不可用时，传统流程仍可继续

---

## 10. 验收标准

人员B负责的部分完成后，应能演示：

患者完成 AI 导诊和预问诊，挂号收费人员完成挂号与缴费，医技端完成检查/检验结果录入，药房完成发药，患者端可以进入用药随访流程，相关状态能被医生端正确查看。

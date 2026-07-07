# 医生排班课程表 + 请假工作流 设计

> 状态：**设计定稿，待 review 后实现**
> 日期：2026-07-02
> 决策：替换 `/ai` 占位菜单 → 周视图+月视图切换 → 请假走 Dify 完整调整方案工作流 → 管理员确认

---

## 1. 背景与目标

### 1.1 用户诉求（原话）

> "医生端不是有一个 AI 组件区吗，这个是不是没啥用，我想换成类似课程表的一个东西，就是医生知道自己什么时候上班……我还会加一个医生请假的工作流，就是医生提出请假，AI 重新调整排版，然后管理员确认就行了。"

**核心**：把"AI 组件区"这个空占位页换成医生能实际使用的「我的排班」课程表；并补全一条「医生请假 → Dify 排版 → 管理员确认」的工作流。

### 1.2 现状澄清（避免误删）

医生端有**两个** AI 相关入口，必须区分清楚：

| 路由 | 组件 | 用途 | 处理方式 |
|---|---|---|---|
| `/physician/assistant` | `PhysicianAiAssistantPage.vue` | Dify 临床 Copilot（工具调用、工作流、确认卡片） | **保留不动**，核心生产力工具 |
| `/ai` | `RoutePlaceholder`（占位） | 当初预留的"AI 组件区"，全角色可见但无内容 | **本次替换** |

本次改造对象是 `/ai`，**绝不能动 `/physician/assistant`**。

### 1.3 已有基础设施盘点（重大利好）

调研发现后端 + 前端 API 层已大量就绪，本需求主要是**串联 + 补全空壳**，而非从零建设。

**后端 `schedule-service`（已存在）：**
- 实体：`DoctorSchedule` / `SchedulePlan` / `LeaveRequest` / `ScheduleAdjustRequest` / `ScheduleAdjustLog`
- Controller：`ScheduleController`（20+ 接口）、`LeaveController`（请假 + 调整确认全流程）
- Service：`SchedulePlanService` / `DoctorScheduleService` / `LeaveRequestService` / `ScheduleAdjustService` / `AiGenerateTaskService`
- Dify 集成：`DifyIntegrationService` —— ⚠️ **请假相关 3 个方法是空壳**
  - `processLeaveWithAI(LeaveRequest)` —— 只打 log（`DifyIntegrationService.java:94`）
  - `parseLeaveRequest(String)` —— 返回 `"{}"`（`DifyIntegrationService.java:109`）
  - `analyzeQuotaDistribution(...)` —— 只打 log（`DifyIntegrationService.java:102`）
- AI 整月排班：`orchestrate(...)` + `persistAiPlanAndSchedules(...)` —— ✅ 已完整实现，可作为 Dify blocking 调用的参考样板

**前端 API 封装 `shared/api/modules/schedule.ts`（已全部封装）：**
- 排班查询：`plans` / `plan` / `planSchedules` / `calendar` / `available` / `detail` / `byPhysician` ✅
- 排班管理：`createPlan` / `generatePlanByAI` / `publishPlan` / `createSchedule` / `updateSchedule` / `stopSchedule` / `resumeSchedule` ✅
- 号源：`deductQuota` / `returnQuota` ✅
- **请假全套**：`leaves` / `createLeave` / `approveLeave` / `substitutes` ✅
- **调整确认全套**：`pendingAdjusts` / `adjust` / `confirmAdjust` / `rejectAdjust` / `createUrgentAdjust` ✅

**管理员前端（已存在）：**
- `admin/pages/ScheduleManagement.vue`（1597 行）—— 已有排班计划、AI 生成、号源分析等完整功能
- 路由 `/admin/schedule`（`routes.ts:385`）

### 1.4 已发现的缺陷（实现时一并修）

**Bug 1：替班医生推荐逻辑反了**（`LeaveRequestService.java:149-156`）
```java
public List<DoctorSchedule> getAvailableSubstitutes(...) {
    return schedules.stream()
        .filter(s -> s.getPhysicianId().equals(excludePhysicianId))  // ❌ 反了
        .filter(s -> s.getStatus().equals("正常"))
        .filter(s -> s.getAvailableQuota() > 0)
        .collect(...);
}
```
当前实现**保留了要排除的医生**（应该是 `.filter(s -> !s.getPhysicianId().equals(excludePhysicianId))`）。Dify 接入后，替班推荐改由 Dify 工作流输出，此方法降级为"候选池查询"，bug 仍需修，否则候选池本身就是错的。

**Bug 2：`processLeave` 不做替班推荐**（`LeaveRequestService.java:116-144`）
当前 `processLeave` 生成的 `ScheduleAdjustRequest` 只填了 `oldPhysicianId` / `oldQuota`，**没填 `newPhysicianId` / `newQuota`**——也就是说现在请假审批后系统只是"记下有人请假"，并不真的安排替班。这正是 Dify 工作流要补上的空白。

---

## 2. 用户流程

### 2.1 医生查看排班（主流程）

```
医生登录 → 侧边栏点击「我的排班」（原 AI 组件区位置）
   ↓
默认显示【本周】课程表（周视图）
   ↓
横轴=周一到周日，纵轴=上午/下午
格子内容：科室 · 诊室 · 已挂号/总号源 · 状态标签
   ↓
点击格子 → 弹层显示该班次详情（含已挂号患者列表预览）
   ↓
切换【月视图】→ 日历样式，每格显示当天班次数量徽标
点击日期 → 侧滑抽屉显示当天所有班次
```

### 2.2 医生请假（完整工作流）

```
医生在「我的排班」点击某班次 → 「申请请假」
   ↓ 填表：请假类型(事假/病假/公假)、原因、(可选)自然语言补充
   ↓ 调 POST /api/schedule/leave/create
   ↓ status=待审批
   ↓
管理员在「排班管理 → 请假审批」Tab 看到待审申请
   ↓ 点击【批准】
   ↓   1. LeaveRequest.status → 已批准
   ↓   2. 触发 processLeave(leaveId)：
   ↓      - 查同科室其他医生当天/同时段排班 → 组装 context
   ↓      - 调 Dify「请假调整排版」工作流（blocking）
   ↓      - 解析输出 → 填充 ScheduleAdjustRequest.newPhysicianId / newQuota / reason
   ↓      - status=PENDING（仍需管理员二次确认调整方案）
   ↓
管理员在「调整确认」Tab 看到 Dify 生成的调整方案
   ↓ 审阅替班医生、号源迁移数、AI 给出的理由
   ↓ 【确认】→ ScheduleAdjustRequest.status=APPROVED，DoctorSchedule 落库更新
   ↓ 【驳回】→ 填写驳回理由，LeaveRequest 回滚到「已批准但未处理」状态，管理员手动选替班
```

### 2.3 异常分支

- **Dify 调用超时/失败**：`processLeaveWithAI` 已有 try-catch（`LeaveRequestService.java:78`），失败时 `LeaveRequest` 保持「已批准」状态，前端提示"AI 调整失败，请管理员手动处理"，管理员可走 `createUrgentAdjust` 手动指派替班。
- **Dify 返回的 `substituteId` 不存在或当天无空排班**：后端校验失败，`ScheduleAdjustRequest` 不落库，原样回退到手动流程。
- **号源迁移后超额**：Dify 输出的 `migratedQuota` + 替班原 `usedQuota` > 替班 `totalQuota` 时，后端拒绝方案，回退手动。

---

## 3. 菜单与路由改造

### 3.1 路由变更（`src/app/router/routes.ts`）

**改造 `/ai` 路由**（`routes.ts:475-480`）：

```diff
  {
    path: 'ai',
-   name: 'AiComponents',
-   component: placeholder,
-   meta: { title: 'AI 组件区', description: 'AI 结果卡片和嵌入组件预留区',
-            icon: 'MagicStick', roles: ['physician','registration','medtech',
-            'pharmacy','patient'], requiresAuth: true, owner: '共同' },
+   name: 'MySchedule',
+   component: MySchedulePage,
+   meta: { title: '我的排班', description: '查看我的班次课程表与请假',
+            icon: 'Calendar', roles: ['physician','registration','medtech',
+            'pharmacy','patient'], requiresAuth: true, owner: '共同' },
  },
```

**保留** `/physician/assistant` 路由不动。

**保留** `/admin/schedule` 路由，仅增强组件。

### 3.2 角色视图差异

同一 `/ai` 路由，按角色展示不同内容（在组件内根据 `authStore.role` 分支）：

| 角色 | 默认视角 | 能否请假 |
|---|---|---|
| `physician` | 本人课程表 | ✅ 本人班次可请假 |
| `registration` / `medtech` / `pharmacy` | 本人课程表 | ✅（若该角色也有排班） |
| `admin` | 全院排班总览 + 跳转「排班管理」 | — 不显示请假，显示「去审批」入口 |
| `patient` | 空状态"您无排班" | — |

---

## 4. 前端页面设计

### 4.1 医生端：`MySchedulePage.vue`（新建）

**位置**：`src/modules/physician/pages/MySchedulePage.vue`（虽是全角色路由，但物理文件放 physician 模块下，其他角色复用）

**布局**：

```
┌─────────────────────────────────────────────────────┐
│ PageHeader: 我的排班   [周视图][月视图]   [<本周>][>]│
├─────────────────────────────────────────────────────┤
│ ┌── 周视图（默认）────────────────────────────────┐ │
│ │       周一   周二   周三   周四   周五   周六  周日│ │
│ │ 上午  内科    ──     ──    内科    ──     ──    休│ │
│ │       5/30                5/30                   │ │
│ │       [请假]              [请假]                 │ │
│ │ 下午  内科    门诊    ──    ──     内科    ──    休│ │
│ │       0/30   8/30            0/30                │ │
│ └─────────────────────────────────────────────────┘ │
│                                                      │
│ 待办徽标：您有 2 条请假待审批结果                    │
└─────────────────────────────────────────────────────┘
```

**格子状态色板**（沿用现有 `StatusTag` 组件）：
- 正常（已发布）：`primary` 蓝色
- 即将开始（<24h）：`warning` 橙色
- 已挂号 > 80%：`danger` 红色（号源紧张提示）
- 停诊：`info` 灰色
- 休息/无排班：浅灰虚线框

**交互**：
- 点击格子 → `ElDrawer` 显示班次详情（科室、诊室、挂号级别、号源明细、已挂号患者列表预览）
- 「申请请假」按钮 → `ElDialog` 弹出请假表单（见 4.3）
- 视图切换：`ElRadioGroup` 或 `ElTabs`，默认周视图，状态保留在 `route.query.view`

**API 调用**：
- `scheduleApi.byPhysician(physicianId, startDate, endDate)` —— 已封装，直接用
- `scheduleApi.createLeave(data)` —— 已封装

### 4.2 月视图设计

```
┌── 月视图 ──────────────────────────────────────┐
│  << 2026年7月 >>                               │
│  日  一  二  三  四  五  六                     │
│            1   2   3   4                       │
│            ●   ●●      ●    （圆点=班次数量）   │
│  5   6   7   8   9  10  11                     │
│  ●   ●●  ●           ●                         │
│ 12  13  14  15  16  17  18                     │
│ ●   ●●                                         │
└────────────────────────────────────────────────┘
点击日期 → 侧滑抽屉显示当天班次（同周视图格子内容）
```

**实现选择**：用 `ElCalendar`（Element Plus 自带）+ 自定义 `#date-cell` slot，无需引入第三方日历库。

### 4.3 请假表单（`ElDialog`）

```
┌─ 申请请假 ────────────────────────┐
│ 班次：2026-07-08 上午 内科门诊     │
│ 请假类型：○事假 ●病假 ○公假 ○其他 │
│ 原因：[_____________________]     │
│ ─ 可选 ──                         │
│ 自然语言补充（让 AI 更准）：       │
│ [发烧39度需要休息一天]             │
│                      [取消][提交] │
└───────────────────────────────────┘
```

**字段映射到 `LeaveRequest`**：
- `leaveDate` / `timeSlot` —— 从所点击的班次自动带入
- `leaveType` —— 单选
- `reason` —— 必填
- `rawText` —— 选填，后续可接入 Dify 自然语言解析（本迭代不用）

### 4.4 管理员端：增强 `ScheduleManagement.vue`

在现有 `ElTabs` 中**新增两个 Tab**：

1. **「请假审批」Tab**
   - 列表展示 `status=待审批` 的请假（调 `scheduleApi.leaves({ status: '待审批' })`）
   - 每行：医生姓名 · 日期 · 时段 · 类型 · 原因 · 【批准】【拒绝】
   - 批准后该行消失，进入「调整确认」Tab

2. **「调整确认」Tab**（如果当前没有）
   - 列表展示 `status=PENDING` 的 `ScheduleAdjustRequest`（调 `scheduleApi.pendingAdjusts()`）
   - 每行展开：原医生 → 替班医生 · 号源迁移数 · AI 理由 · 【确认】【驳回】
   - 驳回需填理由

---

## 5. Dify「请假调整排版」工作流设计

### 5.1 工作流定位

**新建 1 个 Dify 工作流**（不是 2 个）——你已确认走"Dify 输出完整调整方案"。

- **输入**：请假医生 + 日期 + 时段 + 同科室其他医生的当天排班候选池
- **输出**：推荐替班医生 + 号源迁移方案 + 理由

### 5.2 输入契约（后端 → Dify）

```json
{
  "inputs": {
    "department_id": "201",
    "department_name": "内科",
    "leave_physician_id": "102",
    "leave_physician_name": "张三",
    "leave_date": "2026-07-08",
    "leave_time_slot": "上午",
    "leave_quota_used": "5",
    "candidates_json": "[{\"physicianId\":103,\"name\":\"李四\",\"date\":\"2026-07-08\",\"slot\":\"上午\",\"totalQuota\":30,\"usedQuota\":12,\"availableQuota\":18,\"registLevel\":\"专家号\"},{...}]"
  },
  "response_mode": "blocking",
  "user": "schedule-service"
}
```

> **注意**：复用现有 `DifyIntegrationService.callDifyWorkflow` 模板。Dify 开始节点所有字段必须是 String，复合类型字段 JSON.stringify（与现有 `orchestrate` 方法保持一致）。

### 5.3 输出契约（Dify → 后端）

```json
{
  "data": {
    "status": "succeeded",
    "outputs": {
      "substitute_physician_id": 103,
      "substitute_physician_name": "李四",
      "migrated_quota": 5,
      "new_total_quota": 35,
      "reason": "李四当天上午已有专家号排班，余量18，可吸收5个迁移号源，且同为内科专家级别。",
      "confidence": 0.92,
      "alternatives": [
        {"physicianId": 104, "name": "王五", "reason": "余量不足，仅剩3"}
      ]
    }
  }
}
```

### 5.4 Dify 工作流内部节点（建议）

> 这是给 Dify 平台搭工作流的人看的，不在本次代码改动范围。

1. **开始节点**：接收 6 个 String 输入
2. **LLM 节点**（核心）：system prompt 让模型按"同科室优先 > 余量充足 > 同挂号级别 > 当天不重复排班"排序，输出 JSON
3. **代码节点**：校验输出的 `substitute_physician_id` 在 `candidates_json` 里、`migrated_quota` ≤ 余量、`new_total_quota` 合理
4. **结束节点**：输出上述结构

### 5.5 后端改动（填充空壳）

**文件**：`schedule-service/src/main/java/com/xikang/schedule/service/DifyIntegrationService.java`

新增方法（参考现有 `orchestrate` 模板）：

```java
/**
 * 处理医生请假：调 Dify 工作流生成调整方案，落库为 ScheduleAdjustRequest(PENDING)。
 */
@Transactional
public ScheduleAdjustRequest processLeaveWithAI(LeaveRequest leaveRequest) {
    // 1. 查请假医生的原排班
    DoctorSchedule leaveSchedule = doctorScheduleMapper.selectByPhysicianAndDate(...);
    if (leaveSchedule == null) return null;

    // 2. 查同科室候选替班（修 Bug 1 后的方法）
    List<DoctorSchedule> candidates = leaveRequestService.getAvailableSubstitutes(
        leaveSchedule.getDepartmentId(),
        leaveSchedule.getWorkDate(),
        leaveSchedule.getTimeSlot(),
        leaveSchedule.getPhysicianId()
    );
    if (candidates.isEmpty()) {
        log.warn("无可用替班医生，leaveId={}", leaveRequest.getId());
        return null;
    }

    // 3. 组装 Dify 输入
    Map<String, Object> input = buildLeaveAdjustWorkflowInput(leaveSchedule, candidates);

    // 4. 调 Dify（blocking）
    String rawResponse = callDifyWorkflow(input, null);

    // 5. 解析输出
    LeaveAdjustResult result = parseLeaveAdjustResult(rawResponse);

    // 6. 校验 + 生成 ScheduleAdjustRequest
    ScheduleAdjustRequest adjustRequest = buildAdjustRequest(
        leaveSchedule, leaveRequest, result);
    return scheduleAdjustService.createRequest(adjustRequest);
}
```

**配置项**（`schedule-service/src/main/resources/application.yml`）新增：

```yaml
dify:
  api-key: ${DIFY_API_KEY:}
  base-url: ${DIFY_BASE_URL:http://43.139.102.203}
  timeout-ms: 660000
  workflow:
    monthly-plan: default      # 现有整月排班工作流
    leave-adjust: leave-adjust # 新增请假调整工作流标识
```

---

## 6. 数据模型变更

**本需求不新增表，不新增字段**。所有数据落库到现有：

- `leave_request` —— 请假申请
- `schedule_adjust_request` —— Dify 生成的调整方案（status: PENDING → APPROVED/REJECTED）
- `schedule_adjust_log` —— 调整确认日志
- `doctor_schedule` —— 确认后更新 `physician_id` / `used_quota` / `total_quota`

### 6.1 `ScheduleAdjustRequest` 字段映射（Dify 接入后）

| 字段 | 来源 | 说明 |
|---|---|---|
| `schedule_id` | 原排班 | 请假医生的排班 ID |
| `adjust_type` | `leave_ai` | 区分管理员手动 vs AI 生成 |
| `old_physician_id` / `old_quota` | 原排班 | 现状已填 |
| `new_physician_id` | **Dify 输出** ⭐ | 现状空白，本次补 |
| `new_quota` | **Dify 输出** ⭐ | 现状空白，本次补 |
| `reason` | Dify `reason` 字段 | 给管理员看的 AI 解释 |
| `triggered_by` | 请假医生 ID | |
| `affect_patients` | 原 `used_quota` | 影响的已挂号患者数 |
| `status` | PENDING | 待管理员二次确认 |

---

## 7. API 改动清单

### 7.1 复用现有 API（无需改动）

| 操作 | API | 前端方法 |
|---|---|---|
| 查本人排班 | `GET /api/schedule/physician/{id}` | `scheduleApi.byPhysician` ✅ |
| 查日历视图 | `GET /api/schedule/calendar` | `scheduleApi.calendar` ✅ |
| 创建请假 | `POST /api/schedule/leave/create` | `scheduleApi.createLeave` ✅ |
| 查请假列表 | `GET /api/schedule/leave/list` | `scheduleApi.leaves` ✅ |
| 批准请假 | `POST /api/schedule/leave/{id}/approve` | `scheduleApi.approveLeave` ✅ |
| 查待确认调整 | `GET /api/schedule/adjust/pending` | `scheduleApi.pendingAdjusts` ✅ |
| 确认调整 | `POST /api/schedule/adjust/confirm` | `scheduleApi.confirmAdjust` ✅ |
| 驳回调整 | `POST /api/schedule/adjust/reject` | `scheduleApi.rejectAdjust` ✅ |

### 7.2 后端需改动

| 改动 | 文件 | 说明 |
|---|---|---|
| 填充空壳 | `DifyIntegrationService.processLeaveWithAI` | 接 Dify 工作流 |
| 修 Bug 1 | `LeaveRequestService.getAvailableSubstitutes` | 反转 exclude 过滤 |
| 增强 | `LeaveController.approveLeave` | 现已调用 `processLeave`，确认它会进一步调 `processLeaveWithAI` |
| 新增配置 | `application.yml` | Dify leave-adjust 工作流标识 |

### 7.3 前端需改动

| 文件 | 改动 |
|---|---|
| `routes.ts` | `/ai` 路由改名+换组件 |
| `physician/pages/MySchedulePage.vue` | **新建**（周视图 + 月视图 + 请假入口） |
| `physician/components/ScheduleWeekGrid.vue` | **新建**（周视图表格组件） |
| `physician/components/ScheduleMonthCalendar.vue` | **新建**（月视图，基于 ElCalendar） |
| `physician/components/LeaveApplyDialog.vue` | **新建**（请假表单弹窗） |
| `physician/components/ScheduleCellDrawer.vue` | **新建**（班次详情抽屉） |
| `admin/pages/ScheduleManagement.vue` | **增强**（加「请假审批」「调整确认」两个 Tab，复用现有 API） |

---

## 8. 实施分阶段

### 阶段 1：前端骨架 + 修 Bug（不依赖 Dify）
- [ ] 新建 `MySchedulePage.vue` + 子组件
- [ ] 周视图 + 月视图切换
- [ ] 路由改造 `/ai` → `MySchedule`
- [ ] 请假表单（调已有 `createLeave`）
- [ ] 后端修 Bug 1（`getAvailableSubstitutes`）
- [ ] 管理员端加「请假审批」Tab（调已有 `leaves` + `approveLeave`）

**验收**：医生能看排班、能提交请假（但批准后无替班推荐，需手动 `createUrgentAdjust`）。

### 阶段 2：Dify 工作流接入
- [ ] Dify 平台搭「请假调整排版」工作流
- [ ] 后端填 `processLeaveWithAI` 空壳
- [ ] `approveLeave` 链路打通：批准 → Dify → ScheduleAdjustRequest(PENDING)
- [ ] 管理员端加「调整确认」Tab

**验收**：完整工作流闭环，Dify 返回的替班方案可被管理员一键确认。

### 阶段 3：体验打磨（可选）
- [ ] 周视图格子点击进入患者列表
- [ ] 请假被驳回时的通知
- [ ] 月视图的"今天"高亮 + 节假日标识

---

## 9. 风险与权衡

### 9.1 周视图 + 月视图双视图的成本

你选了"双视图切换"，相比单周视图，前端工作量约 **+40%**（两套渲染逻辑、两套交互、状态同步）。考虑到：
- 周视图已能覆盖 90% 日常使用（"我这周哪天上班"）
- 月视图主要价值是看长期排班模式
- 项目是课程作业性质

**建议**：阶段 1 先只做周视图，阶段 3 再补月视图。文档保留月视图设计章节，但实现优先级降低。**这一点请在 review 时确认**。

### 9.2 Dify blocking 调用超时

整月排班工作流已实测可跑（现有 `orchestrate` 方法 timeout 660s）。请假调整工作流输入数据量小（仅当天同科室几个候选），预期 < 30s。但 Dify 平台本身可能有波动，`LeaveRequestService.createLeave` 的 try-catch 必须保留，失败回退到手动流程。

### 9.3 号源一致性

确认调整后，需要同步：
- 原 `DoctorSchedule.used_quota` → 0（替班接手后原班次作废）
- 替班 `DoctorSchedule.used_quota += migrated_quota`
- 原 `DoctorSchedule.total_quota` 调整或状态置为"停诊"
- 涉及的 `registration` 记录需要把 `schedule_id` 改指向替班

**这一段事务边界要在实现时仔细处理**，建议放在 `ScheduleAdjustService.confirmAdjust` 里一次事务完成。现状代码是否已做此迁移，需在阶段 2 编码前再确认一次（本次设计文档未深入到这一层）。

### 9.4 请假被驳回后的回滚

Dify 生成的调整方案被管理员驳回后，`LeaveRequest` 处于"已批准但未处理"状态。需要：
- 前端在「请假审批」Tab 加一个"已批准但无替班"筛选器
- 管理员对此类记录走 `createUrgentAdjust` 手动指派

---

## 10. 待 review 的开放问题

1. **双视图是否必须一次到位？**（见 9.1，建议阶段 1 先做周视图）
2. **Dify 工作流的搭建人**：是你自己在 Dify 平台搭，还是需要文档里附详细 prompt？
3. **请假通知**：管理员批准/驳回后，医生端是否需要消息提醒？现有系统是否有消息中心可复用？
4. **患者端影响**：替班确认后，已挂号患者是否需要通知"您的医生换了"？本项目范围内是否考虑？

---

## 附：文件位置速查

| 关注点 | 路径 |
|---|---|
| 后端 Dify 集成 | `XIKANG/xikang-cloud-hospital/schedule-service/src/main/java/com/xikang/schedule/service/DifyIntegrationService.java` |
| 后端请假逻辑 | `.../schedule/service/LeaveRequestService.java` |
| 后端调整确认 | `.../schedule/service/ScheduleAdjustService.java` |
| 后端 Controller | `.../schedule/controller/{Schedule,Leave}Controller.java` |
| 前端 API 封装 | `XIKANG/xikang-hospital-frontend/src/shared/api/modules/schedule.ts` |
| 前端管理员页 | `XIKANG/xikang-hospital-frontend/src/modules/admin/pages/ScheduleManagement.vue` |
| 前端路由 | `XIKANG/xikang-hospital-frontend/src/app/router/routes.ts` |

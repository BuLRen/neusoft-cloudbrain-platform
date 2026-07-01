# FEAT - 患者端支付中心（payment-service）

> 状态：**v3.2（v3.1 二轮子代理验证后修订）**
> 创建日期：2026-07-01
> 负责人：人员B
> 历史版本：v1.0（废弃双表）、v2.0（无状态 Feign 层）、v3.0（方案 B：表归属转移）、v3.1（补漏 + 锁策略 + 原子性）、**v3.2（修正 schema 误判：不引入 ref_id 列，复用既有 (register_id, item_code) 幂等）**

> **v3.1 → v3.2 变更摘要**（子代理二轮验证发现）：
> - **B1（关键）**：v3.1 误以为 `expense_record` 有 `ref_id` 列 — 实际表里**根本没有**。整个 v3.1 设计的 `(item_code, ref_id)` 幂等键是错的。
> - 改用既有 `(register_id, item_code)` 作幂等键（既有 `uq_expense_record_medication_fee` 索引已保证 MEDICATION_FEE 唯一；REGISTRATION_FEE 走应用层去重 `invalidateDuplicateRegistrationFees`），**真正保住零 DDL**。
> - B2：`nextval` 预获取与既有 `selectNextSequenceValue/syncIdSequence` 逻辑（L201-219）冲突 — 明确说明两者协同。
> - B3：定时清理任务的 in-flight 窗口 — 明确 Feign 调用位置（事务内 vs afterCommit）。
> - R1：write-token 改为**强制 Redis**（不允许纯内存，避免重启 race）。
> - R2：明确患者侧并发 payItem（payItem↔pay-all）由 `SELECT FOR UPDATE + 二次校验` 保护，**不**走 write-token。
> - R3：明确 `on-fee-paid` 回调自身的幂等性（`IF pay_status != 2 THEN update`）。
> - R4：补 §2.2 链 G/H：`payRegistration` / `payMedication` 兼容路径。
> - R5：表述修正 — "网关不放行" 改为 "Feign 通过 Nacos 直连，不经网关"。

---

## 一、本版核心决策

### 1.1 决策快照

| 维度 | 决策 |
|---|---|
| **后端架构** | 新建 `payment-service` :8096，**接管 `expense_record` 表的读写主职责** |
| **物理表位置** | **不动**。`expense_record` 仍在 xikang_hospital 库，31 行历史数据零迁移 |
| **服务边界** | registration / pharmacy 不再直接读写 `expense_record`，**全部改 Feign 调 payment-service** |
| **`patient_balance_transaction` / `account_balance`** | **归属 auth-service 不变**（账户余额本就是 auth 的职责） |
| **订单粒度** | 按 `register_id` 聚合成账单视图，但底层**逐项扣款**（保留两项独立支付） |
| **检查费** | 数据模型 + UI 预留（item_code=EXAMINATION_FEE），本次不接通 medtech |
| **前端入口** | 患者端侧边栏「我的账单」一级菜单（/patient/payment） |

### 1.2 版本差异

| 维度 | v2.0 | v3.0 | v3.1 | **v3.2** |
|---|---|---|---|---|
| payment-service 是否有 DB | 无 | 有 | 同 v3.0 | 同 v3.0 |
| 调用方向 | payment → registration | reg/phar → payment + payment → auth/reg | 同 v3.0 | 同 v3.0 |
| registration 改造 | 不动 | ChargeService/RefundService/RegistrationService | + ExpenseRecordService + StatsMapper | 同 v3.1 |
| pharmacy 改造 | 不动 | ensureMedicationFeeCreated / assertMedicationPaid | 同 v3.0 | 同 v3.0 |
| 双写期并发保护 | 无 | 无 | write-token (Redis 或内存) + FOR UPDATE | **write-token 强制 Redis** + FOR UPDATE |
| createRegistration 原子性 | 不动 | 笼统 | nextval 预生成 refId（错误，与序列同步逻辑冲突）| **保留 useGeneratedKeys + Feign 在 @Transactional 内 + 定时清理 orphan** |
| 幂等键 | — | (item_code, ref_id)（需要新索引）| 同 v3.0 | **(register_id, item_code) 复用既有约束，无新索引** |
| 回调触发条件 | — | 仅 REGISTRATION_FEE | 所有 itemCode | **所有 itemCode，幂等（重算 summary）** |
| 历史数据 | 不动 | 不动 | 不动 | **不动（零 DDL 真正成立）** |
| 风险 | 低 | 中 | 中 | **低-中**（schema 与代码事实对齐）|

### 1.3 范围明确（本次必做 vs 后续）

**本次必做**：
- ✅ payment-service 建模块、建 entity/mapper、读写 `expense_record`
- ✅ registration-service 改造（**所有** `expense_record` 读写改 Feign，含 ExpenseRecordService + StatsMapper + fillPaymentStatus）
- ✅ pharmacy-service 改造（`ensureMedicationFeeCreated` / `assertMedicationPaid` 改 Feign）
- ✅ payment-service 患者端 API（账单聚合查询 + 单项/全部支付）
- ✅ payment-service → registration-service 反向回调（所有 itemCode 触发，重算 pay_status，幂等）
- ✅ **write-token 端点（强制 Redis）+ 定时清理任务**
- ✅ 兼容入口 `payRegistration` / `payMedication` Feign 透传（链 G/H）
- ✅ 前端 PatientPayment.vue + 路由 + 导航
- ✅ 前端 API 重构（registration.ts 的支付相关函数迁到 payment.ts）

**本次不做**：
- ❌ 收费员 `charge` 接口的彻底迁移（保留 registration-service 现有 + write-token 协调，v3.3 再彻底迁）
- ❌ medtech-service 接通检查费（队友后续）
- ❌ 真实支付网关

---

## 二、架构

### 2.1 服务依赖图（v3.1 调用方向反转）

```
┌──────────────────────────────────────────────────────────────┐
│                     gateway-service :8080                     │
└──────────────────────────────────────────────────────────────┘
   │ /api/payment/**         │ /api/registration/**     │ /api/pharmacy/**
   ▼                         ▼                          ▼
┌──────────────────┐   ┌──────────────────┐    ┌──────────────────┐
│ payment-service  │◀──│ registration-svc │    │  pharmacy-svc    │
│      :8096       │   │      :8091       │    │      :8094       │
│ ┌──────────────┐ │   │ ┌──────────────┐ │    │ ┌──────────────┐ │
│ │expense_record│ │   │ │ register 表  │ │    │ │ prescription │ │
│ │  Mapper      │ │   │ │ Refund 流程  │ │    │ │ drug_stock   │ │
│ │ 账单聚合     │ │   │ │ 挂号建单     │ │    │ │ 发药         │ │
│ └──────┬───────┘ │   └──────────────────┘    └──────────────────┘
 │        │        │
 │        │ Feign  │ 反向回调：
 │        ▼        │   payment → registration
 │ ┌──────────────┐ │   POST /api/registration/internal/{registerId}/on-fee-paid
 │ │ auth-service │ │   → 更新 register.visit_state / pay_status
 │ │ 扣余额/退款  │ │
 │ └──────────────┘ │
 └──────────────────┘
```

**关键反转**：
- 旧：`patient → registration.ChargeService → 写 expense_record + Feign auth 扣款`
- 新：`patient → payment-service → 写 expense_record + Feign auth 扣款 + Feign registration 回调更新 register`

### 2.2 调用链（v3.2 完整版，已修订）

**A. 患者挂号（保持 registration-service 主导，触发 payment-service 建费用项）**
```
前端 POST /api/registration/register
  → registration.RegistrationService.createRegistration  [@Transactional L54]
    ├─ [既有 L201-219] selectNextSequenceValue / syncIdSequence 序列同步逻辑（保留不动）
    ├─ INSERT register (visit_state=1, pay_status=0)  [L221]
    │   ← MyBatis useGeneratedKeys 回填 register.id（既有逻辑，v3.2 不改）
    ├─ [新] Feign: payment-service.POST /api/payment/internal/items
    │         { registerId=register.id, itemCode=REGISTRATION_FEE, amount, ... }
    │   ← payment-service: 幂等 INSERT expense_record status=0
    │       （REGISTRATION_FEE：先 SELECT 后 INSERT；MEDICATION_FEE：ON CONFLICT）
    │   ← 返回 itemId
    ├─ [既有 L229] createRegistrationFee 改为 Feign 调（即上面那步）
    ├─ tryBalancePayment (若余额够)  [L230, v3.2 改造为 Feign]:
    │   └─ Feign: payment-service.POST /api/payment/internal/items/{itemId}/pay
    │       ← payment-service: 扣 auth 余额 + UPDATE expense_record status=1
    │       ← payment-service: Feign 回调 registration.on-fee-paid（在 afterCommit 阶段，避免事务内 Feign）
    └─ [既有 L232] invalidateDuplicateRegistrationFees（保留，仍走 ExpenseRecordMapper 兜底；v3.2 后改为 Feign）
[事务失败时] @Transactional 回滚 → register 行不存在；
   → payment-service 端 expense_record 留下 status=0 孤儿行
   → v3.2 兜底：payment-service 定时任务每 5min 扫描
      "register_id NOT IN (SELECT id FROM register) AND status=0 AND create_time < NOW()-10min"
      标记为 status=3（已作废）
```

⚠️ **原子性损失分析**（v3.0 子代理 R1 + v3.2 修订）：
| 路径 | 失败点 | 后果 | 缓解 |
|---|---|---|---|
| register insert 失败 | 之前 | payment-service 未被调（无 expense 行）| OK，无残留 |
| payment createItem Feign 失败/超时 | registration `@Transactional` 回滚 register | expense 行未创建（payment 那边若已 insert 也会因 register 不存在被定时清理）| 定时清理 |
| tryBalancePayment Feign 扣款成功后 register 回滚 | registration 回滚 | **余额已扣 + register 没了** | registration 在 afterCommit 检测到事务 rollback 时主动调 `payment.internal.items/{itemId}/refund` 补偿；Feign 超时则定时清理（status=0 行被清，但已扣余额需要 payment-service 在清理前检测 `pay_time != NULL` 防止误清已支付孤儿）|

> **v3.2 关键修订**（vs v3.1）：
> - v3.1 提议 `nextval` 预生成 id — 与既有 `selectNextSequenceValue/syncIdSequence`（L201-219）冲突，**取消**。继续用 useGeneratedKeys 回填 id，Feign 调用在 INSERT 之后。
> - Feign 调用在 `@Transactional` 内（事务未提交）— payment-service 端的 expense 行在 registration 提交前就已 commit。这就是为什么需要定时清理 orphan 行。
> - 回调 `on-fee-paid` 必须放在 `afterCommit`（参考既有 L244-265 的 `registerSynchronization` 模式），避免事务未提交就调外部服务。

**B. 患者自助缴挂号费**
```
前端 POST /api/payment/orders/{registerId}/items/{itemId}/pay
  → payment-service.PaymentOrchestrator.payItem
    ├─ SELECT expense_record WHERE id=itemId AND status=0 FOR UPDATE   ← 行级锁防双写
    ├─ Feign auth-service.deductBalance(businessType=REGISTRATION)
    ├─ UPDATE expense_record status=1, pay_time=NOW
    └─ Feign registration-service.POST /api/registration/internal/{registerId}/on-fee-paid
         ← registration 重新调 payment.internal.items.summary 汇总，决定 visit_state/pay_status
```

**C. 医生开方 + 药品费入账**
```
pharmacy.PharmacyService.getPatientPrescriptions
  → ensureMedicationFeeCreated
    [新] Feign: payment-service.POST /api/payment/internal/items
            { registerId=prescription.register_id, itemCode=MEDICATION_FEE, amount, ... }
```

> **v3.2 关键**：不传 refId（表里无此列）。药品费的"哪些药品组成了这次处方"信息存在 pharmacy 自己的 `prescription` 表，expense_record 只记总金额 + register_id。pharmacy 想反查时用 `GET /internal/items/by-register?registerId=&itemCode=MEDICATION_FEE`。

**D. 患者自助缴药品费（v3.1 修订：必须回调 registration）**
```
前端 POST /api/payment/orders/{registerId}/items/{itemId}/pay
  → payment-service.PaymentOrchestrator.payItem
    ├─ SELECT expense_record ... FOR UPDATE
    ├─ Feign auth.deductBalance(businessType=MEDICATION)
    ├─ UPDATE expense_record status=1
    └─ Feign registration-service.POST /api/registration/internal/{registerId}/on-fee-paid
         ← registration 重新汇总该挂号所有 expense 状态，刷新 register.pay_status
```

> **v3.0 → v3.1 修订**（R3）：v3.0 说 "药品费不需要回调 registration" — 错。`RegistrationService.fillPaymentStatus`（L767-823）当前直接 `expenseRecordMapper.selectByRegisterId` 汇总 pay_status，迁移后 registration 不再持有 mapper，必须通过回调重新拉取汇总。所以**所有支付成功（不论 itemCode）都要回调**。

**E. 退号 / 退费（v3.2 补充 Scenario Z）**
```
前端 POST /api/registration/{id}/cancel
  → registration.RegistrationService.cancelRegistration  [@Transactional]
    ├─ Feign payment-service.POST /api/payment/internal/items/{regItemId}/refund
    │    ← payment-service: Feign auth.refundBalance + UPDATE expense_record status=2（仅退 REGISTRATION_FEE）
    └─ UPDATE register.visit_state=4
```

> **Scenario Z 边界**（v3.2 R3 验证补）：`cancelRegistration`（L355-394）只调 `refundRegistrationFee`，**不退药品费**。这是有意为之 — 处方可能已发药/已消费，患者需走 `RefundService.refund(expenseRecordId)` 独立申请药品费退款（收费员审批）。文档明确：cancelRegistration ≠ 全额退款。

**F. 收费员集中收费（v3.2 write-token 强制 Redis）**
```
registration.ChargeService.charge  [保留旧路径]
  ├─ [v3.2 新] 申请 payment-service 写令牌：POST /api/payment/internal/write-token
  │    body: { registerId, holder: "CHARGE_SERVICE", ttlSeconds: 30 }
  │   ← 201 拿到令牌 / 409 已被 payment 持有（提示用户"患者正在自助支付，请稍候"）
  ├─ 拿到令牌后 SELECT/UPDATE expense_record status=1
  ├─ UPDATE register.pay_status / visit_state
  └─ 释放令牌（DEL key）或让其自然过期
```

> **v3.2 修订**（vs v3.1 R2）：write-token **强制 Redis**（`SET NX EX 30`），不允许纯内存 `ConcurrentHashMap` — 重启 race 会让 charge 与 payItem 同时进入 critical section，`FOR UPDATE` 兜底虽然能挡住 DB 层，但 auth.deductBalance 可能已发出，造成短暂双扣。Redis 是更可靠的锁。本项目既有 redis 实例可复用（参考 auth-service 的 token 缓存）。

**G. 患者侧兼容入口 `payRegistration`（v3.2 补，原前端 `registration.ts:140-145` 调用）**
```
前端 POST /api/registration/{registerId}/pay  [兼容入口]
  → registration.RegistrationController.payRegistration [v3.2 改 Feign 透传]
    ├─ Feign payment-service.GET /api/payment/internal/items?registerId=&itemCode=REGISTRATION_FEE
    │   ← 拿到 itemId（如果有 status=0 的）
    └─ Feign payment-service.POST /api/payment/internal/items/{itemId}/pay
        ← 同链 B
```

**H. 患者侧兼容入口 `payMedication`（v3.2 补，原前端 `registration.ts:147-152` 调用）**
```
前端 POST /api/registration/{registerId}/pay-medication  [兼容入口]
  → registration.RegistrationController.payMedication [v3.2 改 Feign 透传]
    └─ 同 G，但 itemCode=MEDICATION_FEE
```

> 链 G/H 让既有前端 `PatientRegistration.vue` 和 `PatientPrescription.vue` 的支付按钮**无需改动**即可工作；新前端走 `/api/payment/**` 直接调 payment-service。v3.2（清理期）下线 G/H 兼容入口。

> **退款路径边界确认**（R4）：`RefundService.refund` / `refundByRegisterId` / `refundRegistrationFee`（L34-106）**完全不碰 register 表**，只动 expense_record + auth 余额。所以独立退款路径**不需要回调** registration —— 它本来就是收费员主动操作，不影响挂号状态。只有 `cancelRegistration`（L383 `updateStatus(id,4)`）才动 register，且是 registration 自己动，不需要回调。

### 2.3 服务边界一览

| 数据/动作 | 归属服务 |
|---|---|
| `expense_record` 表读写 | **payment-service**（唯一拥有 mapper） |
| `patient_balance_transaction` 表读写 | **auth-service**（不变） |
| `patient.account_balance` 列 | **auth-service**（不变） |
| `register` 表读写 | **registration-service**（不变） |
| 扣余额动作 | payment-service **Feign 调** auth-service（通过 Nacos 服务发现直连，不经网关） |
| 支付成功后更新 register 状态 | payment-service **Feign 回调** registration-service |
| 退号时退挂号费 | registration-service **Feign 调** payment-service |
| **管理员统计 `dailyTrend`**（v3.1 补）| `StatsMapper.xml:89-95` 当前直接 `FROM expense_record WHERE status=1`。迁到 payment-service `GET /api/payment/internal/stats/daily-charges` |
| **挂号费记录查询 `expense-records` 端点**（v3.1 补）| `RegistrationController.getExpenseRecords`（L169-179）走 `ExpenseRecordService` 5 个读方法。保留端点为兼容入口，内部 Feign 调 `GET /api/payment/internal/records` |
| **`fillPaymentStatus` 汇总 pay_status**（v3.1 补）| `RegistrationService.fillPaymentStatus`（L767-823）改为 Feign 调 `GET /api/payment/internal/items/summary?registerId=`，回调链 B/D 也保障数据一致 |

---

## 三、数据库

### 3.1 表变更：**真正零物理变更**（v3.2 已核实表结构）

`expense_record` 表结构（既有，不动）：
```sql
-- 既有表结构（来自 migrate_005_expense_record.sql + entity ExpenseRecord.java，v3.2 已核实）
CREATE TABLE expense_record (
    id BIGSERIAL PRIMARY KEY,
    register_id     INTEGER,                    -- 关联挂号号（幂等键的一部分）
    patient_id      INTEGER,
    patient_name    VARCHAR(64),
    category_id     BIGINT,
    category_name   VARCHAR(64),
    item_id         BIGINT,
    item_name       VARCHAR(128),
    item_code       VARCHAR(64),                -- REGISTRATION_FEE / MEDICATION_FEE / EXAMINATION_FEE(预留)（幂等键的一部分）
    quantity        INTEGER,
    unit_price      NUMERIC,
    total_amount    NUMERIC DEFAULT 0,
    status          SMALLINT NOT NULL DEFAULT 0,-- 0待缴费/1已缴费/2已退款/3已作废
    pay_time        TIMESTAMP,
    refund_time     TIMESTAMP,
    operator_id     INTEGER,
    operator_name   VARCHAR(64),
    remark          VARCHAR(255),
    create_time     TIMESTAMP DEFAULT NOW()
);
-- 既有索引：pkey, idx_expense_record_register_id, idx_expense_record_patient_id, idx_expense_record_status
-- 既有唯一索引（partial）：uq_expense_record_medication_fee ON (register_id) WHERE item_code='MEDICATION_FEE'
```

> **v3.2 关键修正**：v3.1 误以为表里有 `ref_id` / `ref_type` 列 — **没有**。整个文档已改为用既有 `(register_id, item_code)` 作幂等键。零 DDL 真正成立。

### 3.1.1 幂等性保证（不引入新索引）

| item_code | 既有保证 | v3.2 复用方案 |
|---|---|---|
| MEDICATION_FEE | `uq_expense_record_medication_fee`（partial unique on `register_id`）| payment-service `POST /internal/items` 用 `INSERT ON CONFLICT (register_id) WHERE item_code='MEDICATION_FEE' DO NOTHING RETURNING id`（既有 pharmacy 的写法就是这样，直接复用）|
| REGISTRATION_FEE | 无 DB 约束；应用层 `invalidateDuplicateRegistrationFees` 把重复行标 status=3 | payment-service 建费用项前先查 `SELECT id FROM expense_record WHERE register_id=? AND item_code='REGISTRATION_FEE' AND status IN (0,1)`，命中则返回既有 id（不重建）；唯一性靠"查询前置 + 应用层去重"保证，与现状一致 |
| EXAMINATION_FEE | 无 | 本次不接通，预留 |

### 3.2 数据归属变更（DB 角度无变化，代码角度）

| 表 | v3.2 前 | v3.2 后 |
|---|---|---|
| `expense_record` mapper | registration + pharmacy 各自直接读写 | **仅 payment-service 持有 mapper**，其他服务 Feign 调 |
| 历史 31 行数据 | 继续可读 | 继续可读（无任何变更，无 DDL，无新索引）|

### 3.3 不动的表（明确保护）

- ✅ `expense_record` — 物理表 + 所有既有索引**完全不动**
- ✅ `patient_balance_transaction` — 完全不动
- ✅ `pharmacy_transaction` — 与支付无关（药房库存流水）
- ✅ `register` — registration-service 独占

---

## 四、payment-service API

### 4.1 患者端 API（gateway 路由 `/api/payment`）

#### `GET /api/payment/orders` — 我的账单列表
**Query**：`patientId` Long required, `status` Integer optional, `page`/`size` 默认 1/10

**实现**：直接查 `expense_record WHERE patient_id=?` → 内存 `GROUP BY register_id` 聚合 → 排序 + 分页

**Response**：
```json
{ "code": 200, "data": {
  "list": [{
    "registerId": 1019,
    "patientName": "张三",
    "itemCount": 2,
    "paidItemCount": 1,
    "totalAmount": 170.00,
    "paidAmount": 20.00,
    "pendingAmount": 150.00,
    "status": 1,
    "statusName": "部分支付",
    "createTime": "2026-06-30T15:00:00",
    "items": [
      { "id": 41, "itemCode": "REGISTRATION_FEE", "itemName": "挂号费", "amount": 20.00, "status": 1, "statusName": "已支付", "payTime": "..." },
      { "id": 42, "itemCode": "MEDICATION_FEE", "itemName": "药品费", "amount": 150.00, "status": 0, "statusName": "待支付" }
    ]
  }],
  "total": 5, "page": 1, "size": 10
}}
```

#### `GET /api/payment/orders/{registerId}` — 单账单详情

#### `POST /api/payment/orders/{registerId}/items/{itemId}/pay` — 单项支付
**实现**：
1. `SELECT expense_record WHERE id=itemId AND status=0 FOR UPDATE`
2. 根据 `item_code`：
   - REGISTRATION_FEE → Feign `auth.deductBalance(businessType=REGISTRATION)`
   - MEDICATION_FEE → Feign `auth.deductBalance(businessType=MEDICATION)`
   - EXAMINATION_FEE → 返回 400「暂未开通」
3. `UPDATE expense_record SET status=1, pay_time=NOW() WHERE id=itemId`
4. **回调** registration-service `POST /api/registration/internal/{registerId}/on-fee-paid`（携带 itemCode/paidAmount），由 registration 决定是否更新 visit_state

**Response**：`{ success, itemId, paidAmount, accountBalance, orderPendingAmount }`

#### `POST /api/payment/orders/{registerId}/pay-all` — 全部支付
**实现**：拿到该 register 所有 `status=0` 的 item，**串行调用单项支付流程**，部分失败不中断，返回 `{ paidCount, failedCount, failedItems[] }`

### 4.2 内部 API（业务服务 Feign 用，前缀 `/api/payment/internal`）

> 网关层不放行，仅 service-to-service。

#### `POST /api/payment/internal/items` — 推送费用项（幂等，v3.2 修订）
```json
{
  "registerId": 88,
  "patientId": 5,
  "patientName": "张三",
  "itemCode": "MEDICATION_FEE",
  "itemName": "药品费",
  "amount": 55.00,
  "businessType": "MEDICATION"
}
```
**幂等性**（v3.2 关键修订）：复用既有约束，**不引入新索引**：
- `itemCode=MEDICATION_FEE` → SQL：`INSERT INTO expense_record (...) VALUES (...) ON CONFLICT (register_id) WHERE item_code='MEDICATION_FEE' DO NOTHING RETURNING id`；若 RETURNING 为空则 `SELECT id` 返回既有行
- `itemCode=REGISTRATION_FEE` → 先 `SELECT id FROM expense_record WHERE register_id=? AND item_code='REGISTRATION_FEE' AND status IN (0,1)`；命中则返回既有 id（不重建），无则 INSERT
- 其他 itemCode → 单纯 INSERT（无唯一约束，业务侧保证不重复调）

**响应**：`{ itemId: 41, created: true/false }`（created=false 表示命中既有行）

> **不传 refId**（v3.1 错误地引入了 ref_id 字段，但表里没这列 — v3.2 删除）。需要追溯原 prescription/register 的关联，用 `register_id`（既有列）即可。pharmacy 调用时 `registerId` 来自 prescription 的 `register_id` 字段。

#### `GET /api/payment/internal/items?registerId=&itemCode=&status=` — 内部查询
返回 item 列表（供 pharmacy 判断药品费是否已付、registration 查挂号费状态）

#### `GET /api/payment/internal/items/by-register?registerId=&itemCode=` — 按挂号+类型查（v3.2 改名）
（pharmacy.assertMedicationPaid 用：`?registerId=88&itemCode=MEDICATION_FEE` 查状态）

#### `GET /api/payment/internal/items/summary?registerId=` — 汇总状态（v3.1 新增，v3.2 保留）
返回 `{ registerId, totalAmount, paidAmount, pendingAmount, payStatus, payStatusName }`
（registration 的 `fillPaymentStatus` + 回调 `on-fee-paid` 用）

#### `POST /api/payment/internal/items/{itemId}/pay` — 内部触发支付
（registration-service 的 `tryBalancePayment` 用，挂号时余额够就直接扣）
**实现要点（v3.2）**：与外部患者 `payItem` 共用同一方法 — `SELECT ... FOR UPDATE` + 二次校验 `status==0`，避免双扣

#### `POST /api/payment/internal/items/{itemId}/refund` — 退款
（registration-service 的 `cancelRegistration` / `RefundService` 用）

#### `POST /api/payment/internal/write-token` — 写令牌（v3.1 新增，v3.2 强制 Redis）
**body**：`{ registerId, holder, ttlSeconds: 30 }`
**响应**：`201 { token }` 或 `409 { holder, expiresAt }`
**实现**：**强制 Redis** `SET key value NX EX 30`（v3.1 允许进程内 ConcurrentHashMap，v3.2 修订 — 重启 race 会导致双扣窗口重开）
**使用方**：
- registration-service `ChargeService.charge`（cashier 集中收费）— 必须**先**申请令牌
- payment-service `payItem`（患者自助支付）— **不**申请令牌（见下）
- 写完后 `DEL key` 或让其自然过期

> **为什么 payItem 不走 token？** 患者侧并发场景（单项支付 ↔ 全部支付）由 payment-service 内部 `SELECT ... FOR UPDATE` + 二次校验保护，行级锁已足够。write-token 只用来跨服务协调（payment 与 registration 的 charge 不能同时写同一 register）。这样把"跨服务互斥"（用 token）和"单服务并发"（用 DB 锁）分开，避免 token 滥用。

#### `GET /api/payment/internal/records?patientId=&registerId=&status=&startDate=&endDate=` — 历史记录查询（v3.1 新增）
（替代 `ExpenseRecordService.queryExpenseRecords` 的 5 个读方法）

#### `GET /api/payment/internal/stats/daily-charges?startDate=&endDate=` — 每日收费统计（v3.1 新增）
（替代 `StatsMapper.xml:89-95` 的硬编码 SQL）

#### 定时清理任务（v3.1 新增，v3.2 修订窗口判断）
**频率**：每 5 分钟
**逻辑**：`UPDATE expense_record SET status=3 WHERE status=0 AND register_id NOT IN (SELECT id FROM register) AND create_time < NOW() - INTERVAL '10 min'`
**in-flight 安全性**（v3.2 R3）：调用链 A 中 Feign `createItem` 在 `createRegistration` 的 `@Transactional` **内**调用（事务未提交）— 因此如果事务最终回滚，register 永远不存在，orphan expense 行确实会被本任务清理；如果事务提交成功，register 一定先于 expense 出现（INSERT 在 Feign 前），10 分钟窗口足够覆盖任何合理的事务提交延迟。
（清理 §A 链路事务回滚留下的 orphan 行）

---

## 五、registration-service 改造点

### 5.1 删除/替换的代码

| 文件 | 现有 | 改造为 |
|---|---|---|
| `mapper/ExpenseRecordMapper.java` | MyBatis 接口 | **删除**（移到 payment-service） |
| `resources/mapper/ExpenseRecordMapper.xml` | SQL | **删除** |
| `entity/ExpenseRecord.java` | 实体类 | **删除**（如还在 controller DTO 里用，保留为 DTO） |
| `service/ExpenseRecordService.java`（5 个读方法，L24-158）| 直查 SQL | **改 Feign** 调 `payment-service.internal.records`（v3.1 补：v3.0 漏列） |
| `resources/mapper/StatsMapper.xml`（L89-95 `dailyTrend.charge` 子查询）| 硬编码 `FROM expense_record` | **改 Feign** 调 `payment-service.internal.stats.daily-charges`（v3.1 补：v3.0 漏列） |
| `service/ChargeService.java` L35-127, L141-203 | 直写 SQL + Feign auth | **大幅重写**：只保留 cashier `charge` 路径；`payRegistration` / `payMedication` 标记为 @Deprecated，透传到 payment-service |
| `service/ChargeService.java` L213-265（cashier `charge`）| 直写 expense_record + register | **保留**，但调用前必须先 `POST /api/payment/internal/write-token` 申请令牌（v3.1 R2 修订）|
| `service/RefundService.java` L35-106 | 直写 SQL + Feign auth | **改 Feign** payment-service.refund。**v3.1 确认**：此类不碰 register，无需回调 |
| `service/RegistrationService.java` L54-298 `createRegistration` | 建单 + 写费用一个 `@Transactional`（L221 insert + L229 createRegistrationFee + L230 tryBalancePayment）| **v3.2 改造**：保留既有 `selectNextSequenceValue/syncIdSequence`（L201-219）+ `useGeneratedKeys` 回填 id 不动；L229 `createRegistrationFee` 改为 Feign 调 `payment.internal.items`（registerId=回填后的 register.id）；L230 `tryBalancePayment` 改为 Feign 调 `payment.internal.items/{id}/pay`；Feign 调用在 `@Transactional` 内但回调 `on-fee-paid` 必须 `afterCommit` |
| `service/RegistrationService.java` L767-823 `fillPaymentStatus` | 直读 expense_record 汇总 payStatus | **改 Feign** 调 `payment-service.internal.items.summary`（v3.1 R3 修订）|
| `controller/RegistrationController.java` L109-200 | 支付相关 6 个端点 | **保留**作为兼容入口（Feign 透传 payment-service），或前端改造完后下线 |
| `feign/AuthPatientFeignClient.java` | 共享 | **保留**（registration 退号流程仍要用） |
| **新增** `feign/PaymentFeignClient.java` | — | 调 payment-service 的所有内部 API |

### 5.2 新增的端点：`POST /api/registration/internal/{registerId}/on-fee-paid`

payment-service 支付成功后回调，body：`{ itemCode, itemId, paidAmount, operatorId }`

**registration 内部逻辑**（v3.2 修订 — 幂等）：
- **总是** Feign 调 `payment-service.internal.items.summary?registerId=` 重新汇总
- 若汇总结果显示"已无待缴"且 `register.visit_state==1` → `updateStatus(id, 2)`（接诊中）+ `updatePayStatus(id, 2)`
- 若仍有待缴项 → 仅更新 `pay_status`（部分缴费），不动 visit_state
- **幂等性**（v3.2 R1 补）：汇总 + update 本身就幂等 — 多次回调同一 itemId，重新算出的 pay_status 一致，UPDATE 是 idempotent。无需额外去重表。
- 若回调失败 → payment-service 重试 3 次；仍失败则将 itemId 加入 `expense_record.remark` 的 `callback_pending:待回调` 标记，定时任务每 5min 重试

> **v3.0 → v3.1 → v3.2 修订史**：
> - v3.0（错）：MEDICATION_FEE 不回调
> - v3.1（修正）：所有 itemCode 都回调，但回调逻辑按 itemCode 分支
> - **v3.2（最终）**：回调统一触发"重新汇总"，由 summary 结果决定 visit_state。把"哪种 item 改 visit_state"的判断收敛到一处 — 汇总服务。这样收费员 `charge` 路径（链 F）和 payment-service `payItem`（链 B/D）只要任一处触发状态变更，registration 状态都最终一致。

### 5.3 回调归属与事务边界（v3.2 R3-2 澄清）

"on-fee-paid 回调放 afterCommit" 这句话有两种语境，必须分清：

| 场景 | 回调发起方 | afterCommit 在哪个事务 | 实现方式 |
|---|---|---|---|
| **链 A** 挂号时 tryBalancePayment 自动扣款成功 | registration-service | registration 的 `@Transactional`（createRegistration）| registration 在调 Feign `internal/items/{id}/pay` 后，注册本事务的 `afterCommit` 钩子（参考既有 L244-265 `registerSynchronization` 模式）→ 钩子内 Feign 调 `payment.internal.items.summary` 重算 + UPDATE register 状态。**这条路径根本不需要 payment-service 反向回调**，registration 自己就能完成 |
| **链 B/D** 患者自助支付成功 | payment-service | payment-service 自己的 `@Transactional`（payItem）| payment-service 在自己事务 `afterCommit` 钩子里 Feign 调 `registration./api/registration/internal/{registerId}/on-fee-paid`。registration 收到后**同步**走 §5.2 逻辑：调 `payment.summary` 重算 → UPDATE register |

**关键差别**：链 A 中 payment-service 的 `payItem` Feign 返回时 registration 事务**还没提交**，所以 registration 用本地 afterCommit 钩子就够；链 B/D 中 registration 完全没参与 payment 的事务，必须靠 payment-service 的反向回调通知。

### 5.4 Feign 调用的事务延迟（v3.2 R3-1 风险声明）

链 A 在 `@Transactional` 内调两次 Feign（createItem + tryBalancePayment），register 行的事务窗口会被撑开。

**约束**：
- 每次 Feign 调用超时 **≤ 3 秒**（Feign client 配置）
- 整个 createRegistration 事务预算 **≤ 8 秒**
- 患者端列表查询走 **READ COMMITTED**（PostgreSQL 默认）— 未提交的 register 行不会被其他事务看到，不会被阻塞
- 写操作（如另一个挂号请求）受影响的是 `register_id_seq` 序列锁，但既有 `syncIdSequence` 逻辑已经处理

> 这是为换"原子性 + 单一数据所有权"付出的代价。监控 `createRegistration` 的 p99 延迟，超 5s 报警。

---

## 六、pharmacy-service 改造点

### 6.1 替换的代码

| 文件 | 现有 | 改造为 |
|---|---|---|
| `mapper/ExpenseRecordMapper.java` | 直读 expense_record（INSERT ON CONFLICT + SELECT status）| **删除** |
| `resources/mapper/ExpenseRecordMapper.xml` | SQL | **删除** |
| `service/PharmacyService.java` L614-676 | `ensureMedicationFeeCreated` / `assertMedicationPaid` 直读写 | **改 Feign** 调 payment-service |
| **新增** `feign/PaymentFeignClient.java` | — | 调 payment-service |

### 6.2 调用替换

| 旧调用 | 新调用（v3.2） |
|---|---|
| `expenseRecordMapper.insertMedicationFee(registerId, ...)` | `paymentFeignClient.createItem({ registerId: prescription.registerId, itemCode: MEDICATION_FEE, amount, ... })` |
| `expenseRecordMapper.selectMedicationFeeStatus(registerId)` | `paymentFeignClient.getItemByRegister(registerId, MEDICATION_FEE)` |
| `PharmacyService.assertMedicationPaid` | 调上面 Feign + 检查 status==1 |

> **v3.2 修正**：v3.1 这里残留了 `refId: prescriptionId` — 表里没这列。MEDICATION_FEE 通过 `register_id` 关联，pharmacy 想反查时用 `register_id` 即可（一个挂号至多一条药品费，由 `uq_expense_record_medication_fee` 保证）。处方本身的明细（哪些药品）仍在 pharmacy 自己的 `prescription` 表里，与 expense_record 解耦。

---

## 七、前端

### 7.1 新增

- **路由** `/patient/payment`，name=`PatientPayment`，meta.roles=`['patient']`
- **侧边栏入口** `PatientLayout.vue` quickActions 加 `{ label: '我的账单', icon: 'Wallet', route: '/patient/payment' }`
- **页面** `modules/patient/pages/PatientPayment.vue`（复用 PatientPrescription.vue 的 GlassCard + family-tabs + split-grid 1:1 设计）
- **API 模块** `shared/api/modules/payment.ts`：
  ```ts
  paymentApi.orders(query)
  paymentApi.orderDetail(registerId)
  paymentApi.payItem(registerId, itemId)
  paymentApi.payAll(registerId)
  ```
- **类型** `shared/types/payment.ts`：PaymentOrder / PaymentOrderItem / PayResult

### 7.2 既有改造（API 重定向）

`shared/api/modules/registration.ts` 中以下函数**保留函数名但内部指向 paymentApi**（渐进式迁移）：
- `payRegistration(id)` → `paymentApi.payItem(registerId, regFeeItemId)`（需要先 getItemByRef 查 itemId）
- `payMedication(registerId)` → 同上指向 MEDICATION_FEE item
- `expenseRecords(query)` → `paymentApi.orders` 或保留 registration-service 兼容端点

`PatientRegistration.vue` 和 `PatientPrescription.vue` 的现有按钮**不需要改**，API 函数内部重定向后自动生效。

### 7.3 页面结构（关键节点）

```
<GlassCard>
  <header>我的账单 / 查看每个挂号的所有费用和支付状态</header>
  <FamilyTabs v-if="patients.length > 1" />
  
  <SplitGrid template="1fr 1fr">
    <Pane class="pane--list">
      <!-- 账单卡片：挂号号 + StatusTag + 待付金额 -->
    </Pane>
    <Pane class="pane--detail">
      <!-- ElDescriptions 头 + ElTable items + 底部一次支付 -->
    </Pane>
  </SplitGrid>
</GlassCard>
```

金额统一 `toFixed(2)`，待付用 `--color-primary`、已付用 `--color-success`、状态用 StatusTag。

---

## 八、风险与缓解（v3.2 修订）

| 风险 | 严重度 | 缓解 |
|---|---|---|
| `createRegistration` 跨服务事务原子性丢失 | ⚠️ 中 | ① Feign `createItem` 在 `@Transactional` 内，事务回滚后 register 不存在；② payment-service 定时任务（每 5min）扫 `status=0 AND register_id NOT IN register AND create_time < NOW()-10min` 标 status=3 清理；③ tryBalancePayment 扣款后若事务回滚，registration 在 rollback handler 里调 `payment.refund` 补偿 |
| 收费员 `charge` 与 payment `payItem` 双写竞态 → 双重扣款 | ⚠️ 中 | ① write-token **强制 Redis** `SET NX EX 30`（v3.2 修订，不允许内存）；② payment-service 内部 `SELECT ... FOR UPDATE` + 二次校验 `status==0`；③ 409 时收费员界面提示"患者正在自助支付" |
| payment-service → registration 回调失败 | ⚠️ 低 | 重试 3 次；仍失败在 `expense_record.remark` 打 `callback_pending:待回调` 标记，定时任务每 5min 重试。**回调本身幂等**（v3.2 R1）— 多次重算 summary 不影响最终状态 |
| pharmacy 不知 medication_fee 已被外部改 | ⚠️ 中 | 改造前与队友对齐；保留 `assertMedicationPaid` 接口语义不变 |
| `fillPaymentStatus` 汇总断链 | ⚠️ 低 | 改 Feign 调 `payment.internal.items.summary`；所有支付（含 MEDICATION_FEE）都触发回调重算 |
| payment-service 故障 | ⚠️ 中 | 业务服务 Feign 调用降级（保留旧 mapper 一段时间，标记 @Deprecated） |
| 患者侧并发（单项支付 ↔ 全部支付同一 register）| ⚠️ 低 | 不走 write-token；payment-service 内部 `SELECT ... FOR UPDATE` + 二次校验保护；pay-all 串行调 payItem，天然互斥 |
| Feign 调用在 @Transactional 内撑开事务窗口（v3.2 R3-1）| ⚠️ 中 | Feign 超时 ≤3s/次；总预算 ≤8s；READ COMMITTED 隔离下未提交行不阻塞读；监控 createRegistration p99 >5s 报警 |
| pay-all 与 cancelRegistration 竞态（v3.2 R3-3）| ⚠️ 低 | 两者都通过 `SELECT ... FOR UPDATE` 锁 expense_record item 行，行级锁天然互斥；pay-all 先锁 item1 → cancel 等 → pay-all 拿 item2 → cancel 拿到锁后看到 status=1 已支付走退款流程，或 status=0 走"无费用可退"分支 |
| Scenario Z：cancelRegistration 不退药品费 | ⚠️ 低 | 设计如此（处方可能已消费）；文档已明示；患者需走独立 `RefundService.refund` 退款 |

---

## 九、迁移步骤（生产环境）

### 阶段 1：建 payment-service（无侵入）
1. 新建 payment-service :8096，注册 Nacos
2. 移植 `ExpenseRecordMapper` / `ExpenseRecord` entity 到 payment-service（**代码迁移，表不动**）
3. 实现 payment-service 所有内部 API（含 `write-token` 端点强制 Redis + 定时清理任务）+ 患者 API
4. gateway 加 `/api/payment/**` 路由

### 阶段 2：registration-service 改造
5. 加 `PaymentFeignClient`（含 JWT 透传拦截器）
6. **保留**既有 `ExpenseRecordMapper`（双写期），新增"走 Feign"开关
7. `ChargeService.payRegistration` / `payMedication` 改为透传 payment-service（链 G/H）
8. **`ChargeService.charge`（cashier）改造**：调用前先申请 `write-token`；如失败提示用户
9. `RefundService` 改 Feign（不碰 register，无需回调）
10. `RegistrationService.createRegistration` 改造：保留既有 `selectNextSequenceValue/syncIdSequence` 不动；L229 createRegistrationFee 改 Feign `payment.internal.items`（registerId=useGeneratedKeys 回填 id）；L230 tryBalancePayment 改 Feign `payment.internal.items/{id}/pay`；回调 on-fee-paid 放 afterCommit
11. `RegistrationService.fillPaymentStatus` 改 Feign 调 `internal.items.summary`
12. `RegistrationService.cancelRegistration` 改 Feign payment refund
13. **`ExpenseRecordService` 5 个读方法改 Feign** `internal.records`
14. **`StatsMapper.xml` `dailyTrend.charge` 改 Feign** `internal.stats.daily-charges`
15. 新增 `POST /api/registration/internal/{registerId}/on-fee-paid` 端点（幂等）

### 阶段 3：pharmacy-service 改造
16. 加 `PaymentFeignClient`
17. `ensureMedicationFeeCreated` / `assertMedicationPaid` 改 Feign
18. **保留**既有 mapper 一段时间作为降级 fallback

### 阶段 4：前端上线
19. 新增 `payment.ts` + `payment.ts` 类型
20. 上线 `PatientPayment.vue` + 路由 + 导航
21. `registration.ts` 内部函数重定向到 `paymentApi`

### 阶段 5：清理（v3.3）
22. 监控稳定 1-2 周后，删除 registration / pharmacy 中的 `ExpenseRecordMapper`
23. 收费员 `charge` 路径迁到 payment-service（移除 write-token，统一单写者）

---

## 十、验证清单（开发完成后用子 agent 跑）

### 数据保全
- [ ] 现有 31 条 expense_record 数据在新接口下完整可见
- [ ] 现有 38 条 patient_balance_transaction 数据无任何变更
- [ ] `pharmacy_transaction` 表完全未被触碰

### 服务边界
- [ ] registration-service 的 `ExpenseRecordMapper` 已删除或仅留降级版本
- [ ] pharmacy-service 的 `ExpenseRecordMapper` 已删除或仅留降级版本
- [ ] payment-service 是唯一拥有 `ExpenseRecordMapper` 的服务
- [ ] payment-service 不持有 `patient_balance_transaction` 表的 mapper（auth 独占）
- [ ] `StatsMapper.xml` 不再直接 `FROM expense_record`（v3.1）
- [ ] `ExpenseRecordService` 不再直接读 mapper（v3.1）

### 端到端流程
- [ ] 单项支付端到端跑通（患者→payment→auth→expense→registration 回调）
- [ ] 全部支付部分失败时返回结构正确
- [ ] 退号流程跑通（registration→payment→auth→expense.status=2）
- [ ] 药品费发药前阻塞校验生效（pharmacy.assertMedicationPaid 走 Feign）
- [ ] 收费员 `charge` 流程在 write-token 持有时跑通（v3.1）
- [ ] write-token 冲突场景：自助支付进行中 → 收费员收到 409 + 友好提示（v3.1）
- [ ] createRegistration 失败回滚后，5 分钟内 orphan expense 行被清理为 status=3（v3.1）
- [ ] MEDICATION_FEE 支付成功 → registration 回调触发 → `fillPaymentStatus` 重算正确（v3.1）

### 前端
- [ ] "我的账单"页面家属切换、单项支付、全部支付、空状态全部正常
- [ ] `PatientRegistration.vue` 和 `PatientPrescription.vue` 既有支付按钮仍可工作

---

## 十一、变更记录

| 版本 | 日期 | 内容 |
|---|---|---|
| v1.0 | 2026-07-01 | 初版，新建 pay_order + pay_order_item 双表（废弃） |
| v2.0 | 2026-07-01 | payment-service 无状态 Feign 编排层（用户嫌"没建表"） |
| v3.0 | 2026-07-01 | 方案 B：表归属转移。expense_record 物理不动，读写主服务改为 payment-service |
| v3.1 | 2026-07-01 | 一轮验证后修订：补 ExpenseRecordService + StatsMapper 两处漏点；charge 双写期加 write-token；createRegistration 加 nextval + 补偿 + 清理；fillPaymentStatus 改 Feign summary；所有 itemCode 都回调 |
| **v3.2** | 2026-07-01 | **二轮验证后修订（关键）**：修正 v3.1 误判 expense_record 有 ref_id 列 — 实际表里没有；幂等键改用既有 (register_id, item_code)，复用既有 `uq_expense_record_medication_fee` 索引，**真正零 DDL**；write-token 强制 Redis（重启 race 防护）；回调本身幂等（重算 summary）；取消 nextval 预生成（与既有 syncIdSequence 冲突），改 useGeneratedKeys + Feign 在 @Transactional 内 + 定时清理 orphan；补链 G/H 兼容入口；表述修正（Feign 走 Nacos 不经网关）|

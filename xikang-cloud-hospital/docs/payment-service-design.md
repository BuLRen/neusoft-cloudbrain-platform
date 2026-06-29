# payment-service 支付中心模块设计文档

> 版本：v0.1（设计稿，待确认后进入实现）
> 日期：2026-06-29
> 状态：**设计阶段 — 等待用户确认**

---

## 一、背景与目标

### 1.1 现状问题

当前系统的"支付"能力**分散且不完整**：

| 能力 | 现状 |
|------|------|
| 挂号费支付 | ✅ 已实现（registration-service） |
| 药品费支付 | ✅ 已实现（pharmacy-service 写 expense_record） |
| 检查/检验/处置费 | ❌ **未接入费用体系**（医生开单不产生费用记录） |
| 患者端"待支付订单"页 | ❌ **缺失**（后端 API 已有，前端无聚合页） |
| 医生工作站费用展示 | ❌ **缺失** |
| 强阻塞卡点 | ❌ **不完整**（仅药房发药前校验，医技执行前、确诊前无校验） |
| 支付逻辑归属 | ⚠️ 散落在 registration-service（Charge/Refund/ExpenseRecord）+ pharmacy-service |

### 1.2 目标

建立一个**统一的支付中心** `payment-service`，实现：

1. **集中托管**所有费用相关逻辑（出账、支付、退款、查询、状态校验）
2. **覆盖全费用类型**：挂号费、检查费、检验费、处置费、药品费
3. **强阻塞闭环**：未支付不能继续看诊 / 执行检查 / 发药
4. **按挂号号合并**：一个就诊号 = 一个聚合订单，患者一次支付结清
5. **双入口支付**：患者自助 + 收费员代收
6. **模拟支付**：点击即支付（走通流程，不接真实支付网关）

### 1.3 非目标（本期不做）

- 真实支付网关对接（微信/支付宝）
- 医保结算
- 发票打印
- 退款审批流（保持现有"直接退"模式）

---

## 二、架构定位

### 2.1 服务基本信息

| 项 | 值 |
|----|----|
| 服务名 | `payment-service` |
| 端口 | **8096**（业务端口段紧邻 8095 schedule-service） |
| 基包 | `com.xikang.payment` |
| 父 POM | 继承 `xikang-cloud-hospital`（需在根 pom.xml 的 `<modules>` 追加） |
| 启动类 | `PaymentApplication`（`@EnableDiscoveryClient` + `@EnableFeignClients`） |

### 2.2 服务边界（职责划分）

```
┌─────────────────────────────────────────────────────────────┐
│                    payment-service                          │
│  ─ 费用明细出账（被各业务服务通过 Feign 调用）                │
│  ─ 聚合订单查询（按 patientId / registerId）                 │
│  ─ 支付（患者自助 / 收费员代收）                             │
│  ─ 退款                                                      │
│  ─ 支付状态校验（被各业务服务在卡点调用）                    │
│  ─ expense_record 表的所有权                                 │
└─────────────────────────────────────────────────────────────┘
            ▲ Feign                ▲ Feign              ▲ Feign
            │                      │                    │
   ┌────────┴───────┐    ┌─────────┴────────┐   ┌───────┴────────┐
   │ physician-svc  │    │ medtech-service   │   │ pharmacy-svc   │
   │ 开检查/检验/   │    │ 执行前校验已支付  │   │ 发药前校验已支付│
   │ 处置/处方时    │    │                   │   │                │
   │ 推送费用       │    │                   │   │                │
   └────────────────┘    └───────────────────┘   └────────────────┘
            │                      │                    │
            └──────────┬───────────┴────────────────────┘
                       ▼ Feign
           ┌───────────────────────────┐
           │       auth-service         │
           │  ─ 患者钱包 account_balance│
           │  ─ 流水 patient_balance_   │
           │    transaction（幂等）     │
           │  ─ /balance/deduct|refund  │
           └───────────────────────────┘
```

**核心原则**：
- `payment-service` 是**业务编排层**，不持有钱包表（钱包仍在 auth-service）
- `expense_record` 表的**所有权转移到 payment-service**（registration-service 和 pharmacy-service 现有的 ExpenseRecordMapper 将改为 Feign 调用 payment-service）
- 所有资金动作（扣款/退款）通过 Feign 调 auth-service 的钱包接口，复用其幂等机制

### 2.3 与 auth-service 钱包的关系

`auth-service` 已经是钱包的事实标准（`patient_balance_transaction` 表带业务幂等唯一索引）。

**payment-service 不复制钱包能力**，只做：
- 接收业务服务的"出账"请求 → 写 `expense_record`（status=0 待缴费）
- 接收"支付"请求 → 调 auth-service `deductBalance` → 成功后更新 `expense_record.status=1`
- 接收"退款"请求 → 调 auth-service `refundBalance` → 更新 `expense_record.status=2`

---

## 三、数据库设计

### 3.1 复用现有表（不改动）

#### `expense_record`（费用明细表 — payment-service 接管所有权）

来源：`docker/init-db/migrate_005_expense_record.sql`

```sql
CREATE TABLE expense_record (
    id              SERIAL          PRIMARY KEY,
    register_id     INTEGER,                              -- 挂号单ID（聚合键）
    patient_id      BIGINT,
    patient_name    VARCHAR(64),
    category_id     INTEGER,
    category_name   VARCHAR(64),                          -- 挂号费/检查费/检验费/处置费/药品费
    item_id         INTEGER,                              -- 项目ID（medical_technology.id / 药品ID / 挂号级别ID）
    item_name       VARCHAR(128)    NOT NULL,
    item_code       VARCHAR(64),                           -- REGISTRATION_FEE/CHECK_FEE/...
    quantity        INTEGER         NOT NULL DEFAULT 1,
    unit_price      DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    total_amount    DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    status          SMALLINT        NOT NULL DEFAULT 0,   -- 0待缴费/1已缴费/2已退款/3已作废
    pay_time        TIMESTAMP,
    refund_time     TIMESTAMP,
    operator_id     BIGINT,
    operator_name   VARCHAR(64),
    remark          VARCHAR(255),
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_expense_record_status CHECK (status IN (0,1,2,3))
);
```

### 3.2 `item_code` 取值规范（本期新增）

| item_code | category_name | 来源业务 | 触发服务 |
|-----------|---------------|----------|----------|
| `REGISTRATION_FEE` | 挂号费 | 挂号 | registration-service（已有，迁移） |
| `CHECK_FEE` | 检查费 | 医生开检查单 | **physician-service（新增）** |
| `INSPECTION_FEE` | 检验费 | 医生开检验单 | **physician-service（新增）** |
| `DISPOSAL_FEE` | 处置费 | 医生开处置单 | **physician-service（新增）** |
| `MEDICATION_FEE` | 药品费 | 开处方 | pharmacy-service（已有，迁移） |

### 3.3 新增迁移脚本

文件：`docker/init-db/migrate_022_payment_service_expense_indexes.sql`

**目的**：
1. 为检查/检验/处置费补幂等唯一索引（参考已有的 `migrate_013_medication_fee_unique.sql` 模式），防止同一申请单重复出账
2. 在 `expense_record` 加 `source_id` 字段记录业务来源 ID（如 check_request.id），用于幂等定位

```sql
-- 1. 新增来源业务ID字段，用于幂等定位（检查/检验/处置申请单ID）
ALTER TABLE expense_record
    ADD COLUMN IF NOT EXISTS source_id INTEGER;

-- 2. 检查费幂等：同一 register_id + 同一 check_request.id 只能有一条 CHECK_FEE
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_record_check_fee
    ON expense_record (register_id, source_id)
    WHERE item_code = 'CHECK_FEE';

-- 3. 检验费幂等
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_record_inspection_fee
    ON expense_record (register_id, source_id)
    WHERE item_code = 'INSPECTION_FEE';

-- 4. 处置费幂等
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_record_disposal_fee
    ON expense_record (register_id, source_id)
    WHERE item_code = 'DISPOSAL_FEE';

-- 5. 聚合查询索引（按患者+状态查待支付）
CREATE INDEX IF NOT EXISTS idx_expense_record_patient_status
    ON expense_record (patient_id, status);
```

> 注意：`MEDICATION_FEE` 已有 `migrate_013` 的唯一索引（按 `register_id`），保持不变。`REGISTRATION_FEE` 由 registration-service 内部 `invalidateDuplicateRegistrationFees` 逻辑保证唯一，保持不变。

### 3.4 不新建独立的 `order` 表

**设计决策**：不新建订单表。"订单"概念通过**视图层聚合**实现 —— 同一 `register_id` 下所有 `status=0` 的 `expense_record` 行即为该就诊号的"待支付订单"。

**理由**：
- 避免双写（订单表 + 明细表）的一致性问题
- `expense_record` 的 `register_id` 天然是聚合键
- 聚合查询一条 SQL 即可（见 4.3 节）

---

## 四、API 接口设计

所有接口前缀：`/api/payment`，统一返回 `Result<T>`（`com.xikang.common.result.Result`）。

### 4.1 出账接口（被业务服务通过 Feign 调用）

#### `POST /api/payment/expense/create` — 创建费用明细（单条）

**调用方**：registration-service（挂号费）、physician-service（检查/检验/处置费）、pharmacy-service（药品费）

**请求体**：
```json
{
  "registerId": 1001,
  "patientId": 2001,
  "patientName": "张三",
  "itemCode": "CHECK_FEE",
  "categoryName": "检查费",
  "itemId": 3001,
  "itemName": "血常规检查",
  "sourceId": 5001,
  "quantity": 1,
  "unitPrice": 50.00,
  "totalAmount": 50.00,
  "remark": "医生工作站开单"
}
```

**响应**：`Result<ExpenseRecordVO>`（含生成的 id、status）

**幂等**：由唯一索引保证（见 3.3），重复创建会被拦截并返回已存在的记录。

#### `POST /api/payment/expense/create-batch` — 批量创建

用于处方多药品项、一次开多检查项的场景。

### 4.2 支付接口

#### `POST /api/payment/pay/register/{registerId}` — 按挂号号支付（聚合支付）

**核心接口**：一次结清该就诊号下所有待缴费明细。

**流程**：
1. 查询该 `register_id` 下所有 `status=0` 的明细，汇总 `total_amount`
2. 若金额为 0，返回"无需支付"
3. Feign 调 auth-service `deductBalance`（`businessType="VISIT_PAYMENT"`, `businessId=registerId`）
4. 钱包扣款成功 → 批量更新 `expense_record.status=1, pay_time=now()`
5. 返回支付结果（含支付明细、余额）

**请求体**（可选）：
```json
{
  "operatorId": 2001,
  "operatorName": "张三（患者自助）",
  "payMethod": "BALANCE"
}
```

**响应**：`Result<PaymentResultVO>`
```json
{
  "registerId": 1001,
  "totalAmount": 280.00,
  "paidAmount": 280.00,
  "accountBalance": 720.00,
  "paidItems": 4,
  "payTime": "2026-06-29T10:30:00"
}
```

#### `POST /api/payment/pay/item/{expenseId}` — 单条支付（可选，用于部分支付场景）

按单条 `expense_record.id` 支付。本期**默认走聚合支付**，单条支付预留。

### 4.3 查询接口

#### `GET /api/payment/pending/patient/{patientId}` — 患者所有待支付（按挂号号分组）

**响应**：`Result<List<RegisterOrderVO>>`
```json
[
  {
    "registerId": 1001,
    "patientName": "张三",
    "visitDate": "2026-06-29",
    "deptmentName": "内科",
    "employeeName": "李医生",
    "totalAmount": 280.00,
    "itemCount": 4,
    "items": [
      {"id": 1, "itemCode": "REGISTRATION_FEE", "itemName": "挂号费", "amount": 30.00},
      {"id": 2, "itemCode": "CHECK_FEE", "itemName": "血常规", "amount": 50.00},
      {"id": 3, "itemCode": "INSPECTION_FEE", "itemName": "尿常规", "amount": 40.00},
      {"id": 4, "itemCode": "MEDICATION_FEE", "itemName": "药品费", "amount": 160.00}
    ]
  }
]
```

#### `GET /api/payment/pending/register/{registerId}` — 单挂号号待支付详情

#### `GET /api/payment/records` — 费用流水查询（原有 `expense-records` 迁移）

支持 `patientId` / `registerId` / `status` / 时间区间过滤。

### 4.4 状态校验接口（被业务服务在卡点调用）

#### `GET /api/payment/check-paid/register/{registerId}` — 校验挂号号是否全部已付清

**响应**：`Result<PaymentStatusVO>`
```json
{
  "registerId": 1001,
  "allPaid": false,
  "pendingAmount": 100.00,
  "pendingItems": 2,
  "pendingItemCodes": ["CHECK_FEE", "MEDICATION_FEE"]
}
```

#### `GET /api/payment/check-paid/item` — 校验指定明细是否已付

**参数**：`registerId` + `itemCode`（可选 `sourceId`）

用于医技执行前只校验"检查费已付"、药房发药前只校验"药品费已付"的细粒度场景。

### 4.5 退款接口（迁移自 registration-service）

- `POST /api/payment/refund` — 单条退费
- `POST /api/payment/refund/register/{registerId}` — 按挂号号退全部
- `POST /api/payment/refund/registration-fee/{registerId}` — 仅退挂号费（取消挂号场景）

### 4.6 收费员代收接口

#### `POST /api/payment/charge` — 窗口收费

迁移自现有 `/api/registration/charge`。收费员批量将 `status: 0 → 1`，可指定不扣余额（现金/刷卡场景）。

---

## 五、强阻塞卡点设计

### 5.1 卡点位置与校验方式

| # | 卡点位置 | 校验调用 | 校验粒度 | 阻断行为 |
|---|----------|----------|----------|----------|
| 1 | 挂号完成后医生接诊前 | `check-paid/register/{registerId}` | REGISTRATION_FEE | 医生工作站看不到该患者 / 接诊按钮置灰 |
| 2 | 医生开检查/检验/处置单时 | （不卡，开单即出账） | — | 开单时同步写 expense_record |
| 3 | 医生**确诊/结束看诊**前 | `check-paid/register/{registerId}` | 全部付清 | 阻断确诊，提示患者先支付 |
| 4 | 医技**执行检查/检验**前 | `check-paid/item?registerId&itemCode=CHECK_FEE` | 对应费用项 | 阻断执行，提示未付费 |
| 5 | 药房**发药**前 | `check-paid/item?registerId&itemCode=MEDICATION_FEE` | 药品费 | 阻断发药（现有逻辑迁移） |

### 5.2 卡点实现模式（统一规范）

业务服务在卡点处通过 Feign 调 payment-service：

```java
// 示例：physician-service 确诊前校验
PaymentStatusVO status = paymentFeignClient.checkPaidByRegister(registerId);
if (!status.isAllPaid()) {
    throw new BusinessException(4001,
        "该患者有待支付费用未结清（" + status.getPendingAmount() + "元），请先支付后再确诊");
}
```

错误码规范：`4001` = 待支付未结清，`4002` = 钱包余额不足，`4003` = 重复支付，`4004` = 退费失败。

### 5.3 例外处理

- **急诊绿色通道**（本期不实现，预留）：`register` 表可加 `is_emergency` 字段，急诊挂号跳过卡点 1，但卡点 3/4/5 仍保留
- **部分支付后允许继续**：聚合支付是"全付"才解除阻塞，单条支付（4.2）预留后可支持部分解除

---

## 六、服务间 Feign 客户端

### 6.1 payment-service 内部依赖（对外 Feign 客户端）

```java
// 复用 auth-service 钱包（参考 registration-service/AuthPatientFeignClient）
@FeignClient(name = "auth-service", url = "${auth.service.url:http://localhost:8081}")
public interface AuthPatientFeignClient {
    @PostMapping("/api/patient/{patientId}/balance/deduct")
    Map<String, Object> deductBalance(@PathVariable Integer patientId, @RequestBody Map<String, Object> body);

    @PostMapping("/api/patient/{patientId}/balance/refund")
    Map<String, Object> refundBalance(@PathVariable Integer patientId, @RequestBody Map<String, Object> body);

    @GetMapping("/api/patient/{patientId}/balance")
    Map<String, Object> getBalance(@PathVariable Integer patientId);
}
```

### 6.2 业务服务新增 Feign 客户端（调 payment-service）

各业务服务（registration/physician/medtech/pharmacy）新建 `feign/PaymentFeignClient.java`：

```java
@FeignClient(name = "payment-service", url = "${payment.service.url:http://localhost:8096}")
public interface PaymentFeignClient {
    @PostMapping("/api/payment/expense/create")
    Map<String, Object> createExpense(@RequestBody Map<String, Object> body);

    @GetMapping("/api/payment/check-paid/register/{registerId}")
    Map<String, Object> checkPaidByRegister(@PathVariable Long registerId);

    @GetMapping("/api/payment/check-paid/item")
    Map<String, Object> checkPaidByItem(@RequestParam Long registerId,
                                        @RequestParam String itemCode);
}
```

`businessType` 取值规范（与 auth-service 幂等唯一索引对齐）：
- 现有：`REGISTRATION` / `MEDICATION`
- 新增：`VISIT_PAYMENT`（聚合支付）、`EXAMINATION`（检查/检验/处置）、`REFUND_*`

---

## 七、代码结构（payment-service）

```
payment-service/
├── pom.xml
├── src/main/java/com/xikang/payment/
│   ├── PaymentApplication.java
│   ├── config/
│   │   └── FeignConfig.java
│   ├── controller/
│   │   ├── ExpenseController.java        # 出账（4.1）
│   │   ├── PaymentController.java        # 支付（4.2）
│   │   ├── PaymentQueryController.java   # 查询（4.3）
│   │   ├── PaymentCheckController.java   # 状态校验（4.4）
│   │   └── RefundController.java         # 退款（4.5）+ 收费员代收（4.6）
│   ├── service/
│   │   ├── ExpenseService.java           # 出账逻辑（幂等、写 expense_record）
│   │   ├── PaymentService.java           # 支付编排（聚合、调钱包、更新状态）
│   │   ├── PaymentQueryService.java      # 聚合查询
│   │   ├── PaymentCheckService.java      # 状态校验
│   │   └── RefundService.java            # 退款
│   ├── mapper/
│   │   └── ExpenseRecordMapper.java
│   ├── entity/
│   │   └── ExpenseRecord.java
│   ├── feign/
│   │   └── AuthPatientFeignClient.java
│   └── dto/                              # （规范化建议，区别于项目惯例的 Map）
│       ├── CreateExpenseDTO.java
│       ├── PaymentResultVO.java
│       ├── RegisterOrderVO.java
│       └── PaymentStatusVO.java
├── src/main/resources/
│   ├── application.yml
│   ├── bootstrap.yml
│   └── mapper/ExpenseRecordMapper.xml
```

**配置要点**（`application.yml`）：
```yaml
server:
  port: 8096
spring:
  application:
    name: payment-service
  datasource:
    url: jdbc:postgresql://localhost:3307/xikang_hospital
    ...
mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.xikang.payment.entity
  configuration:
    map-underscore-to-camel-case: true
auth:
  service:
    url: http://localhost:8081
```

---

## 八、现有逻辑搬迁清单（registration-service → payment-service）

> 策略：**直接替换，一步到位**（用户确认）。老接口 `/api/registration/*` 下的支付端点删除，前端同步改调用 `/api/payment/*`。

### 8.1 整体搬迁的代码

| registration-service 现有文件 | → payment-service 目标 | 说明 |
|-------------------------------|------------------------|------|
| `service/ChargeService.java` | `service/PaymentService.java` + `service/RefundService.java` | payRegistration / payMedication / charge 三个方法 |
| `service/RefundService.java` | `service/RefundService.java` | refund / refundByRegisterId / refundRegistrationFee |
| `service/ExpenseRecordService.java` | `service/PaymentQueryService.java` | getPendingExpenses / queryExpenseRecords |
| `mapper/ExpenseRecordMapper.java` + xml | `mapper/ExpenseRecordMapper.java` + xml | 整体迁移 |
| `entity/ExpenseRecord.java` | `entity/ExpenseRecord.java` | 整体迁移 |
| `controller/RegistrationController.java` 中 8 个支付端点 | `controller/*.java`（按职责拆分） | 路径从 `/api/registration/*` 改为 `/api/payment/*` |

### 8.2 registration-service 内部出账逻辑（保留但改为 Feign 调用）

`RegistrationService.createRegistrationFee`（第 550-566 行）：原本直接写 `expense_record`，**改为 Feign 调 payment-service `/api/payment/expense/create`**。

`RegistrationService.invalidateDuplicateRegistrationFees`（第 621-639 行）：作废重复挂号费的逻辑，**迁移到 payment-service 的 ExpenseService**（作为出账幂等的一部分）。

### 8.3 pharmacy-service 改造

pharmacy-service 现有 `ExpenseRecordMapper`（直接写库）→ **改为 Feign 调 payment-service 出账**。发药前校验逻辑改为调 `/api/payment/check-paid/item`。

### 8.4 physician-service 改造（新增检查/检验/处置费出账）

**这是本期最大的业务增量**。改造点：

1. `PhysicianService.createCheckRequest / createInspectionRequest / createDisposalRequest`（第 196/205/214 行）：
   - 现状：只写 `check_request` / `inspection_request` / `disposal_request`
   - 改造：开单成功后，**同步 Feign 调 payment-service 出账**
   - 价格来源：从 `medical_technology.tech_price` 读取（开单时前端已传入 `medicalTechnologyId`，后端 JOIN 取价）

2. `PhysicianService.createPrescription`（第 268 行，开处方）：
   - 现状：开处方后 pharmacy-service 出账
   - 改造：保持由 pharmacy 出账（或改为 physician 直接出账，待定）

3. **新增**：确诊/结束看诊前调 payment-service 校验全部付清（卡点 3）

### 8.5 搬迁后 registration-service 保留的职责

挂号本身（CRUD、排班关联、就诊状态机）保留，**只剥离支付/费用相关**。

---

## 九、前端改造点

### 9.1 患者端新增"待支付订单"页（核心新增）

| 项 | 内容 |
|----|------|
| 路由 | `path: 'payment'`，name `PatientPayment` |
| 文件 | `src/modules/patient/pages/PatientPayment.vue` |
| 挂入导航 | `PatientLayout.vue` 第 34-39 行 `quickActions` 数组追加项 |
| 角色 | `roles: ['patient', 'admin']` |
| API | `GET /api/payment/pending/patient/{patientId}` + `POST /api/payment/pay/register/{registerId}` |
| 类型 | `src/shared/api/modules/payment.ts`（新建）+ `src/shared/types/payment.ts`（新建） |

**页面结构**（按挂号号分组的卡片列表）：
```
┌─────────────────────────────────────────┐
│ 💳 我的待支付订单                          │
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ 就诊号 #1001  2026-06-29  内科 李医生 │ │
│ │ ─────────────────────────────────── │ │
│ │ • 挂号费              ¥30.00        │ │
│ │ • 血常规检查          ¥50.00        │ │
│ │ • 尿常规检验          ¥40.00        │ │
│ │ • 药品费              ¥160.00       │ │
│ │ ─────────────────────────────────── │ │
│ │ 合计：4 项            ¥280.00       │ │
│ │              [立即支付（余额结清）]  │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ 就诊号 #1002  ...（另一就诊号）       │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 9.2 医生工作站费用展示（改造现有页面）

| 页面 | 改造 |
|------|------|
| `PhysicianOrdersPage.vue`（开检查检验） | 开单后展示已产生费用，显示该就诊号累计金额 + 支付状态徽标 |
| `PhysicianPrescriptionPage.vue`（开处方） | 同上，处方金额纳入总计 |
| `PhysicianDiagnosisPage.vue`（确诊） | **确诊按钮点击前校验已付清**，未付清弹出提示并提供"提醒患者支付"入口 |

医生工作站可加一个**费用摘要侧栏/顶栏**，实时显示当前就诊号的：累计费用 / 已付 / 待付。

### 9.3 挂号收费员工作台改造

`RegistrationWorkspace.vue` 现有的"收费"Tab：
- API 调用从 `/api/registration/charge` 改为 `/api/payment/charge`
- `pending-charges` 改为 `/api/payment/pending/*`
- 退费接口改为 `/api/payment/refund/*`
- 功能 UI 保持不变（收费员无感知）

### 9.4 前端 API 模块新建

`src/shared/api/modules/payment.ts`：
```typescript
export const paymentApi = {
  pendingByPatient: (patientId: number) =>
    http<RegisterOrderVO[]>({ url: `/payment/pending/patient/${patientId}` }),
  pendingByRegister: (registerId: number) =>
    http<RegisterOrderVO>({ url: `/payment/pending/register/${registerId}` }),
  payByRegister: (registerId: number, body?: PayBody) =>
    http<PaymentResultVO>({ url: `/payment/pay/register/${registerId}`, method: 'POST', data: body }),
  records: (params: ExpenseRecordQuery) =>
    http<ExpenseRecord[]>({ url: `/payment/records`, params }),
  // 收费员代收
  charge: (data: ChargePayload) =>
    http<ChargeResult>({ url: `/payment/charge`, method: 'POST', data }),
  // 退款
  refund: (data: RefundPayload) =>
    http({ url: `/payment/refund`, method: 'POST', data }),
  refundByRegister: (registerId: number) =>
    http({ url: `/payment/refund/register/${registerId}`, method: 'POST' }),
}
```

`registrationApi` 中原有的 `payRegistration` / `payMedication` / `charge` / `pendingCharges*` / `refund*` **删除**，调用方改为 `paymentApi`。

---

## 十、实施阶段划分

> 本期只做阶段 1-4，阶段 5 作为可选增强。

### 阶段 1：payment-service 骨架与搬迁（后端）
- 新建 payment-service 模块（pom、启动类、配置）
- 迁移 ExpenseRecord 实体/Mapper/Service/Controller
- 迁移 Charge/Refund 逻辑
- 接入 auth-service 钱包（复用 Feign）
- 数据库迁移脚本 `migrate_022`

### 阶段 2：业务服务接入出账
- registration-service：挂号费出账改 Feign 调用
- physician-service：开检查/检验/处置单时出账（**新增**）
- pharmacy-service：药品费出账改 Feign 调用

### 阶段 3：强阻塞卡点接入
- physician-service 确诊前校验（卡点 3）
- medtech-service 执行前校验（卡点 4）
- pharmacy-service 发药前校验迁移（卡点 5）
- 医生接诊前校验挂号费（卡点 1）

### 阶段 4：前端
- 患者端"待支付订单"页
- 医生工作站费用展示 + 确诊校验
- 收费员工作台 API 路径迁移

### 阶段 5（可选增强）
- 单条支付（部分支付）
- 急诊绿色通道
- 支付成功后的消息通知

---

## 十一、风险与待确认

1. **搬迁回归风险**：registration-service 现有支付逻辑经过验证，搬迁后需完整回归测试（挂号费支付、窗口收费、退费、取消挂号退费）。**缓解**：搬迁时保持业务逻辑等价，只改路径和包名。

2. ** physician-service 出账时机**：开单时同步出账 vs 异步。**建议同步**（强阻塞要求费用立即可见），但要处理 payment-service 不可用时的降级（开单失败 vs 出账失败）。

3. **`medical_technology` 双价格字段**：生产库存在 `tech_price`（代码用）和 `price`（遗留）并存。**统一读 `tech_price`**（与 entity/mapper 一致）。

4. **聚合支付的原子性**：钱包扣款成功但 `expense_record` 状态更新失败会导致数据不一致。**缓解**：利用 auth-service `patient_balance_transaction` 的业务幂等唯一索引（`businessId=registerId`），重试时不会重复扣款；payment-service 需实现"查询钱包流水 → 补偿更新 expense_record"的恢复逻辑。

5. **gateway-service 路由**：新增 `payment-service` 路由规则（`/api/payment/**` → payment-service）。

---

## 十二、附录：关键文件清单（实现时定位用）

**后端 — 搬迁源**：
- `registration-service/src/main/java/com/xikang/registration/service/ChargeService.java`
- `registration-service/src/main/java/com/xikang/registration/service/RefundService.java`
- `registration-service/src/main/java/com/xikang/registration/service/ExpenseRecordService.java`
- `registration-service/src/main/java/com/xikang/registration/service/RegistrationService.java`（createRegistrationFee 第 550-566 行、invalidateDuplicateRegistrationFees 第 621-639 行）
- `registration-service/src/main/java/com/xikang/registration/mapper/ExpenseRecordMapper.java` + xml
- `registration-service/src/main/java/com/xikang/registration/entity/ExpenseRecord.java`
- `registration-service/src/main/java/com/xikang/registration/controller/RegistrationController.java`（8 个支付端点）
- `registration-service/src/main/java/com/xikang/registration/feign/AuthPatientFeignClient.java`（Feign 模板）

**后端 — 改造点**：
- `physician-service/src/main/java/com/xikang/physician/service/PhysicianService.java`（createCheckRequest 第 196 行 / createInspectionRequest 第 205 行 / createDisposalRequest 第 214 行 / createPrescription 第 268 行）
- `pharmacy-service/src/main/java/com/xikang/pharmacy/mapper/ExpenseRecordMapper.java`
- `pharmacy-service/src/main/java/com/xikang/pharmacy/service/PharmacyService.java`（发药前校验，第 545-612 行）
- `medtech-service/src/main/java/com/xikang/medtech/service/*.java`（执行前校验，需新增）

**公共**：
- `common/src/main/java/com/xikang/common/result/Result.java`
- `common/src/main/java/com/xikang/common/exception/BusinessException.java`
- `pom.xml`（根，需追加 payment-service 模块）

**数据库**：
- `docker/init-db/migrate_005_expense_record.sql`（现有表定义）
- `docker/init-db/migrate_006_patient_balance_transaction.sql`（钱包流水幂等）
- `docker/init-db/migrate_013_medication_fee_unique.sql`（MEDICATION_FEE 幂等模板）
- `docker/init-db/migrate_022_payment_service_expense_indexes.sql`（**新增**）

**前端**：
- `src/app/router/routes.ts`（第 54-128 行 patientRoutes，新增 payment 路由）
- `src/modules/patient/layouts/PatientLayout.vue`（第 34-39 行 quickActions，第 94-97 行余额胶囊）
- `src/modules/registration/RegistrationWorkspace.vue`（收费员工作台 API 迁移）
- `src/modules/physician/pages/PhysicianOrdersPage.vue` / `PhysicianDiagnosisPage.vue` / `PhysicianPrescriptionPage.vue`
- `src/shared/api/modules/registration.ts`（第 101-125 行支付 API，迁移后删除）
- `src/shared/api/modules/payment.ts`（**新建**）
- `src/shared/types/registration.ts`（费用类型，迁移到 `src/shared/types/payment.ts`）

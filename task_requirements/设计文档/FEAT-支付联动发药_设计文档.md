# FEAT：患者支付药品费 → 药房发药联动 设计文档

> **状态**：方案已确认，待用户"开始编码"指令。
> **日期**：2026-06-25

---

## 1. 业务流程（已确认）

```
1. 医生开药
   └─ prescription 表插药品行（drug_id + 数量），不涉及金额
   └─ 医生端完全不管钱 ✅ 不改

2. 患者在「我的处方」页查看
   └─ 调药房接口 → 药房按 drug_info.price × 数量算总额
   └─ 首次访问时，药房幂等地写一行 expense_record（药品费, status=0）
   └─ 返回处方明细 + 待缴金额给前端

3. 患者点「立即支付」
   └─ registration-service 扣余额（复用现有 deductBalance Feign）
   └─ expense_record.status: 0 → 1, 写 pay_time

4. 患者去药房取药
   └─ 药房 /pharmacy/pending JOIN expense_record 判断 status
   └─ 已缴费 → 允许「确认发药」
   └─ 未缴费 → 按钮 disabled + 提示「请先缴费」
```

## 2. 确认的决策

| 决策点 | 选择 |
|---|---|
| 出账时机 | **时机 A**：药房查询时幂等出账（不动医生端） |
| 支付方式 | 余额扣款（复用 `auth-service.deductBalance`） |
| 退药退费联动 | **本次不做**（独立特性） |
| 跨服务读数据 | 共享单库 JOIN（沿用 `PrescriptionMapper.xml` 现有风格） |

---

## 3. 现状清单（调研事实）

### 已有 ✅
- `expense_record` 表结构完整（`migrate_005_expense_record.sql`），`status` 0/1/2/3。
- `auth-service` 的 `deductBalance` Feign 接口已在 `registration-service/AuthPatientFeignClient.java` 用过。
- `ChargeService` 的扣余额+写 `pay_time`+`status=0→1` 逻辑（`payRegistration`）可复用模式。
- `ChargeService.getPendingChargesByPatient` 已能按患者查待缴费用。
- 患者端路由 `/patient/prescription`（`PatientPrescription.vue`）存在，但是空壳。

### 缺口 ❌
- **没有任何地方写过药品费 `expense_record` 行**（库里 5 行全是 `REGISTRATION_FEE`）。
- `ChargeService.payRegistration` 的 `filterPatientPayItems` 强制只缴挂号费，无法缴药品费。
- `PatientPrescription.vue` 是静态占位，无 API 调用、无缴费按钮。
- 药房 `/pharmacy/pending` 完全不 JOIN `expense_record`，没有缴费状态过滤。
- `pharmacy-service` 完全没有任何 `expense_record` 引用。

---

## 4. 实施方案

### 4.1 后端 — `pharmacy-service`（新增出账能力）

#### 新增 Mapper 方法（`ExpenseRecordMapper.java` + xml）
本服务首次引用 `expense_record` 表。新增一个简单 mapper：

```java
@Mapper
public interface ExpenseRecordMapper {
    // 查该挂号下是否已有 MEDICATION_FEE 行（幂等判断）
    ExpenseRecord selectMedicationFeeByRegisterId(@Param("registerId") Long registerId);

    // 插入一行药品费
    int insertMedicationFee(@Param("registerId") Long registerId,
                            @Param("patientId") Long patientId,
                            @Param("patientName") String patientName,
                            @Param("totalAmount") BigDecimal totalAmount,
                            @Param("operatorName") String operatorName);
}
```

#### 新增 Service 方法（`PharmacyService`）
```java
/** 患者端调用：返回该患者所有处方 + 药品费出账（幂等）。 */
public List<PatientPrescriptionView> getPatientPrescriptions(Long patientId) {
    // 1. 查该患者所有 register_id 下的 prescription 行
    // 2. 对每个 register_id：
    //    a) 算 totalAmount = Σ drug_info.price × quantity
    //    b) 若 expense_record 中无对应 MEDICATION_FEE 行 → INSERT（status=0）
    //    c) 否则取已有行的 status 作为缴费状态
    // 3. 组装 VO（处方明细 + 金额 + paid 标记）返回
}

/** 发药前校验（在 dispense() 开头调用）。 */
private void assertMedicationPaid(Long registerId) {
    ExpenseRecord er = expenseRecordMapper.selectMedicationFeeByRegisterId(registerId);
    if (er == null) {
        throw new BusinessException("该挂号尚未生成药品费账单，请患者在患者端查看处方后再发药");
    }
    if (er.getStatus() != 1) {
        throw new BusinessException("患者尚未支付药品费，无法发药");
    }
}
```

#### 新增 Controller 端点（`PharmacyController`）
```java
@GetMapping("/api/pharmacy/patient/{patientId}/prescriptions")
public Result<List<PatientPrescriptionView>> getPatientPrescriptions(@PathVariable Long patientId) {
    return Result.success(pharmacyService.getPatientPrescriptions(patientId));
}
```

⚠️ **权限**：该端点需要 `patient` 角色可访问。gateway-service 已有路由规则，需确认 `/api/pharmacy/patient/**` 允许 patient token 通过（或在 controller 上加 `@PreAuthorize("hasAnyRole('PATIENT','PHARMACY','ADMIN')")`）。

#### 修改 `/pharmacy/pending`（`PrescriptionMapper.xml:194` selectPending）
在 SELECT 列表追加 `paid_flag` 子查询：

```sql
(SELECT COUNT(*) FROM expense_record er
   WHERE er.register_id = p.register_id
     AND er.item_code = 'MEDICATION_FEE'
     AND er.status = 1) > 0 AS paid
```

> ⚠️ 注意：此处依赖 4.1 的 `insertMedicationFee` 已写入 `item_code='MEDICATION_FEE'`。

`PrescriptionSummary.java` 增加 `private Boolean paid;` 字段。

#### 修改 `dispense()` 方法
事务开头加 `assertMedicationPaid(registerId)`。

---

### 4.2 后端 — `registration-service`（新增药品费支付）

#### 新增 Service 方法（`ChargeService`）
```java
/** 患者自助支付药品费（扣余额）。 */
@Transactional
public void payMedication(Long registerId) {
    // 1. 查该挂号下的 MEDICATION_FEE expense_record（status=0）
    ExpenseRecord er = expenseRecordMapper.selectMedicationByRegisterId(registerId);
    if (er == null) throw new BusinessException("未找到药品费账单");
    if (er.getStatus() == 1) throw new BusinessException("已支付，无需重复缴费");
    if (er.getStatus() != 0) throw new BusinessException("当前状态不允许支付");

    // 2. Feign 扣余额（复用现有 AuthPatientFeignClient.deductBalance）
    authPatientFeignClient.deductBalance(er.getPatientId(), er.getTotalAmount(), "MEDICATION");

    // 3. 更新 expense_record：status=1, pay_time=now, operator_*
    expenseRecordMapper.markPaid(er.getId(), "患者", "患者自助支付药品费");
}
```

> 不动 `payRegistration`，避免影响挂号费支付逻辑。

#### 新增 Controller 端点（`RegistrationController`）
```java
@PostMapping("/api/registration/{registerId}/pay-medication")
public Result<Void> payMedication(@PathVariable Long registerId) {
    chargeService.payMedication(registerId);
    return Result.success(null);
}
```

---

### 4.3 前端 — 类型 & API

#### `src/shared/types/pharmacy.ts`
```typescript
export interface PatientPrescriptionView {
  registerId: number
  patientName?: string
  diagnosis?: string
  items: PrescriptionDetailItem[]
  totalAmount?: number
  paid: boolean
  payTime?: string
}

// PrescriptionSummary 增加
// paid?: boolean
```

#### `src/shared/api/modules/pharmacy.ts`
```typescript
patientPrescriptions(patientId: number) {
  return http<PatientPrescriptionView[]>({
    url: `/pharmacy/patient/${patientId}/prescriptions`, method: 'GET'
  })
}
```

#### `src/shared/api/modules/registration.ts`
```typescript
payMedication(registerId: number) {
  return http<void>({
    url: `/registration/${registerId}/pay-medication`, method: 'POST'
  })
}
```

---

### 4.4 前端 — `PatientPrescription.vue`（重写空壳）

```vue
<script setup lang="ts">
const loading = ref(false)
const payingId = ref<number | null>(null)
const list = ref<PatientPrescriptionView[]>([])

async function load() {
  loading.value = true
  try {
    list.value = await pharmacyApi.patientPrescriptions(authStore.userId)
  } finally { loading.value = false }
}

async function pay(row: PatientPrescriptionView) {
  try {
    await ElMessageBox.confirm(`确认支付药费 ${row.totalAmount} 元？`, '支付确认')
    payingId.value = row.registerId
    await registrationApi.payMedication(row.registerId)
    ElMessage.success('支付成功，请前往药房取药')
    await load()
  } finally { payingId.value = null }
}

onMounted(load)
</script>

<template>
  <!-- 每个 registerId 一张卡片：诊断 + 药品明细表 + 总金额 + 支付按钮 -->
  <!-- paid=true 时按钮置灰显示"已支付" -->
</template>
```

---

### 4.5 前端 — `DispensingPage.vue`（药房端禁用按钮）

在已有的 `currentStatus === 0`（待发药）分支里：

```vue
<template v-if="currentStatus === 0">
  <ElButton :loading="reviewing" @click="reviewBeforeDispense">审方预检</ElButton>
  <ElButton
    type="primary"
    :disabled="!selectedPrescription.prescription.paid"
    @click="dispenseSelected"
  >确认发药</ElButton>
  <ElAlert
    v-if="!selectedPrescription.prescription.paid"
    type="warning" :closable="false"
    title="患者尚未支付药品费，暂不可发药"
  />
</template>
```

---

## 5. 数据流验证

**场景 1：医生开药后，患者立刻查看**
- 患者端调 `/pharmacy/patient/{pid}/prescriptions`
- 药房 service 查 prescription → 算金额 → 发现无 MEDICATION_FEE 行 → INSERT（status=0）→ 返回 `paid=false`
- 患者看到「待缴 XX 元」+ 「立即支付」按钮 ✅

**场景 2：患者支付**
- 调 `/registration/{rid}/pay-medication`
- registration 扣余额 → `expense_record.status=1` ✅

**场景 3：药房发药**
- 药房调 `/pharmacy/pending`，SQL 里 `paid` 子查询返回 `true`
- 前端按钮可点
- 后端 `dispense()` 里 `assertMedicationPaid` 通过 ✅

**场景 4：未支付强点发药（防御）**
- 前端按钮 disabled，点不了
- 即使绕过前端，后端 `assertMedicationPaid` 抛异常 ✅

**场景 5：同一患者多次刷新处方页（幂等）**
- 第二次调 `getPatientPrescriptions` 时，`selectMedicationFeeByRegisterId` 命中已有行，不再 INSERT
- 金额从已有 `expense_record.total_amount` 取（不重新算，避免价格变动导致金额不一致）✅

---

## 6. 风险与边界

| 场景 | 处理 |
|---|---|
| 患者多次挂号、跨科室 | 按 `register_id` 维度出账，每挂号一行 `MEDICATION_FEE` |
| 余额不足 | `deductBalance` Feign 会抛异常，前端提示"余额不足" |
| 处方明细有变动（医生补开） | 目前不支持。已出账的金额以 `expense_record.total_amount` 为准，不重算。如要支持需追加"处方变更触发重算"逻辑（本次不做） |
| 退药 | 现状：退药只回滚库存 + `drug_state`。`expense_record` 不动。如患者已缴，需线下退费（本次不做） |
| 历史数据（库内已有未出账的旧处方） | 患者查看时自动补出账，无需迁移 |

---

## 7. 改动文件清单

**后端 pharmacy-service**：
- 新增 `mapper/ExpenseRecordMapper.java` + `resources/mapper/ExpenseRecordMapper.xml`
- 新增 `dto/PatientPrescriptionView.java`
- 修改 `service/PharmacyService.java`（+ getPatientPrescriptions / assertMedicationPaid）
- 修改 `controller/PharmacyController.java`（+ GET /patient/{pid}/prescriptions）
- 修改 `mapper/PrescriptionMapper.java`（+ paid 字段映射）
- 修改 `resources/mapper/PrescriptionMapper.xml`（selectPending 加子查询）
- 修改 `dto/PrescriptionSummary.java`（+ paid 字段）

**后端 registration-service**：
- 修改 `service/ChargeService.java`（+ payMedication）
- 修改 `controller/RegistrationController.java`（+ POST /{rid}/pay-medication）
- 可能需要在 `ExpenseRecordMapper` 加 `selectMedicationByRegisterId` 和 `markPaid`（若不存在）

**前端**：
- 修改 `src/shared/types/pharmacy.ts`（+ PatientPrescriptionView，PrescriptionSummary.paid）
- 修改 `src/shared/api/modules/pharmacy.ts`（+ patientPrescriptions）
- 修改 `src/shared/api/modules/registration.ts`（+ payMedication）
- 重写 `src/modules/patient/pages/PatientPrescription.vue`
- 修改 `src/modules/pharmacy/pages/DispensingPage.vue`（按钮 disabled 逻辑）

---

## 8. 待确认事项

无。所有决策已在上一轮对话确认。等待"开始编码"指令。

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import ExpiringStockBanner from '@/modules/pharmacy/components/ExpiringStockBanner.vue'
import MedicationGuidePrintSheet from '@/shared/components/MedicationGuidePrintSheet.vue'
import MedicationGuidePreviewDialog from '@/shared/components/MedicationGuidePreviewDialog.vue'
import { useMedicationGuideExport } from '@/shared/composables/useMedicationGuideExport'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  dispensationStatusName,
  RETURN_REASONS,
  REVIEW_STATUS_TONE,
} from '@/shared/constants/pharmacy'
import type {
  DispensePayload,
  PrescriptionDetailResponse,
  PrescriptionSummary,
  ReturnDrugPayload,
  ReviewResult,
} from '@/shared/types/pharmacy'

const authStore = useAuthStore()

// ===== 处方列表（所有状态） =====
const listLoading = ref(false)
const allPrescriptions = ref<PrescriptionSummary[]>([])
// -1=全部, 0=待发药, 1=已发药, 2=已退药
const STATUS_ALL = -1
const statusFilter = ref<number>(STATUS_ALL)
const selectedPrescriptionId = ref<number | undefined>()
const selectedPrescription = ref<PrescriptionDetailResponse | null>(null)
const reviewing = ref(false)
const dispensing = ref(false)

// 按筛选条件过滤后的列表
const filteredPrescriptions = computed(() => {
  // -1=全部，且 ElSelect clearable 清空后变成 undefined/null 也按全部处理
  if (statusFilter.value == null || statusFilter.value === STATUS_ALL) {
    return allPrescriptions.value
  }
  return allPrescriptions.value.filter((p) => p.dispensationStatus === statusFilter.value)
})

// 各状态计数（筛选栏徽章用）
const countByStatus = computed(() => {
  const c = { all: allPrescriptions.value.length, pending: 0, dispensed: 0, returned: 0 }
  for (const p of allPrescriptions.value) {
    if (p.dispensationStatus === 0) c.pending++
    else if (p.dispensationStatus === 1) c.dispensed++
    else if (p.dispensationStatus === 2) c.returned++
  }
  return c
})

// ===== 用药指导单（处方级 PDF，延迟生成；前端用 html2pdf 渲染，字体走浏览器系统字体） =====
const guideStatus = ref<import('@/shared/types/pharmacy').MedicationGuideRecord | null>(null)
const guideStatusLoading = ref(false)
const guideRetrying = ref(false)
const { exportPdf: exportGuidePdf, record: guidePrintRecord, exporting: guideExporting } = useMedicationGuideExport()
const guidePrintSheetRef = ref<InstanceType<typeof MedicationGuidePrintSheet> | null>(null)

const pharmacistId = computed(() => {
  const parsed = Number(authStore.userId)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
})
const pharmacistName = computed(() => authStore.realName || authStore.username || '药房')

function statusTone(status?: number) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

async function loadPrescriptions() {
  listLoading.value = true
  try {
    // 调用 queryPrescriptions（不带过滤参数）拿全部状态处方
    allPrescriptions.value = await pharmacyApi.queryPrescriptions()
    // 选中策略：若当前选中的仍在列表里则保持，否则选第一条
    const stillThere = selectedPrescriptionId.value
      && allPrescriptions.value.some((p) => p.id === selectedPrescriptionId.value)
    if (stillThere) {
      await loadPrescriptionDetail(selectedPrescriptionId.value)
    } else if (allPrescriptions.value.length > 0) {
      // 列表变化后选第一条（但要考虑筛选）
      const firstVisible = filteredPrescriptions.value[0]
      if (firstVisible) {
        selectedPrescriptionId.value = firstVisible.id
        await loadPrescriptionDetail(firstVisible.id)
      } else {
        selectedPrescriptionId.value = undefined
        selectedPrescription.value = null
      }
    } else {
      selectedPrescriptionId.value = undefined
      selectedPrescription.value = null
    }
  } finally {
    listLoading.value = false
  }
}

async function loadPrescriptionDetail(id?: number) {
  if (!id) {
    selectedPrescription.value = null
    return
  }
  selectedPrescription.value = await pharmacyApi.prescriptionDetail(id)
  // 发药前/已发药都探测指导单状态（发药前用于判断"是否已生成"）
  void refreshGuideStatus()
}

async function refreshGuideStatus() {
  const registerId = selectedPrescription.value?.prescription.registerId
  if (!registerId) return
  guideStatusLoading.value = true
  try {
    guideStatus.value = await pharmacyApi.medicationGuideStatus(registerId)
  } catch {
    guideStatus.value = null
  } finally {
    guideStatusLoading.value = false
  }
}

async function downloadGuide() {
  const registerId = selectedPrescription.value?.prescription.registerId
  if (!registerId) {
    ElMessage.warning('请先选择一条处方')
    return
  }
  if (!guideStatus.value || guideStatus.value.status !== 'success') {
    ElMessage.warning('指导单尚未就绪，请稍后再试')
    return
  }
  // 复用已经查到的 guideStatus（含 guideContent），无需再请求一次后端
  await exportGuidePdf(guideStatus.value, guidePrintSheetRef)
}

async function retryGuide() {
  const registerId = selectedPrescription.value?.prescription.registerId
  if (!registerId) return
  guideRetrying.value = true
  try {
    await pharmacyApi.retryMedicationGuide(registerId)
    ElMessage.success('已重新生成指导单数据')
    await refreshGuideStatus()
  } finally {
    guideRetrying.value = false
  }
}

// ===== 发药前主动生成用药指导单 =====
const generatingGuide = ref(false)
const previewVisible = ref(false)

async function generateGuideBeforeDispense() {
  const prescription = selectedPrescription.value?.prescription
  if (!prescription?.registerId) {
    ElMessage.warning('请先选择一条处方')
    return
  }
  generatingGuide.value = true
  try {
    const fresh = await pharmacyApi.generateMedicationGuide(prescription.registerId)
    guideStatus.value = fresh
    if (fresh.status === 'success') {
      ElMessage.success('用药指导单已生成')
      previewVisible.value = true // 生成成功后自动弹预览
    } else if (fresh.status === 'failed') {
      ElMessage.error(fresh.errorMessage || 'AI 生成失败，请重试')
    }
  } catch {
    ElMessage.error('生成失败，请稍后重试')
  } finally {
    generatingGuide.value = false
  }
}

function openGuidePreview() {
  if (!guideStatus.value) {
    ElMessage.warning('指导单尚未生成')
    return
  }
  previewVisible.value = true
}

// 预览弹窗里点了"重新生成/生成"后同步本页 guideStatus
function onGuideChanged(record: import('@/shared/types/pharmacy').MedicationGuideRecord) {
  guideStatus.value = record
}

// 指导单按钮可用性：已发药 + 状态为 success
const canDownloadGuide = computed(() => {
  return selectedPrescription.value?.prescription.dispensationStatus === 1
    && guideStatus.value?.status === 'success'
})

// 当前选中处方的发药状态（0待发药 / 1已发药 / 2已退药）
const currentStatus = computed(() => selectedPrescription.value?.prescription.dispensationStatus)

// 已发药分支的"下载/重试指导单"按钮文案（只在 currentStatus===1 渲染）
const guideButtonLabel = computed(() => {
  if (guideStatusLoading.value) return '指导单查询中…'
  if (!guideStatus.value) return '指导单生成中…'
  if (guideStatus.value.status === 'failed') return '重新生成指导单'
  return '下载用药指导单'
})

async function reviewBeforeDispense() {
  const prescription = selectedPrescription.value?.prescription
  if (!prescription?.registerId) {
    ElMessage.warning('请先选择一条处方')
    return
  }
  reviewing.value = true
  try {
    const result = await pharmacyApi.review(prescription.registerId)
    showReviewResult(result)
  } finally {
    reviewing.value = false
  }
}

function showReviewResult(result: ReviewResult) {
  const blocked = result.items.filter((i) => i.status === 'block')
  const warned = result.items.filter((i) => i.status === 'warn')
  const tone = REVIEW_STATUS_TONE[result.overallStatus] || 'warning'
  const lines: string[] = []
  if (result.totalAmount != null) {
    lines.push(`本单总金额：${result.totalAmount} 元`)
  }
  if (blocked.length > 0) {
    lines.push(`阻断 ${blocked.length} 项：`)
    blocked.forEach((i) => lines.push(`  · ${i.drugName} — ${i.reason}`))
  }
  if (warned.length > 0) {
    lines.push(`提醒 ${warned.length} 项：`)
    warned.forEach((i) => lines.push(`  · ${i.drugName} — ${i.reason}`))
  }
  if (blocked.length === 0 && warned.length === 0) {
    lines.push('全部明细通过校验，可以发药。')
  }
  ElMessageBox.alert(lines.join('\n'), '审方预检结果', {
    type: tone === 'success' ? 'success' : tone === 'danger' ? 'error' : 'warning',
    confirmButtonText: '知道了',
  })
}

async function dispenseSelected() {
  if (dispensing.value) return
  const prescription = selectedPrescription.value?.prescription
  if (!prescription?.registerId || !prescription.patientId) {
    ElMessage.warning('请先选择一条待发药处方')
    return
  }
  // 发药前必须先生成用药指导单
  if (guideStatus.value?.status !== 'success') {
    ElMessage.warning('请先点击「生成用药指导单」并确认后再发药')
    return
  }
  const itemsCount = selectedPrescription.value?.details.length ?? 0
  dispensing.value = true
  try {
    try {
      await ElMessageBox.confirm(
        `确认对此挂号的 ${itemsCount} 条药品明细发药？发药后库存会立即扣减并触发 AI 随访计划创建。`,
        '发药确认',
        { type: 'warning', confirmButtonText: '确认发药', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    const payload: DispensePayload = {
      pharmacistId: pharmacistId.value,
      pharmacistName: pharmacistName.value,
    }
    await pharmacyApi.dispense(prescription.registerId, payload)
    ElMessage.success('发药成功，随访计划将在后台自动创建')
    await loadPrescriptions()
    // 指导单在发药 afterCommit 异步生成，前端稍后探测；先清空旧状态
    guideStatus.value = null
  } catch {
    ElMessage.error('发药失败，请稍后重试')
  } finally {
    dispensing.value = false
  }
}

async function returnSelected() {
  const prescription = selectedPrescription.value?.prescription
  if (!prescription?.registerId) {
    ElMessage.warning('请先选择一条处方')
    return
  }
  try {
    const result = await ElMessageBox.prompt('请输入退药原因', '退药确认', {
      type: 'warning',
      confirmButtonText: '确认退药',
      cancelButtonText: '取消',
      inputPlaceholder: '如：患者申请退药',
      inputValue: RETURN_REASONS[0],
    })
    const reason = (result?.value || '').trim()
    if (!reason) {
      ElMessage.warning('请填写退药原因')
      return
    }
    const payload: ReturnDrugPayload = {
      pharmacistId: pharmacistId.value,
      pharmacistName: pharmacistName.value,
      reason,
    }
    await pharmacyApi.returnDrug(prescription.registerId, payload)
    ElMessage.success('退药成功，库存已恢复')
    await loadPrescriptions()
  } catch {
    // 用户取消
  }
}

/**
 * 把后端 ISO 字符串（'2026-06-25T13:33:44.013476'）格式化成 '2026-06-25 13:33:44'。
 * 兼容三种输入：null/空 → '-'; 含 T → 替换 T 为空格并截到秒; 普通 yyyy-MM-dd HH:mm:ss → 原样。
 */
function formatDateTime(raw?: string | null): string {
  if (!raw) return '-'
  const s = String(raw).trim()
  if (!s || s === '-') return '-'
  // 含 'T'（LocalDateTime 默认序列化）→ '2026-06-25T13:33:44.013476'
  if (s.includes('T')) {
    // 去掉 T，按空格拼接；小数秒截断到 6 位后取整到秒
    const [date, time] = s.split('T')
    // time 可能是 '13:33:44.013476'，取冒号前两段 + 第三段整数部分
    const cleanTime = time.split('.')[0] || ''
    return `${date} ${cleanTime}`
  }
  // 已经是 'yyyy-MM-dd HH:mm:ss' 或 'yyyy-MM-dd' 直接返回
  return s
}

onMounted(() => {
  void loadPrescriptions()
})
</script>

<template>
  <div class="dispensing-page u-page-grid">
    <PageHeader
      title="发药工作台"
      description="按挂号聚合的全部处方。发药前可审方预检；发药后自动扣库存、生成发药单、异步创建 AI 随访；支持按状态筛选。"
      eyebrow="Role B / Pharmacy · ①"
    >
      <template #actions>
        <ElButton @click="loadPrescriptions">刷新列表</ElButton>
      </template>
    </PageHeader>

    <!-- 近效期预警条（发药时需要知道）-->
    <ExpiringStockBanner />

    <GlassCard class="flow-card">
      <div class="split-grid">
        <!-- 左：处方列表（全部状态，可筛选） -->
        <section class="pane">
          <div class="section-title">
            <h3>处方列表</h3>
            <StatusTag :tone="listLoading ? 'warning' : 'primary'">
              {{ filteredPrescriptions.length }} / {{ allPrescriptions.length }} 条
            </StatusTag>
          </div>

          <!-- 状态筛选 -->
          <div class="filter-bar">
            <ElSelect
              v-model="statusFilter"
              placeholder="全部状态"
              clearable
              size="small"
              class="status-select"
            >
              <ElOption label="全部状态" :value="-1" />
              <ElOption :label="`待发药（${countByStatus.pending}）`" :value="0" />
              <ElOption :label="`已发药（${countByStatus.dispensed}）`" :value="1" />
              <ElOption :label="`已退药（${countByStatus.returned}）`" :value="2" />
            </ElSelect>
          </div>

          <ElAlert v-if="listLoading && !allPrescriptions.length" type="info" :closable="false" title="正在加载…" />
          <div class="prescription-list">
            <button
              v-for="item in filteredPrescriptions"
              :key="item.id"
              class="prescription-item"
              :class="{ 'is-active': item.id === selectedPrescriptionId }"
              type="button"
              @click="selectedPrescriptionId = item.id; loadPrescriptionDetail(item.id)"
            >
              <div class="prescription-item__head">
                <strong>#{{ item.id }} · {{ item.patientName || '-' }}</strong>
                <StatusTag :tone="statusTone(item.dispensationStatus)">
                  {{ item.dispensationStatusName || dispensationStatusName(item.dispensationStatus) }}
                </StatusTag>
              </div>
              <div class="prescription-item__diag">{{ item.diagnosis || '待补充诊断' }}</div>
              <div class="prescription-item__meta">
                <span>挂号 {{ item.registerId || '-' }}</span>
                <span>{{ item.totalAmount ?? '-' }} 元</span>
                <span v-if="item.dispensationStatus === 0 && item.paid === false" class="warn-pay">
                  · 未缴费
                </span>
              </div>
            </button>
            <ElEmpty
              v-if="!listLoading && filteredPrescriptions.length === 0"
              :description="allPrescriptions.length === 0 ? '当前暂无处方' : '当前筛选条件下无处方'"
            />
          </div>
        </section>

        <!-- 右：处方详情 -->
        <section class="pane">
          <h3>处方详情</h3>
          <ElEmpty v-if="!selectedPrescription" description="请选择一条处方查看详情" />
          <template v-else>
            <ElDescriptions :column="2" border>
              <ElDescriptionsItem label="患者">{{ selectedPrescription.prescription.patientName || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="医生">{{ selectedPrescription.prescription.physicianName || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="诊断" :span="2">{{ selectedPrescription.prescription.diagnosis || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="挂号号">{{ selectedPrescription.prescription.registerId || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="总金额">{{ selectedPrescription.prescription.totalAmount ?? '-' }} 元</ElDescriptionsItem>
              <!-- 已发药/已退药时显示发药信息（替代原"查看发药单"弹窗） -->
              <ElDescriptionsItem v-if="currentStatus !== 0" label="发药单号">{{ selectedPrescription.prescription.dispensingNo || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem v-if="currentStatus !== 0" label="发药人">{{ selectedPrescription.prescription.pharmacist || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem v-if="currentStatus !== 0" label="发药时间" :span="2">{{ formatDateTime(selectedPrescription.prescription.dispensationTime) }}</ElDescriptionsItem>
            </ElDescriptions>

            <ElTable :data="selectedPrescription.details" class="mt">
              <ElTableColumn prop="drugName" label="药品" min-width="160" />
              <ElTableColumn prop="specification" label="规格" min-width="120" />
              <ElTableColumn prop="usage" label="用法" min-width="120" />
              <ElTableColumn prop="dosage" label="剂量" min-width="100" />
              <ElTableColumn prop="quantity" label="数量" min-width="80" />
              <ElTableColumn prop="totalAmount" label="金额" min-width="100" />
            </ElTable>

            <div class="actions">
              <!-- 待发药：审方预检 → 生成用药指导单 → 确认发药 -->
              <template v-if="currentStatus === 0">
                <ElButton :loading="reviewing" :disabled="dispensing || generatingGuide" @click="reviewBeforeDispense">审方预检</ElButton>

                <!-- 生成用药指导单按钮：黑色（primary），状态驱动文案 -->
                <ElButton
                  v-if="guideStatus?.status !== 'success'"
                  type="primary"
                  :loading="generatingGuide || guideStatusLoading"
                  @click="guideStatus?.status === 'failed' ? retryGuide() : generateGuideBeforeDispense()"
                >{{ guideStatus?.status === 'failed' ? '重新生成指导单' : (generatingGuide ? '生成中…' : '生成用药指导单') }}</ElButton>
                <ElButton v-else plain @click="openGuidePreview">查看用药指导单</ElButton>

                <ElButton
                  type="primary"
                  :loading="dispensing"
                  :disabled="!selectedPrescription.prescription.paid || guideStatus?.status !== 'success'"
                  @click="dispenseSelected"
                >{{ dispensing ? '发药中…' : '确认发药' }}</ElButton>

                <ElAlert
                  v-if="!selectedPrescription.prescription.paid"
                  type="warning"
                  :closable="false"
                  title="患者尚未支付药品费，暂不可发药"
                />
                <ElAlert
                  v-else-if="guideStatus?.status !== 'success'"
                  type="info"
                  :closable="false"
                  title="请先生成用药指导单，确认后再发药"
                />
              </template>

              <!-- 已发药：查看指导单 + 下载 PDF + 退药 -->
              <template v-else-if="currentStatus === 1">
                <ElButton
                  type="primary"
                  plain
                  :disabled="!guideStatus || guideStatus.status !== 'success'"
                  @click="openGuidePreview"
                >查看用药指导</ElButton>
                <ElButton
                  :type="guideStatus?.status === 'failed' ? 'warning' : 'success'"
                  :loading="guideExporting || guideRetrying"
                  :disabled="!canDownloadGuide && guideStatus?.status !== 'failed'"
                  @click="guideStatus?.status === 'failed' ? retryGuide() : downloadGuide()"
                >{{ guideButtonLabel }}</ElButton>
                <ElButton type="danger" plain @click="returnSelected">退药</ElButton>
              </template>

              <!-- 已退药：纯只读，无操作按钮 -->
              <template v-else-if="currentStatus === 2">
                <!-- 无操作 -->
              </template>
            </div>
          </template>
        </section>
      </div>
    </GlassCard>

    <!-- 用药指导单预览弹窗（生成后查看 / 病历查看入口共用） -->
    <MedicationGuidePreviewDialog
      v-model:visible="previewVisible"
      :record="guideStatus"
      :register-id="selectedPrescription?.prescription?.registerId"
      :show-retry="currentStatus === 0"
      @changed="onGuideChanged"
    />

    <!-- PDF 渲染容器：屏幕外，用户不可见；导出时 html2pdf 截图此 DOM -->
    <div class="mg-print-host" aria-hidden="true">
      <MedicationGuidePrintSheet ref="guidePrintSheetRef" :record="guidePrintRecord" />
    </div>
  </div>
</template>

<style scoped>
.flow-card {
  padding: var(--space-5);
}

.split-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.8fr) minmax(0, 1fr);
  gap: var(--space-5);
}

.pane {
  min-width: 0;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.filter-bar {
  margin-block-end: var(--space-3);
}

.status-select {
  width: 200px;
}

.warn-pay {
  color: var(--color-danger, #d97706);
  font-weight: 600;
}

.prescription-list {
  display: grid;
  gap: var(--space-3);
}

.prescription-item {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.prescription-item:hover {
  border-color: var(--color-primary);
}

.prescription-item.is-active {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.prescription-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.prescription-item__diag {
  color: var(--color-text-muted);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.prescription-item__meta {
  display: flex;
  gap: var(--space-3);
  color: var(--color-text-muted);
  font-size: 12px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.mt {
  margin-block-start: var(--space-5);
}

@media (max-width: 1080px) {
  .split-grid {
    grid-template-columns: 1fr;
  }
}

/* PDF 渲染容器：推到屏幕外，不占布局也不可见，但浏览器仍会正常渲染 */
.mg-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  visibility: hidden;
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import MedicationGuidePrintSheet from '@/shared/components/MedicationGuidePrintSheet.vue'
import { useMedicationGuideExport } from '@/shared/composables/useMedicationGuideExport'
import { useAuthStore } from '@/app/stores/auth'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import { registrationApi } from '@/shared/api/modules/registration'
import { dispensationStatusName } from '@/shared/constants/pharmacy'
import type { MedicationGuideRecord, PrescriptionDetailResponse, PrescriptionSummary } from '@/shared/types/pharmacy'

const authStore = useAuthStore()

const loading = ref(false)
const prescriptions = ref<PrescriptionSummary[]>([])
const payingRegisterId = ref<number | null>(null)

// 分页（本地切片，数据已全量拉取）
const PAGE_SIZE = 10
const page = ref(1)
const pagedPrescriptions = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return prescriptions.value.slice(start, start + PAGE_SIZE)
})

// 处方详情（右栏平铺）
const detail = ref<PrescriptionDetailResponse | null>(null)
const detailLoading = ref(false)
const selectedId = ref<number | null>(null)

// ===== 用药指导单 PDF（复用药师端的前端 html2pdf 方案） =====
const guideStatus = ref<MedicationGuideRecord | null>(null)
const guideStatusLoading = ref(false)
const guideRetrying = ref(false)
const { exportPdf: exportGuidePdf, record: guidePrintRecord, exporting: guideExporting } = useMedicationGuideExport()
const guidePrintSheetRef = ref<InstanceType<typeof MedicationGuidePrintSheet> | null>(null)

const patientId = computed(() => authStore.currentPatientId)
const currentPatient = computed(() => authStore.currentPatient)
const isFamily = computed(() => {
  const p = currentPatient.value
  if (!p) return false
  return p.isPrimary !== 1 && p.relation && p.relation !== '本人'
})
const relationLabel = computed(() => currentPatient.value?.relation || '家属')

// 家属 Tab 仅在 >1 个就诊人时显示
const showFamilyTabs = computed(() => (authStore.patients?.length ?? 0) > 1)

async function load() {
  if (!patientId.value) {
    ElMessage.warning('未找到患者档案，请重新登录')
    return
  }
  loading.value = true
  // 切换家属时清空右栏选中
  selectedId.value = null
  detail.value = null
  guideStatus.value = null
  page.value = 1
  try {
    prescriptions.value = await pharmacyApi.patientPrescriptions(patientId.value)
  } finally {
    loading.value = false
  }
}

async function selectRow(rx: PrescriptionSummary) {
  selectedId.value = rx.id
  detailLoading.value = true
  detail.value = null
  guideStatus.value = null
  try {
    detail.value = await pharmacyApi.prescriptionDetail(rx.id)
    // 已发药才有用药指导单，选中后顺带查状态
    if (rx.dispensationStatus === 1 && rx.registerId) {
      void refreshGuideStatus(rx.registerId)
    }
  } finally {
    detailLoading.value = false
  }
}

// ===== 用药指导单 =====
async function refreshGuideStatus(registerId: number) {
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
  const registerId = detail.value?.prescription.registerId
  if (!registerId) {
    ElMessage.warning('请先选择一条处方')
    return
  }
  if (!guideStatus.value || guideStatus.value.status !== 'success') {
    ElMessage.warning('指导单尚未就绪，请稍后再试')
    return
  }
  await exportGuidePdf(guideStatus.value, guidePrintSheetRef)
}

/** 指导单按钮统一入口：success→下载；failed/无记录→重新生成 */
function handleGuideClick() {
  if (guideStatus.value?.status === 'success') {
    void downloadGuide()
  } else {
    void retryGuide()
  }
}

async function retryGuide() {
  const registerId = detail.value?.prescription.registerId
  if (!registerId) return
  guideRetrying.value = true
  try {
    await pharmacyApi.retryMedicationGuide(registerId)
    ElMessage.success('已重新生成指导单数据')
    await refreshGuideStatus(registerId)
  } finally {
    guideRetrying.value = false
  }
}

const guideButtonLabel = computed(() => {
  if (!detail.value) return '下载用药指导单'
  if (detail.value.prescription.dispensationStatus !== 1) return '下载用药指导单'
  if (guideStatusLoading.value) return '指导单查询中…'
  if (!guideStatus.value) return '指导单生成中…'
  if (guideStatus.value.status === 'failed') return '重新生成指导单'
  return '下载用药指导单'
})

async function switchTo(patientIdToSwitch: number) {
  if (patientIdToSwitch === patientId.value) return
  authStore.switchPatient(patientIdToSwitch)
  await load()
}

async function pay(rx: PrescriptionSummary) {
  if (!rx.registerId) return
  const amount = rx.totalAmount ?? 0
  try {
    await ElMessageBox.confirm(
      `确认支付药品费 ${amount.toFixed(2)} 元？支付成功后可前往药房取药。`,
      '药品费支付确认',
      { type: 'warning', confirmButtonText: '确认支付', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  payingRegisterId.value = rx.registerId
  try {
    const result = await registrationApi.payMedication(rx.registerId)
    if (result && (result as { payStatus?: number }).payStatus === 1) {
      ElMessage.success(`支付成功，剩余余额 ${(result as { accountBalance?: number }).accountBalance ?? '-'} 元`)
      await load()
    } else {
      ElMessage.error((result as { paymentMessage?: string }).paymentMessage || '支付失败')
    }
  } finally {
    payingRegisterId.value = null
  }
}

function statusTone(rx: PrescriptionSummary) {
  if (rx.dispensationStatus === 1) return 'success'
  if (rx.dispensationStatus === 2) return 'neutral'
  return rx.paid ? 'primary' : 'warning'
}

function statusText(rx: PrescriptionSummary) {
  if (rx.dispensationStatus === 1) return '已发药'
  if (rx.dispensationStatus === 2) return '已退药'
  return rx.paid ? '已缴费/待取药' : '待缴费'
}

function payButtonLabel(rx: PrescriptionSummary) {
  if (rx.dispensationStatus === 1) return '已发药'
  if (rx.dispensationStatus === 2) return '已退药'
  return rx.paid ? '已支付' : '支付药品费'
}

function payButtonDisabled(rx: PrescriptionSummary) {
  return rx.paid || (rx.dispensationStatus ?? 0) !== 0
}

// 诊断摘要：列表卡片上截断显示
function diagnosisShort(rx: PrescriptionSummary) {
  const d = (rx.diagnosis || '').trim()
  if (!d) return '—'
  return d.length > 18 ? d.slice(0, 18) + '…' : d
}

// 后端 ISO 时间（2026-06-30T16:14:12.842816）→ 2026-06-30 16:14
function formatTime(value?: string | null): string {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

// 切换家属时重新加载（switchTo 已调 load，这里兜底外部变更）
watch(() => authStore.currentPatientId, (cur, prev) => {
  if (cur && cur !== prev) void load()
})

onMounted(load)
</script>

<template>
  <div class="patient-prescription">
    <GlassCard class="prescription-shell">
      <div class="shell-header">
        <div class="shell-header__text">
          <h2>我的处方</h2>
          <p>医生开药完成后在此查看药费，支付后前往药房取药</p>
        </div>
        <ElButton size="small" :loading="loading" @click="load">刷新</ElButton>
      </div>

      <!-- 家属切换 Tab：仅当存在多个就诊人时显示 -->
      <div v-if="showFamilyTabs" class="family-tabs">
        <ElButton
          v-for="p in authStore.patients"
          :key="p.patientId"
          :type="p.patientId === patientId ? 'primary' : 'default'"
          size="small"
          round
          @click="switchTo(p.patientId)"
        >
          {{ p.realName || `就诊人 ${p.patientId}` }}
          <span v-if="p.isPrimary !== 1 && p.relation" class="family-tabs__relation">
            （{{ p.relation }}）
          </span>
        </ElButton>
      </div>

      <ElAlert v-if="loading && !prescriptions.length" type="info" :closable="false" title="正在加载…" />

      <div v-else class="split-grid">
        <!-- 左栏：处方列表（按挂号维度） -->
        <div class="pane pane--list">
          <div class="pane__title">
            <span>处方列表</span>
            <StatusTag v-if="prescriptions.length" tone="primary">{{ prescriptions.length }} 条</StatusTag>
            <StatusTag v-if="isFamily" tone="ai">{{ relationLabel }}</StatusTag>
          </div>

          <ElEmpty
            v-if="!prescriptions.length"
            description="该就诊人暂无处方记录"
            :image-size="120"
          />

          <div v-else class="rx-list">
            <div
              v-for="rx in pagedPrescriptions"
              :key="rx.id"
              class="rx-card"
              :class="{ 'rx-card--active': selectedId === rx.id }"
              @click="selectRow(rx)"
            >
              <div class="rx-card__head">
                <span class="rx-card__id">挂号 {{ rx.registerId ?? '-' }}</span>
                <StatusTag :tone="statusTone(rx)">{{ statusText(rx) }}</StatusTag>
              </div>
              <div class="rx-card__meta">
                <span>{{ formatTime(rx.createTime) }}</span>
                <span>· {{ rx.physicianName || '—' }}</span>
              </div>
              <div class="rx-card__diagnosis">{{ diagnosisShort(rx) }}</div>
              <div class="rx-card__amount">
                <span class="amount-label">药费</span>
                <span class="amount-value">¥ {{ (rx.totalAmount ?? 0).toFixed(2) }}</span>
              </div>
              <div v-if="!rx.paid && (rx.dispensationStatus ?? 0) === 0" class="rx-card__warn">
                · 请先支付药品费再取药
              </div>
            </div>
          </div>

          <div v-if="prescriptions.length > PAGE_SIZE" class="pagination-row">
            <ElPagination
              v-model:current-page="page"
              :total="prescriptions.length"
              :page-size="PAGE_SIZE"
              layout="prev, pager, next"
              small
              background
            />
          </div>
        </div>

        <!-- 右栏：处方详情（平铺） -->
        <div class="pane pane--detail">
          <ElEmpty
            v-if="!detail && !detailLoading"
            description="请从左侧选择处方查看明细"
            :image-size="140"
          />
          <ElAlert v-else-if="detailLoading" type="info" :closable="false" title="正在加载明细…" />

          <template v-else-if="detail">
            <div class="pane__title">
              <span>处方明细</span>
              <StatusTag :tone="statusTone(detail.prescription as unknown as PrescriptionSummary)">
                {{ dispensationStatusName(detail.prescription.dispensationStatus) }}
              </StatusTag>
            </div>

            <ElDescriptions :column="2" border>
              <ElDescriptionsItem label="患者">
                {{ detail.prescription.patientName || '-' }}
                <StatusTag v-if="isFamily" tone="ai" class="ml">{{ relationLabel }}</StatusTag>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="医生">{{ detail.prescription.physicianName || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="挂号号">{{ detail.prescription.registerId || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="开方时间">{{ formatTime(detail.prescription.createTime) }}</ElDescriptionsItem>
              <ElDescriptionsItem label="诊断" :span="2">{{ detail.prescription.diagnosis || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="发药人">{{ detail.prescription.pharmacist || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="发药时间">{{ formatTime(detail.prescription.dispensationTime) }}</ElDescriptionsItem>
            </ElDescriptions>

            <ElTable :data="detail.details" class="mt">
              <ElTableColumn prop="drugName" label="药品" min-width="160" />
              <ElTableColumn prop="specification" label="规格" min-width="120" />
              <ElTableColumn prop="usage" label="用法" min-width="120" />
              <ElTableColumn prop="dosage" label="剂量" min-width="100" />
              <ElTableColumn prop="quantity" label="数量" min-width="80" />
              <ElTableColumn prop="totalAmount" label="金额" min-width="100" />
            </ElTable>

            <div class="rx-amount-row">
              <span class="amount-label">药品费合计</span>
              <span class="amount-value amount-value--lg">¥ {{ (detail.prescription.totalAmount ?? 0).toFixed(2) }}</span>
            </div>

            <div class="detail-actions">
              <ElButton
                v-if="(detail.prescription.dispensationStatus ?? 0) === 0"
                type="primary"
                :disabled="payButtonDisabled(detail.prescription as unknown as PrescriptionSummary)"
                :loading="payingRegisterId === detail.prescription.registerId"
                @click="pay(detail.prescription as unknown as PrescriptionSummary)"
              >
                {{ payButtonLabel(detail.prescription as unknown as PrescriptionSummary) }}
              </ElButton>

              <!-- 已发药处方：下载用药指导单 PDF -->
              <ElButton
                v-if="detail.prescription.dispensationStatus === 1"
                :type="guideStatus?.status === 'failed' ? 'warning' : 'default'"
                :loading="guideExporting || guideRetrying"
                :disabled="guideStatusLoading"
                @click="handleGuideClick"
              >
                {{ guideButtonLabel }}
              </ElButton>
            </div>
          </template>
        </div>
      </div>
    </GlassCard>

    <!-- 用药指导单 PDF 渲染容器：屏幕外，用户不可见；导出时 html2pdf 截图此 DOM -->
    <div class="mg-print-host" aria-hidden="true">
      <MedicationGuidePrintSheet ref="guidePrintSheetRef" :record="guidePrintRecord" />
    </div>
  </div>
</template>

<style scoped>
.patient-prescription {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 90%;
  margin: 0 5%;
}

.prescription-shell {
  padding: var(--space-5);
}

.shell-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.shell-header__text {
  display: grid;
  gap: var(--space-2);
}

.shell-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.shell-header p {
  color: var(--color-text-muted);
  margin: 0;
}

/* 家属切换 Tab */
.family-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  margin-bottom: var(--space-4);
}

.family-tabs__relation {
  font-size: 12px;
  opacity: 0.85;
}

/* 双栏：等宽对称 1:1 */
.split-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
  align-items: start;
}

.pane {
  min-width: 0;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  padding: var(--space-4);
  min-height: 320px;
  display: flex;
  flex-direction: column;
}

.pane__title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 15px;
  font-weight: 600;
  margin-bottom: var(--space-3);
}

/* 左栏列表 */
.rx-list {
  display: grid;
  gap: var(--space-3);
}

.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border);
}

.rx-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  padding: var(--space-3);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
  background: var(--color-bg, #fff);
}

.rx-card:hover {
  border-color: var(--color-primary);
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06);
}

.rx-card--active {
  border-color: var(--color-primary);
  background: var(--color-primary-bg, rgba(64, 158, 255, 0.08));
}

.rx-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.rx-card__id {
  font-size: 15px;
  font-weight: 600;
}

.rx-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: var(--space-2);
}

.rx-card__diagnosis {
  font-size: 13px;
  color: var(--color-text);
  margin-bottom: var(--space-2);
}

.rx-card__amount {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.rx-card__warn {
  font-size: 12px;
  color: var(--color-warning, #d97706);
  margin-top: var(--space-1);
}

.amount-label {
  color: var(--color-text-muted);
  font-size: 13px;
}

.amount-value {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-primary);
}

.amount-value--lg {
  font-size: 20px;
}

.rx-amount-row {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-3);
  margin-top: var(--space-3);
  border-top: 1px solid var(--color-border);
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.ml {
  margin-left: var(--space-2);
}

.mt {
  margin-top: var(--space-4);
}

@media (max-width: 768px) {
  .patient-prescription {
    width: 95%;
    margin: 0 2.5%;
  }

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
}
</style>

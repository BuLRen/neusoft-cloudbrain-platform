<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import ExpiringStockBanner from '@/modules/pharmacy/components/ExpiringStockBanner.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import {
  dispensationStatusName,
  RETURN_REASONS,
  REVIEW_STATUS_TONE,
} from '@/shared/constants/pharmacy'
import type {
  DispensePayload,
  Dispensing,
  PrescriptionDetailResponse,
  PrescriptionSummary,
  ReturnDrugPayload,
  ReviewResult,
} from '@/shared/types/pharmacy'

const authStore = useAuthStore()

// ===== 待发药列表 =====
const pendingLoading = ref(false)
const pendingPrescriptions = ref<PrescriptionSummary[]>([])
const selectedPrescriptionId = ref<number | undefined>()
const selectedPrescription = ref<PrescriptionDetailResponse | null>(null)
const reviewing = ref(false)

// ===== 发药单查看 =====
const dispensingDialogVisible = ref(false)
const dispensingList = ref<Dispensing[]>([])
const dispensingLoading = ref(false)

// ===== 随访跳转 =====
// 随访计划是独立页面（/pharmacy/follow-up），这里只提供"快速查看该患者"的快捷弹窗
const followUpQuickVisible = ref(false)
const followUpLoading = ref(false)
const followUpPlans = ref<import('@/shared/types/pharmacy').FollowUpPlan[]>([])

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

async function loadPendingPrescriptions() {
  pendingLoading.value = true
  try {
    pendingPrescriptions.value = await pharmacyApi.pendingPrescriptions()
    if (pendingPrescriptions.value.length > 0) {
      const stillThere = selectedPrescriptionId.value
        && pendingPrescriptions.value.some((p) => p.id === selectedPrescriptionId.value)
      const targetId = stillThere ? selectedPrescriptionId.value : pendingPrescriptions.value[0].id
      selectedPrescriptionId.value = targetId
      await loadPrescriptionDetail(targetId)
    } else {
      selectedPrescriptionId.value = undefined
      selectedPrescription.value = null
    }
  } finally {
    pendingLoading.value = false
  }
}

async function loadPrescriptionDetail(id?: number) {
  if (!id) {
    selectedPrescription.value = null
    return
  }
  selectedPrescription.value = await pharmacyApi.prescriptionDetail(id)
}

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
  const prescription = selectedPrescription.value?.prescription
  if (!prescription?.registerId || !prescription.patientId) {
    ElMessage.warning('请先选择一条待发药处方')
    return
  }
  const itemsCount = selectedPrescription.value?.details.length ?? 0
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
  await loadPendingPrescriptions()
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
    await loadPendingPrescriptions()
  } catch {
    // 用户取消
  }
}

async function viewDispensing() {
  const registerId = selectedPrescription.value?.prescription.registerId
  if (!registerId) {
    ElMessage.warning('请先选择一条处方')
    return
  }
  dispensingDialogVisible.value = true
  dispensingLoading.value = true
  try {
    dispensingList.value = await pharmacyApi.dispensingByRegister(registerId)
  } finally {
    dispensingLoading.value = false
  }
}

async function viewFollowUpPlans() {
  const patientId = selectedPrescription.value?.prescription.patientId
  if (!patientId) {
    ElMessage.warning('该处方未关联患者')
    return
  }
  followUpQuickVisible.value = true
  followUpLoading.value = true
  try {
    followUpPlans.value = await pharmacyApi.patientFollowUpPlans(patientId)
  } finally {
    followUpLoading.value = false
  }
}

async function retryFollowUpCreation(prescriptionId: number) {
  try {
    await pharmacyApi.retryFollowUp(prescriptionId)
    ElMessage.success('随访计划已重新创建')
  } catch {
    // 拦截器统一报错
  }
}

onMounted(() => {
  void loadPendingPrescriptions()
})
</script>

<template>
  <div class="dispensing-page u-page-grid">
    <PageHeader
      title="发药工作台"
      description="按挂号聚合的待发药处方。发药前可审方预检；发药后自动扣库存、生成发药单、异步创建 AI 随访。"
      eyebrow="Role B / Pharmacy · ①"
    >
      <template #actions>
        <ElButton @click="loadPendingPrescriptions">刷新待发药</ElButton>
      </template>
    </PageHeader>

    <!-- 近效期预警条（发药时需要知道）-->
    <ExpiringStockBanner />

    <GlassCard class="flow-card">
      <div class="split-grid">
        <!-- 左：待发药列表 -->
        <section class="pane">
          <div class="section-title">
            <h3>待发药处方</h3>
            <StatusTag :tone="pendingLoading ? 'warning' : 'primary'">
              {{ pendingPrescriptions.length }} 条
            </StatusTag>
          </div>
          <ElAlert v-if="pendingLoading" type="info" :closable="false" title="正在加载…" />
          <div class="prescription-list">
            <button
              v-for="item in pendingPrescriptions"
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
              </div>
            </button>
            <ElEmpty v-if="!pendingLoading && pendingPrescriptions.length === 0" description="当前暂无待发药处方" />
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
              <ElButton :loading="reviewing" @click="reviewBeforeDispense">审方预检</ElButton>
              <ElButton type="primary" @click="dispenseSelected">确认发药</ElButton>
              <ElButton type="danger" plain @click="returnSelected">退药</ElButton>
              <ElButton @click="viewFollowUpPlans">查看该患者随访</ElButton>
              <ElButton @click="viewDispensing">查看发药单</ElButton>
            </div>
          </template>
        </section>
      </div>
    </GlassCard>

    <!-- 发药单查看弹窗 -->
    <ElDialog v-model="dispensingDialogVisible" title="发药单（用药指导单）" width="720px">
      <ElEmpty v-if="!dispensingLoading && dispensingList.length === 0" description="该挂号暂无发药单" />
      <ElTable v-else v-loading="dispensingLoading" :data="dispensingList">
        <ElTableColumn prop="dispensingNo" label="发药单号" min-width="220" />
        <ElTableColumn prop="prescriptionId" label="处方 ID" min-width="90" />
        <ElTableColumn prop="amount" label="金额" min-width="100" />
        <ElTableColumn prop="pharmacist" label="发药人" min-width="100" />
        <ElTableColumn prop="dispensingTime" label="发药时间" min-width="160" />
      </ElTable>
    </ElDialog>

    <!-- 随访快捷查看弹窗（完整管理请到 /pharmacy/follow-up）-->
    <ElDialog v-model="followUpQuickVisible" title="该患者的随访计划" width="720px">
      <ElEmpty v-if="!followUpLoading && followUpPlans.length === 0" description="该患者暂无随访计划" />
      <ElTable v-else v-loading="followUpLoading" :data="followUpPlans">
        <ElTableColumn prop="planId" label="计划 ID" min-width="90" />
        <ElTableColumn prop="prescriptionId" label="处方 ID" min-width="90" />
        <ElTableColumn prop="status" label="状态" min-width="100" />
        <ElTableColumn prop="currentStage" label="当前阶段" min-width="140" />
        <ElTableColumn prop="nextFollowUpTime" label="下次随访" min-width="160" />
        <ElTableColumn label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-if="row.prescriptionId"
              link
              size="small"
              @click="retryFollowUpCreation(row.prescriptionId)"
            >重试创建</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDialog>
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
</style>

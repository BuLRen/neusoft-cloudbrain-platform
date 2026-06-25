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
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useAuthStore } from '@/app/stores/auth'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import { registrationApi } from '@/shared/api/modules/registration'
import { dispensationStatusName } from '@/shared/constants/pharmacy'
import type { PrescriptionDetailResponse, PrescriptionSummary } from '@/shared/types/pharmacy'

const authStore = useAuthStore()

const loading = ref(false)
const prescriptions = ref<PrescriptionSummary[]>([])
const payingRegisterId = ref<number | null>(null)

// 处方详情弹窗
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PrescriptionDetailResponse | null>(null)

const patientId = computed(() => authStore.currentPatientId)

async function load() {
  if (!patientId.value) {
    ElMessage.warning('未找到患者档案，请重新登录')
    return
  }
  loading.value = true
  try {
    prescriptions.value = await pharmacyApi.patientPrescriptions(patientId.value)
  } finally {
    loading.value = false
  }
}

async function viewDetail(rx: PrescriptionSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await pharmacyApi.prescriptionDetail(rx.id)
  } finally {
    detailLoading.value = false
  }
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

onMounted(load)
</script>

<template>
  <div class="patient-prescription">
    <GlassCard class="prescription-list">
      <div class="list-header">
        <h2>我的处方</h2>
        <p>医生开药完成后在此查看药费，支付后前往药房取药</p>
        <ElButton size="small" :loading="loading" @click="load">刷新</ElButton>
      </div>

      <ElAlert v-if="loading && !prescriptions.length" type="info" :closable="false" title="正在加载…" />
      <ElEmpty v-else-if="!prescriptions.length" description="暂无处方记录" />

      <div v-else class="prescription-items">
        <div
          v-for="rx in prescriptions"
          :key="rx.id"
          class="prescription-item"
        >
          <div class="rx-info">
            <div class="rx-main">
              <span class="rx-id">处方 #{{ rx.id }}</span>
              <StatusTag :tone="statusTone(rx)">{{ statusText(rx) }}</StatusTag>
              <span v-if="!rx.paid && (rx.dispensationStatus ?? 0) === 0" class="rx-warn">
                · 请先支付药品费再取药
              </span>
            </div>
            <ElDescriptions :column="3" size="small" border>
              <ElDescriptionsItem label="医生">{{ rx.physicianName || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="挂号号">{{ rx.registerId || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="开方时间">{{ rx.createTime || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="诊断" :span="3">{{ rx.diagnosis || '-' }}</ElDescriptionsItem>
            </ElDescriptions>
            <div class="rx-amount-row">
              <span class="amount-label">药品费合计</span>
              <span class="amount-value">¥ {{ (rx.totalAmount ?? 0).toFixed(2) }}</span>
            </div>
          </div>
          <div class="rx-actions">
            <ElButton size="small" @click="viewDetail(rx)">查看明细</ElButton>
            <ElButton
              v-if="(rx.dispensationStatus ?? 0) === 0"
              type="primary"
              size="small"
              :disabled="payButtonDisabled(rx)"
              :loading="payingRegisterId === rx.registerId"
              @click="pay(rx)"
            >{{ payButtonLabel(rx) }}</ElButton>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- 处方明细弹窗 -->
    <ElDialog v-model="detailVisible" title="处方明细" width="780px">
      <ElEmpty v-if="!detailLoading && !detail" description="无明细数据" />
      <template v-else>
        <ElDescriptions v-if="detail" :column="2" border>
          <ElDescriptionsItem label="患者">{{ detail.prescription.patientName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="医生">{{ detail.prescription.physicianName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="诊断" :span="2">{{ detail.prescription.diagnosis || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            {{ dispensationStatusName(detail.prescription.dispensationStatus) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="金额">¥ {{ (detail.prescription.totalAmount ?? 0).toFixed(2) }}</ElDescriptionsItem>
        </ElDescriptions>
        <ElTable v-if="detail" :data="detail.details" v-loading="detailLoading" class="mt">
          <ElTableColumn prop="drugName" label="药品" min-width="160" />
          <ElTableColumn prop="specification" label="规格" min-width="120" />
          <ElTableColumn prop="usage" label="用法" min-width="120" />
          <ElTableColumn prop="dosage" label="剂量" min-width="100" />
          <ElTableColumn prop="quantity" label="数量" min-width="80" />
          <ElTableColumn prop="totalAmount" label="金额" min-width="100" />
        </ElTable>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.patient-prescription {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.prescription-list {
  padding: var(--space-5);
}

.list-header {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.list-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.list-header p {
  color: var(--color-text-muted);
  margin: 0;
}

.prescription-items {
  display: grid;
  gap: var(--space-4);
}

.prescription-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-4);
  align-items: start;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  padding: var(--space-4);
}

.rx-main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  font-size: 16px;
  font-weight: 600;
}

.rx-warn {
  font-size: 13px;
  font-weight: normal;
  color: var(--color-warning, #d97706);
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

.amount-label {
  color: var(--color-text-muted);
}

.amount-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-primary);
}

.rx-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.mt {
  margin-top: var(--space-4);
}

@media (max-width: 768px) {
  .patient-prescription {
    width: 95%;
    margin: 0 2.5%;
  }

  .prescription-item {
    grid-template-columns: 1fr;
  }

  .rx-actions {
    flex-direction: row;
  }
}
</style>

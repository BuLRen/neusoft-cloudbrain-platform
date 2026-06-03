<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type { DrugOption, DrugStock, PharmacyTransaction, PrescriptionDetailResponse, PrescriptionSummary } from '@/shared/types/pharmacy'

const authStore = useAuthStore()
const activeTab = ref('pending')
const pendingLoading = ref(false)

const pendingPrescriptions = ref<PrescriptionSummary[]>([])
const selectedPrescriptionId = ref<number | undefined>()
const selectedPrescription = ref<PrescriptionDetailResponse | null>(null)

const drugKeyword = ref('')
const drugs = ref<DrugOption[]>([])
const lowStockDrugs = ref<DrugOption[]>([])
const selectedDrugId = ref<number | undefined>()
const inventoryRows = ref<DrugStock[]>([])
const transactions = ref<PharmacyTransaction[]>([])
const transactionDrugId = ref<number | undefined>()

const pharmacistId = computed(() => {
  const parsed = Number(authStore.userId)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
})

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
      const currentId = selectedPrescriptionId.value && pendingPrescriptions.value.some((item) => item.id === selectedPrescriptionId.value)
        ? selectedPrescriptionId.value
        : pendingPrescriptions.value[0].id
      selectedPrescriptionId.value = currentId
      await loadPrescriptionDetail(currentId)
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

async function dispenseSelected() {
  const prescription = selectedPrescription.value?.prescription
  if (!prescription?.registerId || !prescription.patientId) {
    ElMessage.warning('请先选择一条待发药处方')
    return
  }
  const result = await pharmacyApi.dispense(prescription.registerId, {
    pharmacistId: pharmacistId.value,
    pharmacistName: authStore.role,
  })
  if ((result.followUpFailedCount || 0) > 0) {
    ElMessage.warning(`发药成功，但有 ${result.followUpFailedCount} 条随访计划创建失败`)
  } else {
    ElMessage.success('发药成功，随访计划已自动创建')
  }
  await loadPendingPrescriptions()
}

async function returnSelected() {
  const prescription = selectedPrescription.value?.prescription
  if (!prescription?.registerId) {
    ElMessage.warning('请先选择一条处方')
    return
  }
  await pharmacyApi.returnDrug(prescription.registerId, {
    pharmacistId: pharmacistId.value,
    pharmacistName: authStore.role,
  })
  ElMessage.success('退药成功')
  await loadPendingPrescriptions()
}

async function loadDrugCatalog() {
  drugs.value = await pharmacyApi.drugs({ keyword: drugKeyword.value || undefined })
  lowStockDrugs.value = await pharmacyApi.lowStockDrugs()
}

async function loadInventory(drugId?: number) {
  if (!drugId) {
    inventoryRows.value = []
    return
  }
  inventoryRows.value = await pharmacyApi.inventory(drugId)
}

async function loadTransactions() {
  transactions.value = await pharmacyApi.transactions({ drugId: transactionDrugId.value })
}

function selectDrug(row: DrugOption) {
  selectedDrugId.value = row.id
  void loadInventory(row.id)
}

onMounted(async () => {
  await Promise.all([loadPendingPrescriptions(), loadDrugCatalog(), loadTransactions()])
})
</script>

<template>
  <div class="pharmacy-workspace u-page-grid">
    <PageHeader
      title="药房工作台"
      description="以待发药处方为主入口，支持处方详情查看、发药、退药，并补充药品、库存和交易记录视图。发药成功后由后端自动编排随访计划创建。"
      eyebrow="Role B / Pharmacy"
    >
      <template #actions>
        <ElButton @click="loadPendingPrescriptions">刷新待发药列表</ElButton>
        <ElButton type="primary" @click="loadDrugCatalog">刷新药品目录</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="flow-card">
      <ElTabs v-model="activeTab">
        <ElTabPane label="待发药 / 处方详情" name="pending">
          <div class="split-grid">
            <section>
              <div class="section-title">
                <h3>待发药处方</h3>
                <StatusTag :tone="pendingLoading ? 'warning' : 'primary'">{{ pendingPrescriptions.length }} 条</StatusTag>
              </div>
              <ElAlert v-if="pendingLoading" type="info" :closable="false" title="正在加载待发药处方" />
              <div class="prescription-list">
                <button
                  v-for="item in pendingPrescriptions"
                  :key="item.id"
                  class="prescription-item"
                  :class="{ 'is-active': item.id === selectedPrescriptionId }"
                  type="button"
                  @click="selectedPrescriptionId = item.id; loadPrescriptionDetail(item.id)"
                >
                  <strong>#{{ item.id }} · {{ item.patientName || '-' }}</strong>
                  <span>{{ item.diagnosis || '待补充诊断' }}</span>
                  <div class="item-meta">
                    <StatusTag :tone="statusTone(item.dispensationStatus)">{{ item.dispensationStatusName || '-' }}</StatusTag>
                    <span>{{ item.totalAmount ?? '-' }} 元</span>
                  </div>
                </button>
                <ElEmpty v-if="!pendingLoading && pendingPrescriptions.length === 0" description="当前暂无待发药处方" />
              </div>
            </section>

            <section>
              <h3>处方详情</h3>
              <ElEmpty v-if="!selectedPrescription" description="请选择一条处方查看详情" />
              <template v-else>
                <ElDescriptions :column="1" border>
                  <ElDescriptionsItem label="患者">{{ selectedPrescription.prescription.patientName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="医生">{{ selectedPrescription.prescription.physicianName || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="诊断">{{ selectedPrescription.prescription.diagnosis || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="挂号单号">{{ selectedPrescription.prescription.registerId || '-' }}</ElDescriptionsItem>
                  <ElDescriptionsItem label="总金额">{{ selectedPrescription.prescription.totalAmount ?? '-' }}</ElDescriptionsItem>
                </ElDescriptions>
                <ElTable :data="selectedPrescription.details" class="mt">
                  <ElTableColumn prop="drugName" label="药品" min-width="160" />
                  <ElTableColumn prop="specification" label="规格" min-width="140" />
                  <ElTableColumn prop="usage" label="用法" min-width="160" />
                  <ElTableColumn prop="quantity" label="数量" min-width="80" />
                  <ElTableColumn prop="totalAmount" label="金额" min-width="100" />
                </ElTable>
                <div class="actions">
                  <ElButton type="primary" @click="dispenseSelected">发药</ElButton>
                  <ElButton type="danger" plain @click="returnSelected">退药</ElButton>
                </div>
              </template>
            </section>
          </div>
        </ElTabPane>

        <ElTabPane label="药品 / 库存 / 交易记录" name="catalog">
          <div class="catalog-grid">
            <section>
              <div class="section-title">
                <h3>药品目录</h3>
                <StatusTag tone="primary">{{ drugs.length }} 条</StatusTag>
              </div>
              <div class="actions actions--compact">
                <ElInput v-model="drugKeyword" placeholder="搜索药品名称" />
                <ElButton @click="loadDrugCatalog">搜索</ElButton>
              </div>
              <ElTable :data="drugs" @row-click="selectDrug">
                <ElTableColumn prop="name" label="药品名称" min-width="160" />
                <ElTableColumn prop="specification" label="规格" min-width="120" />
                <ElTableColumn prop="price" label="单价" min-width="100" />
                <ElTableColumn prop="stockQuantity" label="库存" min-width="90" />
              </ElTable>
            </section>

            <section>
              <div class="section-title">
                <h3>库存批次</h3>
                <StatusTag :tone="selectedDrugId ? 'success' : 'warning'">{{ selectedDrugId ? `药品 #${selectedDrugId}` : '未选药品' }}</StatusTag>
              </div>
              <ElTable :data="inventoryRows">
                <ElTableColumn prop="batchNumber" label="批次号" min-width="140" />
                <ElTableColumn prop="quantity" label="数量" min-width="80" />
                <ElTableColumn prop="location" label="货位" min-width="120" />
                <ElTableColumn prop="expiryDate" label="失效日期" min-width="160" />
              </ElTable>
            </section>
          </div>

          <section class="followup-section">
            <div class="section-title">
              <h3>低库存提醒</h3>
              <StatusTag tone="warning">{{ lowStockDrugs.length }} 条</StatusTag>
            </div>
            <ElTable :data="lowStockDrugs">
              <ElTableColumn prop="name" label="药品名称" min-width="160" />
              <ElTableColumn prop="stockQuantity" label="当前库存" min-width="100" />
              <ElTableColumn prop="lowStockThreshold" label="预警阈值" min-width="120" />
            </ElTable>
          </section>

          <section class="followup-section">
            <div class="section-title">
              <h3>交易记录</h3>
              <div class="actions actions--compact actions--inline">
                <ElInputNumber v-model="transactionDrugId" :min="1" :controls="false" class="field" placeholder="按药品 ID 筛选" />
                <ElButton @click="loadTransactions">查询</ElButton>
              </div>
            </div>
            <ElTable :data="transactions">
              <ElTableColumn prop="transactionTime" label="时间" min-width="160" />
              <ElTableColumn prop="type" label="类型" min-width="120" />
              <ElTableColumn prop="drugName" label="药品" min-width="160" />
              <ElTableColumn prop="quantity" label="数量" min-width="80" />
              <ElTableColumn prop="operatorName" label="操作人" min-width="120" />
              <ElTableColumn prop="reason" label="原因" min-width="180" />
            </ElTable>
          </section>
        </ElTabPane>
      </ElTabs>
    </GlassCard>
  </div>
</template>

<style scoped>
.flow-card {
  padding: var(--space-5);
}

.split-grid,
.catalog-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.8fr) minmax(0, 1fr);
  gap: var(--space-5);
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
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.prescription-item span {
  color: var(--color-text-muted);
}

.prescription-item.is-active {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.item-meta,
.actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.actions {
  flex-wrap: wrap;
  margin-block-start: var(--space-4);
}

.actions--compact {
  margin-block-start: 0;
}

.actions--inline {
  align-items: center;
}

.mt,
.followup-section {
  margin-block-start: var(--space-5);
}

.field {
  width: 140px;
}

.flow-card :deep(.el-tabs__content) {
  padding-block-start: var(--space-4);
}

@media (max-width: 1080px) {
  .split-grid,
  .catalog-grid {
    grid-template-columns: 1fr;
  }

  .field {
    width: 100%;
  }
}
</style>

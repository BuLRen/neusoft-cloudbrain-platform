<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElCard, ElEmpty, ElMessage, ElTable, ElTableColumn } from 'element-plus'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import { physicianApi, type CheckResult, type InspectionResult, type W3Output } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import ResultPayloadViewer from '@/shared/components/ResultPayloadViewer.vue'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'
import { useLabReportExport } from '@/shared/composables/useLabReportExport'
import { buildLabReportContextFromPhysician } from '@/shared/types/labReportPdf'
import { hasExportableLabReportPayload, resolveStructuredOutputFromPayload } from '@/shared/types/simulatedCheckResult'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)
const printSheetRef = ref<InstanceType<typeof LabReportPrintSheet> | null>(null)

const { exportContext, exporting, exportPdf } = useLabReportExport()

const loading = ref(false)
const checkResults = ref<CheckResult[]>([])
const inspectionResults = ref<InspectionResult[]>([])
const w3Output = ref<W3Output | null>(null)

async function loadResults() {
  if (!registerId.value) return
  loading.value = true
  try {
    const [checks, inspections] = await Promise.all([
      physicianApi.checkResults(registerId.value),
      physicianApi.inspectionResults(registerId.value),
    ])
    checkResults.value = checks
    inspectionResults.value = inspections
  } finally {
    loading.value = false
  }
}

async function runW3() {
  if (!registerId.value) return
  loading.value = true
  try {
    w3Output.value = await physicianApi.aiW3(registerId.value)
    ElMessage.success('W3 结果分析已完成')
  } finally {
    loading.value = false
  }
}

function canExportInspectionPdf(row: InspectionResult): boolean {
  return hasExportableLabReportPayload(row.inspectionResult)
}

async function handleExportInspectionPdf(row: InspectionResult) {
  const structuredOutput = resolveStructuredOutputFromPayload(row.inspectionResult)
  if (!structuredOutput) {
    ElMessage.warning('暂无结构化检验明细，无法导出 PDF')
    return
  }
  await exportPdf(buildLabReportContextFromPhysician(row, structuredOutput, encounterStore.patientSummary), printSheetRef)
}

watch(registerId, () => {
  void loadResults()
})

onMounted(() => {
  void loadResults()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="查看结果"
    description="查看检查/检验结果，可运行 W3 生成结构化分析（非最终诊断）。"
    prev-path="/physician/orders"
    next-path="/physician/diagnosis"
  >
    <div class="results-toolbar">
      <ElButton :loading="loading" @click="loadResults">刷新结果</ElButton>
      <ElButton type="primary" :loading="loading" @click="runW3">运行 W3（AI 分析）</ElButton>
    </div>

    <h3>检查结果</h3>
    <ElEmpty v-if="!checkResults.length" description="暂无检查结果" />
    <ElTable v-else :data="checkResults">
      <ElTableColumn type="expand">
        <template #default="{ row }">
          <div class="result-expand">
            <ResultPayloadViewer :raw="row.checkResult" />
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="techName" label="项目" />
      <ElTableColumn prop="checkState" label="状态" width="100" />
      <ElTableColumn label="结果摘要" min-width="220">
        <template #default="{ row }">
          <span v-if="row.checkState === '已归档'">{{ row.checkRemark || '未执行（已归档）' }}</span>
          <ResultPayloadViewer v-else :raw="row.checkResult" compact />
        </template>
      </ElTableColumn>
      <ElTableColumn label="AI 分析">
        <template #default="{ row }">
          <span>{{ row.aiAnalysis?.analysisReport || '-' }}</span>
        </template>
      </ElTableColumn>
    </ElTable>

    <h3 style="margin-top: var(--space-4)">检验结果</h3>
    <ElEmpty v-if="!inspectionResults.length" description="暂无检验结果" />
    <ElTable v-else :data="inspectionResults">
      <ElTableColumn type="expand">
        <template #default="{ row }">
          <div class="result-expand">
            <ResultPayloadViewer :raw="row.inspectionResult" />
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="techName" label="项目" />
      <ElTableColumn prop="inspectionState" label="状态" width="100" />
      <ElTableColumn label="结果摘要" min-width="220">
        <template #default="{ row }">
          <span v-if="row.inspectionState === '已归档'">{{ row.inspectionRemark || '未执行（已归档）' }}</span>
          <ResultPayloadViewer v-else :raw="row.inspectionResult" compact />
        </template>
      </ElTableColumn>
      <ElTableColumn label="AI 分析">
        <template #default="{ row }">
          <span>{{ row.aiAnalysis?.analysisReport || '-' }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <ElButton
            link
            type="primary"
            :disabled="!canExportInspectionPdf(row)"
            :loading="exporting"
            @click="handleExportInspectionPdf(row)"
          >
            导出 PDF
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <h3 style="margin-top: var(--space-4)">W3 汇总</h3>
    <ElEmpty v-if="!w3Output" description="暂无 W3 输出，可运行 W3" />
    <div v-else class="w3-grid">
      <ElCard v-for="item in w3Output.examSummaries || []" :key="item.techName" class="mini-card">
        <strong>{{ item.techName }}</strong>
        <p v-if="item.interpretation">{{ item.interpretation }}</p>
        <p v-if="item.riskLevel">风险等级：{{ item.riskLevel }}</p>
      </ElCard>
      <ElCard v-if="w3Output.overallAnalysis" class="mini-card">
        <strong>总体分析</strong>
        <p>{{ w3Output.overallAnalysis }}</p>
      </ElCard>
    </div>
  </PhysicianStepLayout>

  <div class="lab-report-print-host" aria-hidden="true">
    <LabReportPrintSheet ref="printSheetRef" :context="exportContext" />
  </div>
</template>

<style scoped>
.results-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.result-expand {
  padding: var(--space-3) var(--space-4);
}

.w3-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.mini-card p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.8;
}

.lab-report-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  visibility: hidden;
}
</style>

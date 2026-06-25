<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import {
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElIcon,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import {
  Document,
  DocumentCopy,
  FirstAidKit,
  MagicStick,
  Odometer,
  Picture,
  Refresh,
  View,
} from '@element-plus/icons-vue'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import SimulatedCheckResultContent from '@/shared/components/SimulatedCheckResultContent.vue'
import { physicianApi, type CheckResult, type InspectionResult, type W3Output } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import ResultPayloadViewer from '@/shared/components/ResultPayloadViewer.vue'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'
import { useLabReportExport } from '@/shared/composables/useLabReportExport'
import { buildLabReportContextFromPhysician } from '@/shared/types/labReportPdf'
import {
  hasExportableLabReportPayload,
  resolveStructuredOutputFromPayload,
  type SimulatedCheckStructuredOutput,
} from '@/shared/types/simulatedCheckResult'

type StateTone = 'primary' | 'success' | 'warning' | 'neutral'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)
const printSheetRef = ref<InstanceType<typeof LabReportPrintSheet> | null>(null)

const { exportContext, exporting, exportPdf } = useLabReportExport()

const loading = ref(false)
const w3Loading = ref(false)
const checkResults = ref<CheckResult[]>([])
const inspectionResults = ref<InspectionResult[]>([])
const w3Output = ref<W3Output | null>(null)
const w3Completed = ref(false)
const inspectionDialogVisible = ref(false)
const inspectionDialogTitle = ref('检验结果')
const inspectionDialogOutput = ref<SimulatedCheckStructuredOutput | null>(null)
const inspectionDialogRow = ref<InspectionResult | null>(null)

async function loadW3Status() {
  if (!registerId.value) {
    w3Output.value = null
    w3Completed.value = false
    return
  }
  try {
    const status = await physicianApi.w3Status(registerId.value)
    w3Completed.value = status.completed
    if (status.w3Output) {
      w3Output.value = status.w3Output
    }
  } catch {
    // status 接口失败时保留已有解读结果，避免 analyze 成功后页面被清空
  }
}

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
    await loadW3Status()
  } finally {
    loading.value = false
  }
}

function hasResultPayload(raw: unknown): boolean {
  if (raw == null) return false
  const text = String(raw).trim()
  return text.length > 0 && text !== 'null' && text !== 'undefined'
}

const hasAnalyzableResults = computed(() => {
  const checks = checkResults.value.some(row => hasResultPayload(row.checkResult))
  const inspections = inspectionResults.value.some(
    row => row.inspectionState !== '已归档' && hasResultPayload(row.inspectionResult),
  )
  return checks || inspections
})

async function runW3() {
  if (!registerId.value) return
  if (!hasAnalyzableResults.value) {
    ElMessage.warning('暂无检查检验结果可供解读，请等待项目完成后再运行 W3')
    return
  }
  w3Loading.value = true
  try {
    w3Output.value = await physicianApi.aiW3(registerId.value)
    w3Completed.value = true
    await loadResults()
    ElMessage.success('W3 结果解读已完成')
  } catch {
    // http() 已展示后端错误信息
  } finally {
    w3Loading.value = false
  }
}

function resolveInspectionStructuredOutput(row: InspectionResult): SimulatedCheckStructuredOutput | null {
  return resolveStructuredOutputFromPayload(row.inspectionResult)
}

function canViewInspectionResult(row: InspectionResult): boolean {
  const structured = resolveInspectionStructuredOutput(row)
  if (!structured) return false
  if ((structured.resultItems?.length ?? 0) > 0) return true
  return Boolean(structured.conclusion?.trim() || structured.checkName?.trim())
}

function canExportInspectionPdf(row: InspectionResult): boolean {
  return hasExportableLabReportPayload(row.inspectionResult)
}

function openInspectionResultDialog(row: InspectionResult) {
  const structured = resolveInspectionStructuredOutput(row)
  if (!structured || !canViewInspectionResult(row)) return
  inspectionDialogOutput.value = structured
  inspectionDialogRow.value = row
  inspectionDialogTitle.value = row.techName ? `${row.techName} 检验结果` : '检验结果'
  inspectionDialogVisible.value = true
}

async function handleExportInspectionPdf(row: InspectionResult) {
  const structuredOutput = resolveStructuredOutputFromPayload(row.inspectionResult)
  if (!structuredOutput) {
    ElMessage.warning('暂无结构化检验明细，无法导出 PDF')
    return
  }
  await exportPdf(buildLabReportContextFromPhysician(row, structuredOutput, encounterStore.patientSummary), printSheetRef)
}

function resolveRiskLabel(level?: string): string {
  if (level === 'high') return '高风险'
  if (level === 'attention') return '需关注'
  return '正常'
}

function resolveRiskTone(level?: string): 'success' | 'warning' | 'danger' {
  if (level === 'high') return 'danger'
  if (level === 'attention') return 'warning'
  return 'success'
}

function resolveStateTone(state: string): StateTone {
  if (state === '已完成' || state === '已归档') return 'success'
  if (state === '检查中' || state === '检验中') return 'warning'
  if (state === '待检查' || state === '待检验') return 'primary'
  return 'neutral'
}

function techIcon(name: string, category: 'check' | 'inspection'): Component {
  if (/CT|MRI|X|超声|影像|片|胸|肺/.test(name)) return Picture
  if (/血|化验|检验|蛋白|常规|尿|CRP|反应/.test(name)) return Odometer
  return category === 'inspection' ? Odometer : FirstAidKit
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
    title="检查/检验结果"
    patient-card-variant="profile"
    prev-path="/physician/orders"
    next-path="/physician/diagnosis"
  >
    <template #headerActions>
      <div class="results-toolbar">
        <ElButton class="results-toolbar__refresh" :loading="loading" @click="loadResults">
          <ElIcon><Refresh /></ElIcon>
          刷新结果
        </ElButton>
        <ElButton
          class="results-toolbar__w3"
          type="primary"
          :loading="w3Loading"
          :disabled="!hasAnalyzableResults"
          @click="runW3"
        >
          <ElIcon><MagicStick /></ElIcon>
          运行 W3（结果解读）
        </ElButton>
      </div>
    </template>

    <section class="results-section">
      <header class="results-section__header">
        <span class="results-section__icon results-section__icon--check" aria-hidden="true">
          <ElIcon :size="18"><DocumentCopy /></ElIcon>
        </span>
        <h3 class="results-section__title">检查结果</h3>
      </header>
      <div class="results-section__body">
        <ElEmpty v-if="!checkResults.length" description="暂无检查结果" />
        <ElTable v-else :data="checkResults" class="results-table" stripe>
          <ElTableColumn type="expand" width="44">
            <template #default="{ row }">
              <div class="result-expand">
                <ResultPayloadViewer :raw="row.checkResult" />
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="项目" min-width="160">
            <template #default="{ row }">
              <div class="tech-cell">
                <span class="tech-cell__icon" aria-hidden="true">
                  <ElIcon><component :is="techIcon(row.techName || '', 'check')" /></ElIcon>
                </span>
                <span class="tech-cell__name">{{ row.techName }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="120">
            <template #default="{ row }">
              <span class="state-badge" :data-tone="resolveStateTone(row.checkState)">
                <span class="state-badge__dot" aria-hidden="true" />
                {{ row.checkState }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="结果摘要" min-width="280">
            <template #default="{ row }">
              <span v-if="row.checkState === '已归档'" class="result-summary result-summary--muted">
                {{ row.checkRemark || '未执行（已归档）' }}
              </span>
              <ResultPayloadViewer v-else :raw="row.checkResult" compact />
            </template>
          </ElTableColumn>
          <ElTableColumn label="AI 分析" min-width="180">
            <template #default="{ row }">
              <span class="result-summary" :class="{ 'result-summary--muted': !row.aiAnalysis?.analysisReport }">
                {{ row.aiAnalysis?.analysisReport || '-' }}
              </span>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </section>

    <section class="results-section">
      <header class="results-section__header">
        <span class="results-section__icon results-section__icon--inspection" aria-hidden="true">
          <ElIcon :size="18"><Odometer /></ElIcon>
        </span>
        <h3 class="results-section__title">检验结果</h3>
      </header>
      <div class="results-section__body">
        <ElEmpty v-if="!inspectionResults.length" description="暂无检验结果" />
        <ElTable v-else :data="inspectionResults" class="results-table" stripe>
          <ElTableColumn type="expand" width="44">
            <template #default="{ row }">
              <div class="result-expand">
                <ResultPayloadViewer :raw="row.inspectionResult" />
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="项目" min-width="160">
            <template #default="{ row }">
              <div class="tech-cell">
                <span class="tech-cell__icon tech-cell__icon--inspection" aria-hidden="true">
                  <ElIcon><component :is="techIcon(row.techName || '', 'inspection')" /></ElIcon>
                </span>
                <span class="tech-cell__name">{{ row.techName }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="120">
            <template #default="{ row }">
              <span class="state-badge" :data-tone="resolveStateTone(row.inspectionState)">
                <span class="state-badge__dot" aria-hidden="true" />
                {{ row.inspectionState }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="结果摘要" min-width="280">
            <template #default="{ row }">
              <span v-if="row.inspectionState === '已归档'" class="result-summary result-summary--muted">
                {{ row.inspectionRemark || '未执行（已归档）' }}
              </span>
              <ResultPayloadViewer v-else :raw="row.inspectionResult" compact />
            </template>
          </ElTableColumn>
          <ElTableColumn label="AI 分析" min-width="180">
            <template #default="{ row }">
              <span class="result-summary" :class="{ 'result-summary--muted': !row.aiAnalysis?.analysisReport }">
                {{ row.aiAnalysis?.analysisReport || '-' }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <div class="inspection-actions">
                <ElButton
                  text
                  type="primary"
                  :disabled="!canViewInspectionResult(row)"
                  @click="openInspectionResultDialog(row)"
                >
                  <ElIcon><View /></ElIcon>
                  查看结果
                </ElButton>
                <span class="inspection-actions__divider" aria-hidden="true" />
                <ElButton
                  text
                  type="primary"
                  :disabled="!canExportInspectionPdf(row)"
                  :loading="exporting"
                  @click="handleExportInspectionPdf(row)"
                >
                  <ElIcon><Document /></ElIcon>
                  导出 PDF
                </ElButton>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </section>

    <section class="results-section results-section--w3">
      <header class="results-section__header">
        <span class="results-section__icon results-section__icon--w3" aria-hidden="true">
          <ElIcon :size="18"><MagicStick /></ElIcon>
        </span>
        <div class="results-section__heading">
          <h3 class="results-section__title">W3 结果解读</h3>
          <p class="results-section__hint">解读检查/检验指标的临床意义，非疾病确诊。确诊请前往「门诊确诊」运行 W4。</p>
        </div>
        <ElTag v-if="w3Completed" type="info" effect="plain" class="results-section__tag">非诊断性分析</ElTag>
      </header>
      <div class="results-section__body results-section__body--padded">
        <ElEmpty v-if="!w3Output?.examSummaries?.length && !w3Output?.overallAnalysis" description="暂无 W3 解读，可运行 W3 或等待医技提交结果后自动分析" />
        <div v-else class="w3-grid">
          <ElCard v-for="item in w3Output?.examSummaries || []" :key="item.techName" class="w3-card" shadow="never">
            <div class="w3-card__head">
              <strong class="w3-card__title">{{ item.techName }}</strong>
              <ElTag v-if="item.riskLevel" :type="resolveRiskTone(item.riskLevel)" size="small" effect="light">
                {{ resolveRiskLabel(item.riskLevel) }}
              </ElTag>
            </div>
            <ul v-if="item.keyFindings?.length" class="w3-card__findings">
              <li v-for="(finding, idx) in item.keyFindings" :key="`${item.techName}-finding-${idx}`">{{ finding }}</li>
            </ul>
            <p v-if="item.interpretation" class="w3-card__text">{{ item.interpretation }}</p>
          </ElCard>
          <ElCard v-if="w3Output?.overallAnalysis" class="w3-card w3-card--overall" shadow="never">
            <strong class="w3-card__title">总体分析</strong>
            <p class="w3-card__text">{{ w3Output.overallAnalysis }}</p>
            <p v-if="w3Output.explicitNonDiagnosis !== false" class="w3-card__notice">
              以上为 AI 辅助解读，不构成最终诊断。
            </p>
          </ElCard>
        </div>
      </div>
    </section>
  </PhysicianStepLayout>

  <ElDialog
    v-model="inspectionDialogVisible"
    :title="inspectionDialogTitle"
    width="760px"
    align-center
    destroy-on-close
    class="inspection-result-dialog"
  >
    <SimulatedCheckResultContent :data="inspectionDialogOutput" />
    <template #footer>
      <ElButton @click="inspectionDialogVisible = false">关闭</ElButton>
      <ElButton
        v-if="inspectionDialogRow && canExportInspectionPdf(inspectionDialogRow)"
        :loading="exporting"
        type="primary"
        @click="handleExportInspectionPdf(inspectionDialogRow)"
      >
        导出 PDF
      </ElButton>
    </template>
  </ElDialog>

  <div class="lab-report-print-host" aria-hidden="true">
    <LabReportPrintSheet ref="printSheetRef" :context="exportContext" />
  </div>
</template>

<style scoped>
.results-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.results-toolbar__refresh {
  --el-button-bg-color: rgba(255, 255, 255, 0.82);
  border-color: rgba(31, 140, 255, 0.22);
  color: var(--color-primary-strong);
}

.results-toolbar__w3 {
  border: none;
  background: var(--gradient-primary);
  box-shadow: 0 10px 24px rgba(31, 140, 255, 0.22);
}

.results-toolbar__refresh :deep(.el-icon),
.results-toolbar__w3 :deep(.el-icon) {
  margin-inline-end: 6px;
}

.results-section {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  box-shadow: var(--shadow-sm);
}

.results-section + .results-section {
  margin-block-start: var(--space-5);
}

.results-section__header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 251, 255, 0.88));
}

.results-section__heading {
  flex: 1;
  min-width: 0;
}

.results-section__hint {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.results-section__tag {
  flex-shrink: 0;
}

.results-section__title {
  margin: 0;
  font-size: var(--font-size-md, 16px);
  font-weight: 700;
}

.results-section__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
}

.results-section__icon--check {
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.results-section__icon--inspection {
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.14);
}

.results-section__icon--w3 {
  color: var(--color-ai);
  background: rgba(124, 92, 255, 0.14);
}

.results-section__body--padded {
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.result-expand {
  padding: var(--space-3) var(--space-4);
}

.tech-cell {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.tech-cell__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.tech-cell__icon--inspection {
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.14);
}

.tech-cell__name {
  font-weight: 600;
}

.state-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(95, 114, 136, 0.1);
  color: var(--color-text-muted);
}

.state-badge__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.state-badge[data-tone='primary'] {
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.state-badge[data-tone='warning'] {
  color: var(--color-warning);
  background: rgba(245, 159, 0, 0.14);
}

.state-badge[data-tone='success'] {
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.14);
}

.result-summary {
  color: var(--color-text);
  line-height: 1.7;
}

.result-summary--muted {
  color: var(--color-text-muted);
}

.inspection-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: rgba(247, 251, 255, 0.9);
}

.inspection-actions__divider {
  width: 1px;
  height: 16px;
  background: var(--color-border);
}

.inspection-actions :deep(.el-button) {
  padding-inline: 8px;
}

.inspection-actions :deep(.el-icon) {
  margin-inline-end: 4px;
}

.w3-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.w3-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 251, 255, 0.88));
}

.w3-card--overall {
  grid-column: 1 / -1;
}

.w3-card__title {
  display: block;
  font-size: 15px;
}

.w3-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.w3-card__findings {
  margin: var(--space-2) 0 0;
  padding-inline-start: 1.2rem;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w3-card__text,
.w3-card__meta,
.w3-card__notice {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w3-card__notice {
  font-size: 13px;
  color: var(--color-ai);
}

.lab-report-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  visibility: hidden;
}

:deep(.results-table) {
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: var(--color-table-header);
}

:deep(.results-table .el-table__header th) {
  color: var(--color-text-muted);
  font-weight: 700;
}

:deep(.results-table .el-table__row td) {
  padding-block: 14px;
}

:deep(.results-table .el-table__inner-wrapper::before) {
  display: none;
}

@media (max-width: 900px) {
  .results-toolbar {
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .w3-grid {
    grid-template-columns: 1fr;
  }
}
</style>

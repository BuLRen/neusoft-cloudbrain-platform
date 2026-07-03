<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { Component } from 'vue'
import {
  ElButton,
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
import CtDiagnosisReportPrintSheet from '@/shared/components/CtDiagnosisReportPrintSheet.vue'
import SimulatedCheckResultContent from '@/shared/components/SimulatedCheckResultContent.vue'
import { physicianApi, type CheckResult, type InspectionResult, type W3Output } from '@/shared/api/modules/physician'
import type { CtAnalyzeResult } from '@/shared/api/modules/ctViewer'
import { useEncounterStore } from '@/app/stores/encounter'
import ResultPayloadViewer from '@/shared/components/ResultPayloadViewer.vue'
import CtDiagnosisReportPanel from '@/modules/medtech/components/CtDiagnosisReportPanel.vue'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'
import W3LabReportPanel from '../components/W3LabReportPanel.vue'
import { useLabReportExport } from '@/shared/composables/useLabReportExport'
import { useCtReportExport } from '@/shared/composables/useCtReportExport'
import { buildLabReportContextFromPhysician } from '@/shared/types/labReportPdf'
import { buildCtDiagnosisReportPdfContext } from '@/shared/types/ctReportPdf'
import {
  hasExportableLabReportPayload,
  isDraftResultPayload,
  resolveStructuredOutputFromPayload,
  type SimulatedCheckStructuredOutput,
} from '@/shared/types/simulatedCheckResult'
import { formatPrimaryResultSummary } from '@/shared/types/resultForm'
import type { ResultFormSchema } from '@/shared/types/resultForm'
import { hasW3Content } from '@/shared/types/w3Result'

type StateTone = 'primary' | 'success' | 'warning' | 'neutral'

interface IncompleteExamRow {
  techName: string
  category: '检查' | '检验'
  state: string
}

const TERMINAL_EXAM_STATES = new Set(['已完成', '已归档'])

const encounterStore = useEncounterStore()
const router = useRouter()
const registerId = computed(() => encounterStore.registerId)
const printSheetRef = ref<InstanceType<typeof LabReportPrintSheet> | null>(null)
const ctDiagnosisPrintRef = ref<InstanceType<typeof CtDiagnosisReportPrintSheet> | null>(null)

const { exportContext, exporting, exportPdf } = useLabReportExport()
const {
  diagnosisExportContext,
  exportingReport: exportingCtReport,
  exportDiagnosisPdf,
} = useCtReportExport()

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
const ctReportDialogVisible = ref(false)
const ctReportDialogRow = ref<CheckResult | null>(null)
const ctReportSchema = ref<ResultFormSchema | null>(null)
const ctReportSchemaLoading = ref(false)
const incompleteConfirmVisible = ref(false)
let incompleteConfirmResolver: ((proceed: boolean) => void) | null = null

const qcSeverityLabel: Record<string, string> = {
  clean: '无伪影',
  mild: '轻微',
  moderate: '中等',
  severe: '严重',
}

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

function hasResultPayload(raw: unknown, state?: string): boolean {
  if (raw == null) return false
  const text = String(raw).trim()
  if (text.length === 0 || text === 'null' || text === 'undefined') return false
  if (isDraftResultPayload(text)) return false
  if (state && !isExamTerminalState(state)) return false
  return true
}

const hasAnalyzableResults = computed(() => {
  const checks = checkResults.value.some(row => hasResultPayload(row.checkResult, row.checkState))
  const inspections = inspectionResults.value.some(row =>
    hasResultPayload(row.inspectionResult, row.inspectionState),
  )
  return checks || inspections
})

function isExamTerminalState(state?: string): boolean {
  return TERMINAL_EXAM_STATES.has(state ?? '')
}

const incompleteExamRows = computed<IncompleteExamRow[]>(() => {
  const rows: IncompleteExamRow[] = []
  for (const row of checkResults.value) {
    if (!isExamTerminalState(row.checkState)) {
      rows.push({
        techName: row.techName || '检查项目',
        category: '检查',
        state: row.checkState || '未完成',
      })
    }
  }
  for (const row of inspectionResults.value) {
    if (!isExamTerminalState(row.inspectionState)) {
      rows.push({
        techName: row.techName || '检验项目',
        category: '检验',
        state: row.inspectionState || '未完成',
      })
    }
  }
  return rows
})

const hasIncompleteExams = computed(() => incompleteExamRows.value.length > 0)

function closeIncompleteConfirm(proceed: boolean) {
  incompleteConfirmVisible.value = false
  incompleteConfirmResolver?.(proceed)
  incompleteConfirmResolver = null
}

function onIncompleteConfirmDialogClosed() {
  if (incompleteConfirmResolver) {
    closeIncompleteConfirm(false)
  }
}

function confirmRunW3WhenIncomplete(): Promise<boolean> {
  if (!hasIncompleteExams.value) return Promise.resolve(true)
  incompleteConfirmVisible.value = true
  return new Promise((resolve) => {
    incompleteConfirmResolver = resolve
  })
}

async function runW3() {
  if (!registerId.value) return
  if (!hasAnalyzableResults.value) {
    ElMessage.warning('暂无检查检验结果可供解读，请等待项目完成后再运行 W3')
    return
  }
  if (!(await confirmRunW3WhenIncomplete())) return
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

function resolveCheckSummary(raw: unknown): string {
  return formatPrimaryResultSummary(raw == null ? null : String(raw), 'checkResult')
}

function resolveInspectionSummary(raw: unknown): string {
  return formatPrimaryResultSummary(raw == null ? null : String(raw), 'inspectionResult')
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

function isCtImagingResult(row: CheckResult): boolean {
  return Boolean(row.aiCategoryCode?.startsWith('imaging_ct') || /CT/i.test(row.techName || ''))
}

function resolveQcSummary(result?: CtAnalyzeResult | null): string {
  if (!result) return '-'
  const severity = qcSeverityLabel[result.severity] ?? result.severity
  return result.has_artifact ? `检测到伪影（${severity}）` : `未见明显伪影（${severity}）`
}

function canViewCtImaging(row: CheckResult): boolean {
  return Boolean(row.hasImaging && row.imagingVolumeId && isCtImagingResult(row))
}

function canViewCtReport(row: CheckResult): boolean {
  return Boolean(isCtImagingResult(row) && row.checkState === '已完成' && hasResultPayload(row.checkResult, row.checkState))
}

function openCtReportDialog(row: CheckResult) {
  if (!canViewCtReport(row)) return
  ctReportDialogRow.value = row
  ctReportSchema.value = null
  ctReportDialogVisible.value = true
  ctReportSchemaLoading.value = true
  void physicianApi.resolveCheckResultForm(row.id)
    .then((schema) => {
      ctReportSchema.value = schema
    })
    .catch(() => {
      ElMessage.error('诊断报告加载失败')
    })
    .finally(() => {
      ctReportSchemaLoading.value = false
    })
}

function openCtExamPage(row: CheckResult) {
  if (!registerId.value || !isCtImagingResult(row)) return
  router.push({
    path: '/physician/ct-exam',
    query: {
      registerId: String(registerId.value),
      checkRequestId: String(row.id),
    },
  })
}

async function handleExportCtReport() {
  if (!ctReportDialogRow.value || !ctReportSchema.value) return
  await exportDiagnosisPdf(
    buildCtDiagnosisReportPdfContext(
      ctReportDialogRow.value,
      ctReportSchema.value,
      encounterStore.patientSummary,
    ),
    ctDiagnosisPrintRef,
  )
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
      <ElButton class="results-toolbar__refresh" :loading="loading" @click="loadResults">
        <ElIcon><Refresh /></ElIcon>
        刷新结果
      </ElButton>
    </template>

    <section class="results-section">
      <header class="results-section__header">
        <span class="results-section__icon results-section__icon--check" aria-hidden="true">
          <ElIcon :size="18"><DocumentCopy /></ElIcon>
        </span>
        <h3 class="results-section__title">检查结果</h3>
      </header>
      <div class="results-section__body" :class="{ 'results-section__body--empty': !checkResults.length }">
        <p v-if="!checkResults.length" class="results-section__empty-hint">该患者未安排此内容</p>
        <ElTable v-else :data="checkResults" class="results-table" stripe>
          <ElTableColumn type="expand" width="44">
            <template #default="{ row }">
              <div class="result-expand">
                <h4 class="result-expand__title">诊断报告</h4>
                <ResultPayloadViewer :raw="row.checkResult" />
                <template v-if="row.hasImagingAnalysis && row.imagingAnalysisResult">
                  <h4 class="result-expand__title result-expand__title--qc">影像质控</h4>
                  <div class="result-expand__qc">
                    <p>{{ resolveQcSummary(row.imagingAnalysisResult) }}</p>
                    <p v-if="row.imagingAnalysisResult.has_artifact" class="result-expand__qc-note">
                      金属伪影概率 {{ Math.round((row.imagingAnalysisResult.artifact_types?.metal ?? 0) * 1000) / 10 }}%，供参考，不影响诊断报告正文。
                    </p>
                  </div>
                </template>
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
              <div class="state-cell">
                <span class="state-badge" :data-tone="resolveStateTone(row.checkState)">
                  <span class="state-badge__dot" aria-hidden="true" />
                  {{ row.checkState }}
                </span>
                <ElTag v-if="row.hasImaging" type="info" size="small" class="state-cell__imaging">影像已采集</ElTag>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="结果摘要" min-width="280">
            <template #default="{ row }">
              <span v-if="row.checkState === '已归档'" class="result-summary result-summary--muted">
                {{ row.checkRemark || '未执行（已归档）' }}
              </span>
              <span v-else class="result-summary result-summary--clamped">{{ resolveCheckSummary(row.checkResult) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="影像质控" min-width="140">
            <template #default="{ row }">
              <span
                v-if="row.hasImagingAnalysis"
                class="result-summary"
                :class="{ 'result-summary--muted': !row.imagingAnalysisResult }"
              >
                {{ resolveQcSummary(row.imagingAnalysisResult) }}
              </span>
              <span v-else-if="row.hasImaging" class="result-summary result-summary--muted">待质控分析</span>
              <span v-else class="result-summary result-summary--muted">-</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="AI 分析" min-width="180">
            <template #default="{ row }">
              <span class="result-summary" :class="{ 'result-summary--muted': !row.aiAnalysis?.analysisReport }">
                {{ row.aiAnalysis?.analysisReport || '-' }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div v-if="isCtImagingResult(row)" class="ct-actions">
                <ElButton
                  v-if="canViewCtReport(row)"
                  text
                  type="primary"
                  size="small"
                  @click="openCtReportDialog(row)"
                >
                  <ElIcon><Document /></ElIcon>
                  查看报告
                </ElButton>
                <ElButton
                  v-if="canViewCtImaging(row)"
                  text
                  type="primary"
                  size="small"
                  @click="openCtExamPage(row)"
                >
                  <ElIcon><View /></ElIcon>
                  进入阅片
                </ElButton>
              </div>
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
      <div class="results-section__body" :class="{ 'results-section__body--empty': !inspectionResults.length }">
        <p v-if="!inspectionResults.length" class="results-section__empty-hint">暂无检验结果</p>
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
              <span v-else class="result-summary result-summary--clamped">{{ resolveInspectionSummary(row.inspectionResult) }}</span>
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
          <h3 class="results-section__title">检查检验结果智能解读</h3>
        </div>
        <div class="results-section__actions">
          <ElTag v-if="w3Completed" type="info" effect="plain" class="results-section__tag">非诊断性分析</ElTag>
          <ElButton
            type="primary"
            size="default"
            :loading="w3Loading"
            :disabled="!hasAnalyzableResults"
            @click="runW3"
          >
            <ElIcon><MagicStick /></ElIcon>
            检查检验结果智能分析
          </ElButton>
        </div>
      </header>
      <div class="results-section__body results-section__body--padded">
        <ElEmpty v-if="!hasW3Content(w3Output)" description="暂无解读，可点击「检查检验结果智能分析」或等待医技提交结果后自动分析" />
        <W3LabReportPanel v-else :data="w3Output" />
      </div>
    </section>
  </PhysicianStepLayout>

  <ElDialog
    v-model="incompleteConfirmVisible"
    title="检查尚未全部完成"
    width="560px"
    align-center
    class="incomplete-exam-dialog"
    @closed="onIncompleteConfirmDialogClosed"
  >
    <p class="incomplete-exam-dialog__lead">
      以下检查/检验尚未全部完成，基于当前已有结果运行分析可能不完整，是否仍要运行？
    </p>
    <ElTable :data="incompleteExamRows" class="incomplete-exam-dialog__table" stripe size="small">
      <ElTableColumn prop="techName" label="项目" min-width="168" />
      <ElTableColumn prop="category" label="类型" width="80" />
      <ElTableColumn label="状态" width="112">
        <template #default="{ row }">
          <span class="state-badge" :data-tone="resolveStateTone(row.state)">
            <span class="state-badge__dot" aria-hidden="true" />
            {{ row.state }}
          </span>
        </template>
      </ElTableColumn>
    </ElTable>
    <template #footer>
      <ElButton @click="closeIncompleteConfirm(false)">取消</ElButton>
      <ElButton type="primary" @click="closeIncompleteConfirm(true)">仍要运行</ElButton>
    </template>
  </ElDialog>

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

  <ElDialog
    v-model="ctReportDialogVisible"
    :title="ctReportDialogRow?.techName ? `${ctReportDialogRow.techName} 诊断报告` : 'CT 诊断报告'"
    width="720px"
    align-center
    destroy-on-close
    class="physician-ct-report-dialog"
  >
    <div v-loading="ctReportSchemaLoading" class="physician-ct-report-dialog__body">
      <CtDiagnosisReportPanel
        v-if="ctReportDialogRow && ctReportSchema"
        embedded
        :check-request-id="ctReportDialogRow.id"
        :can-edit="false"
        :readonly-schema="ctReportSchema"
        :analysis-result="ctReportDialogRow.imagingAnalysisResult"
      />
      <ResultPayloadViewer
        v-else-if="ctReportDialogRow && !ctReportSchemaLoading"
        :raw="ctReportDialogRow.checkResult"
      />
    </div>
    <template #footer>
      <ElButton @click="ctReportDialogVisible = false">关闭</ElButton>
      <ElButton
        type="primary"
        :loading="exportingCtReport"
        :disabled="!ctReportSchema"
        @click="handleExportCtReport"
      >
        导出 PDF
      </ElButton>
    </template>
  </ElDialog>

  <div class="lab-report-print-host" aria-hidden="true">
    <LabReportPrintSheet ref="printSheetRef" :context="exportContext" />
    <CtDiagnosisReportPrintSheet ref="ctDiagnosisPrintRef" :context="diagnosisExportContext" />
  </div>
</template>

<style scoped>
.results-toolbar__refresh {
  --el-button-bg-color: rgba(255, 255, 255, 0.82);
  border-color: rgba(31, 140, 255, 0.22);
  color: var(--color-primary-strong);
}

.results-toolbar__refresh :deep(.el-icon) {
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

.results-section__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--space-3);
}

.results-section__tag {
  flex-shrink: 0;
}

.results-section__w3-btn {
  border: none;
  background: var(--gradient-primary);
  box-shadow: 0 6px 18px rgba(31, 140, 255, 0.2);
}

.results-section__w3-btn :deep(.el-icon) {
  margin-inline-end: 4px;
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

.results-section__body--empty {
  padding: var(--space-3) var(--space-5);
}

.results-section__empty-hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm, 14px);
  line-height: 1.6;
  text-align: center;
}

.result-expand {
  padding: var(--space-3) var(--space-4);
}

.result-expand__title {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text);
}

.result-expand__title--qc {
  margin-top: var(--space-4);
  color: var(--color-text-muted);
}

.result-expand__qc {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(247, 251, 255, 0.9);
  border: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.result-expand__qc-note {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-xs, 12px);
}

.physician-ct-viewer-dialog__body {
  height: min(78vh, 820px);
  min-height: 480px;
}

:deep(.physician-ct-viewer-dialog .el-dialog__body) {
  padding: 0 12px 12px;
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

.state-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.state-cell__imaging {
  margin: 0;
}

.result-summary {
  color: var(--color-text);
  line-height: 1.7;
}

.result-summary--muted {
  color: var(--color-text-muted);
}

.result-summary--clamped {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
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

.ct-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 4px;
}

.ct-actions :deep(.el-button) {
  padding-inline: 6px;
}

.ct-actions :deep(.el-icon) {
  margin-inline-end: 4px;
}

.physician-ct-report-dialog__body {
  min-height: 240px;
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
  .results-section__header {
    flex-wrap: wrap;
  }

  .results-section__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>

<style>
.incomplete-exam-dialog__lead {
  margin: 0 0 var(--space-4);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.incomplete-exam-dialog__table {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.incomplete-exam-dialog__table .el-table__header th {
  background: var(--color-table-header);
  color: var(--color-text-muted);
  font-weight: 600;
}
</style>

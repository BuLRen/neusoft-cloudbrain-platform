<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Aim,
  Connection,
  Document,
  DocumentCopy,
  Grid,
  Location,
  Odometer,
  Postcard,
  User,
  VideoPlay,
} from '@element-plus/icons-vue'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import { medtechApi, type InspectionReport } from '@/shared/api/modules/medtech'
import { resultFormApi } from '@/shared/api/modules/resultForm'
import DynamicResultForm from '@/shared/components/DynamicResultForm.vue'
import SimulatedCheckResultContent from '@/shared/components/SimulatedCheckResultContent.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import type { ResultFormSchema } from '@/shared/types/resultForm'
import {
  resolveSimulationDisplayOutput,
  type SimulatedCheckStructuredOutput,
} from '@/shared/types/simulatedCheckResult'
import { buildLabReportContextFromMedtech, canExportLabReport } from '@/shared/types/labReportPdf'
import { useLabReportExport } from '@/shared/composables/useLabReportExport'
import { ElAlert, ElButton, ElDialog, ElEmpty, ElIcon, ElMessage, ElSwitch } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const specimenLoading = ref(false)
const simulating = ref(false)
const errorMessage = ref('')
const simulateError = ref('')
const report = ref<InspectionReport | null>(null)
const schema = ref<ResultFormSchema | null>(null)
const formValues = ref<Record<string, unknown>>({})
const formRef = ref<InstanceType<typeof DynamicResultForm>>()
const started = ref(false)
const specimenRecorded = ref(false)
const isNormal = ref(false)
const structuredOutput = ref<SimulatedCheckStructuredOutput | null>(null)
const dialogVisible = ref(false)
const printSheetRef = ref<InstanceType<typeof LabReportPrintSheet> | null>(null)

const { exportContext, exporting, exportPdf } = useLabReportExport()

const id = computed(() => Number(route.query.id || 0))
const canRecordSpecimen = computed(() => started.value && !specimenLoading.value && !specimenRecorded.value)
const canSimulate = computed(() => started.value && !loading.value && !simulating.value)
const canSubmit = computed(() => started.value && !!schema.value && !loading.value && !simulating.value)

const statusTone = computed(() => {
  const state = report.value?.statusText || report.value?.inspectionState || ''
  if (state === '检验中') return 'primary'
  if (state === '已完成') return 'success'
  if (state === '待检验') return 'warning'
  return 'neutral'
})

function hasDisplayableStructuredOutput(data: SimulatedCheckStructuredOutput | null): boolean {
  if (!data) return false
  if ((data.resultItems?.length ?? 0) > 0) return true
  return !!data.conclusion?.trim() || !!data.checkName?.trim()
}

const canViewResult = computed(() => hasDisplayableStructuredOutput(structuredOutput.value))
const dialogTitle = computed(() =>
  structuredOutput.value?.checkName ? `${structuredOutput.value.checkName} 模拟结果` : '模拟检验结果',
)

async function loadPage() {
  if (!id.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    report.value = await medtechApi.inspectionReport(id.value)
    schema.value = await resultFormApi.resolveInspectionForm({ inspectionRequestId: id.value })
    formValues.value = { ...(schema.value.existingValues ?? {}) }
    specimenRecorded.value = !!report.value.inspectionTime

    if (report.value.inspectionState === '待检验') {
      await medtechApi.startInspection(id.value)
      report.value = { ...report.value, inspectionState: '检验中', statusText: '检验中' }
    }
    started.value = report.value.inspectionState === '检验中'
  } catch {
    report.value = null
    schema.value = null
    errorMessage.value = '检验申请加载失败，请返回列表重试'
  } finally {
    loading.value = false
  }
}

async function recordSpecimen() {
  if (!id.value || !canRecordSpecimen.value) return
  specimenLoading.value = true
  try {
    await medtechApi.recordInspectionSpecimen(id.value)
    specimenRecorded.value = true
    ElMessage.success('采样已记录')
  } catch {
    ElMessage.error('采样记录失败，请稍后重试')
  } finally {
    specimenLoading.value = false
  }
}

async function runSimulation() {
  if (!id.value || !canSimulate.value) return
  simulating.value = true
  simulateError.value = ''
  structuredOutput.value = null
  try {
    const result = await medtechApi.simulateInspection(id.value, { normal_status: isNormal.value })

    structuredOutput.value = resolveSimulationDisplayOutput(result, {
      defaultCheckName: report.value?.techName,
    })
    if (result.simulatedValues) {
      formValues.value = { ...formValues.value, ...result.simulatedValues }
    }
    if (result.source === 'workflow') {
      ElMessage.success('模拟检验完成（Dify 工作流），请确认后提交')
      openResultDialog()
    } else {
      const hint = result.difyError ? `Dify 调用失败：${result.difyError}` : '未检测到 Dify 配置'
      ElMessage.warning(`模拟检验完成（内置模拟）。${hint}`)
      openResultDialog()
    }
  } catch {
    simulateError.value = '模拟检验失败，请稍后重试或手动录入'
  } finally {
    simulating.value = false
  }
}

function openResultDialog() {
  if (!hasDisplayableStructuredOutput(structuredOutput.value)) return
  dialogVisible.value = true
}

async function handleExportPdf() {
  if (!report.value || !canExportLabReport(structuredOutput.value)) {
    ElMessage.warning('暂无结构化检验明细，无法导出 PDF')
    return
  }
  await exportPdf(buildLabReportContextFromMedtech(report.value, structuredOutput.value!), printSheetRef)
}

async function submit() {
  if (!id.value || !schema.value) return
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    await medtechApi.submitInspectionResult(id.value, {
      values: formValues.value,
      structuredOutput: structuredOutput.value ?? undefined,
    })
    ElMessage.success('检验结果已提交')
    router.push('/medtech/check-queue')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <div class="inspection-page u-page-grid">
    <ElEmpty v-if="!id" description="请从医技申请列表选择一条检验记录" />

    <template v-else>
      <ElAlert
        v-if="errorMessage"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
        class="section-alert"
      />

      <GlassCard v-if="report" v-loading="loading" class="info-card">
        <header class="info-card__header">
          <div class="card-heading">
            <span class="card-heading__icon card-heading__icon--primary" aria-hidden="true">
              <ElIcon :size="18"><Document /></ElIcon>
            </span>
            <h2 class="card-heading__title">申请信息</h2>
          </div>
          <nav class="step-pills" aria-label="检验流程进度">
            <span class="step-pill step-pill--done">1 申请信息</span>
            <span class="step-pill step-pill--active">2 检验执行</span>
            <span class="step-pill">3 结果录入</span>
          </nav>
        </header>

        <div class="info-grid">
          <div class="info-item">
            <span class="info-item__icon info-item__icon--blue" aria-hidden="true">
              <ElIcon :size="18"><Postcard /></ElIcon>
            </span>
            <div class="info-item__content">
              <span class="info-item__label">病历号</span>
              <span class="info-item__value">{{ report.caseNumber || '-' }}</span>
            </div>
          </div>
          <div class="info-item">
            <span class="info-item__icon info-item__icon--violet" aria-hidden="true">
              <ElIcon :size="18"><User /></ElIcon>
            </span>
            <div class="info-item__content">
              <span class="info-item__label">患者</span>
              <span class="info-item__value">{{ report.patientName || '-' }}</span>
            </div>
          </div>
          <div class="info-item">
            <span class="info-item__icon info-item__icon--teal" aria-hidden="true">
              <ElIcon :size="18"><Odometer /></ElIcon>
            </span>
            <div class="info-item__content">
              <span class="info-item__label">检验项目</span>
              <span class="info-item__value">{{ report.techName || '-' }}</span>
            </div>
          </div>
          <div class="info-item">
            <span class="info-item__icon info-item__icon--cyan" aria-hidden="true">
              <ElIcon :size="18"><DocumentCopy /></ElIcon>
            </span>
            <div class="info-item__content">
              <span class="info-item__label">状态</span>
              <StatusTag :tone="statusTone">
                {{ report.statusText || report.inspectionState || '-' }}
              </StatusTag>
            </div>
          </div>
          <div class="info-item">
            <span class="info-item__icon info-item__icon--rose" aria-hidden="true">
              <ElIcon :size="18"><Location /></ElIcon>
            </span>
            <div class="info-item__content">
              <span class="info-item__label">检验部位</span>
              <span class="info-item__value">{{ report.position || '-' }}</span>
            </div>
          </div>
          <div class="info-item">
            <span class="info-item__icon info-item__icon--amber" aria-hidden="true">
              <ElIcon :size="18"><Grid /></ElIcon>
            </span>
            <div class="info-item__content">
              <span class="info-item__label">项目编码</span>
              <span class="info-item__value">{{ report.techCode || '-' }}</span>
            </div>
          </div>
          <div class="info-item info-item--wide">
            <span class="info-item__icon info-item__icon--indigo" aria-hidden="true">
              <ElIcon :size="18"><Aim /></ElIcon>
            </span>
            <div class="info-item__content">
              <span class="info-item__label">目的要求</span>
              <span class="info-item__value">{{ report.info || '-' }}</span>
            </div>
          </div>
        </div>
      </GlassCard>

      <div v-if="started" class="action-row">
        <GlassCard class="action-card action-card--execute">
          <div class="action-card__watermark" aria-hidden="true">
            <ElIcon :size="120"><Odometer /></ElIcon>
          </div>
          <div class="action-card__body">
            <div class="card-heading">
              <span class="card-heading__icon card-heading__icon--primary" aria-hidden="true">
                <ElIcon :size="20"><Odometer /></ElIcon>
              </span>
              <h2 class="card-heading__title">检验执行</h2>
            </div>
            <p class="action-card__hint">
              可先记录采样，再运行模拟检验生成初步结果，也可直接录入后提交。
            </p>
            <ElButton
              class="action-card__btn action-card__btn--outline"
              :loading="specimenLoading"
              :disabled="!canRecordSpecimen"
              @click="recordSpecimen"
            >
              <ElIcon><DocumentCopy /></ElIcon>
              {{ specimenRecorded ? '已记录采样' : '记录采样' }}
            </ElButton>
          </div>
        </GlassCard>

        <GlassCard v-if="schema" class="action-card action-card--simulate">
          <div class="action-card__watermark" aria-hidden="true">
            <ElIcon :size="120"><Connection /></ElIcon>
          </div>
          <div class="action-card__body">
            <div class="card-heading">
              <span class="card-heading__icon card-heading__icon--success" aria-hidden="true">
                <ElIcon :size="20"><Connection /></ElIcon>
              </span>
              <h2 class="card-heading__title">模拟工作流</h2>
            </div>
            <p class="action-card__hint">
              运行模拟检验工作流生成初步结果，可在下方修改后提交。
            </p>
            <div class="simulate-options">
              <span class="simulate-options__label">模拟为正常结果</span>
              <ElSwitch v-model="isNormal" :disabled="simulating" />
            </div>
            <div class="action-buttons">
              <ElButton
                type="primary"
                class="action-card__btn action-card__btn--primary"
                :loading="simulating"
                :disabled="!canSimulate"
                @click="runSimulation"
              >
                <ElIcon><VideoPlay /></ElIcon>
                运行模拟检验
              </ElButton>
              <ElButton v-if="canViewResult" @click="openResultDialog">查看结果</ElButton>
              <ElButton
                v-if="canViewResult && canExportLabReport(structuredOutput)"
                :loading="exporting"
                @click="handleExportPdf"
              >
                导出 PDF
              </ElButton>
            </div>
            <ElAlert
              v-if="simulateError"
              type="warning"
              :title="simulateError"
              show-icon
              :closable="false"
              class="section-alert"
            />
          </div>
        </GlassCard>
      </div>

      <GlassCard v-if="schema" class="form-card">
        <header class="form-card__header">
          <div class="card-heading">
            <span class="card-heading__icon card-heading__icon--primary" aria-hidden="true">
              <ElIcon :size="18"><Document /></ElIcon>
            </span>
            <h2 class="card-heading__title">结果录入</h2>
          </div>
        </header>
        <p v-if="schema.extensionFieldCount" class="form-meta">
          表单分类：{{ schema.categoryName || schema.categoryCode }} ·
          {{ schema.baseFieldCount }} 个基础字段、{{ schema.extensionFieldCount }} 个扩展字段
        </p>
        <DynamicResultForm
          ref="formRef"
          v-model="formValues"
          :fields="schema.fields"
          :base-field-count="schema.baseFieldCount"
          class="result-form"
        />
        <div class="actions">
          <ElButton @click="router.push('/medtech/check-queue')">返回列表</ElButton>
          <ElButton type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
            提交检验结果
          </ElButton>
        </div>
      </GlassCard>
    </template>
  </div>

  <ElDialog v-model="dialogVisible" :title="dialogTitle" width="760px" align-center destroy-on-close>
    <SimulatedCheckResultContent :data="structuredOutput" />
    <template #footer>
      <ElButton @click="dialogVisible = false">关闭</ElButton>
      <ElButton
        v-if="canExportLabReport(structuredOutput)"
        :loading="exporting"
        type="primary"
        @click="handleExportPdf"
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
.inspection-page {
  padding-block-end: var(--space-4);
}

.section-alert {
  margin-block-end: 0;
}

.info-card,
.form-card {
  padding: var(--space-5) var(--space-6);
}

.info-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-block-end: var(--space-5);
  flex-wrap: wrap;
}

.card-heading {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.card-heading__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.card-heading__icon--primary {
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.card-heading__icon--success {
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.14);
}

.card-heading__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.step-pills {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.step-pill {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 var(--space-3);
  border-radius: 999px;
  color: var(--color-text-soft);
  font-size: 13px;
  font-weight: 600;
  background: rgba(95, 114, 136, 0.08);
}

.step-pill--done {
  color: var(--color-success);
  background: rgba(32, 180, 134, 0.12);
}

.step-pill--active {
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4) var(--space-5);
}

.info-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
}

.info-item--wide {
  grid-column: span 2;
}

.info-item__icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.info-item__icon--blue {
  color: #1f8cff;
  background: rgba(31, 140, 255, 0.12);
}

.info-item__icon--violet {
  color: #7c5cff;
  background: rgba(124, 92, 255, 0.12);
}

.info-item__icon--teal {
  color: #20b486;
  background: rgba(32, 180, 134, 0.12);
}

.info-item__icon--cyan {
  color: #0ea5e9;
  background: rgba(14, 165, 233, 0.12);
}

.info-item__icon--rose {
  color: #f43f5e;
  background: rgba(244, 63, 94, 0.1);
}

.info-item__icon--amber {
  color: #f59f00;
  background: rgba(245, 159, 0, 0.12);
}

.info-item__icon--indigo {
  color: #6366f1;
  background: rgba(99, 102, 241, 0.12);
}

.info-item__content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.info-item__label {
  color: var(--color-text-soft);
  font-size: 12px;
  font-weight: 600;
}

.info-item__value {
  color: var(--color-text);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  word-break: break-word;
}

.action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.action-card {
  position: relative;
  overflow: hidden;
  min-height: 220px;
}

.action-card__body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  height: 100%;
  padding: var(--space-5) var(--space-6);
}

.action-card__watermark {
  position: absolute;
  inset-block-end: -12px;
  inset-inline-end: -8px;
  color: var(--color-primary);
  opacity: 0.07;
  pointer-events: none;
}

.action-card--simulate .action-card__watermark {
  color: var(--color-success);
}

.action-card__hint {
  margin: var(--space-3) 0 var(--space-4);
  max-width: 360px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.action-card__btn {
  margin-block-start: auto;
}

.action-card__btn--outline {
  --el-button-bg-color: var(--color-surface-strong);
  --el-button-border-color: rgba(31, 140, 255, 0.35);
  --el-button-text-color: var(--color-primary-strong);
  --el-button-hover-bg-color: var(--color-primary-soft);
  --el-button-hover-border-color: var(--color-primary);
  --el-button-hover-text-color: var(--color-primary-strong);
}

.action-card__btn--primary :deep(.el-icon) {
  margin-inline-end: 2px;
}

.simulate-options {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-block-end: var(--space-3);
}

.simulate-options__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-weight: 600;
}

.action-buttons {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-block-end: var(--space-3);
}

.form-card__header {
  margin-block-end: var(--space-4);
}

.form-meta {
  margin: 0 0 var(--space-4);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.result-form :deep(.el-form-item__label) {
  color: var(--color-text);
  font-weight: 600;
}

.result-form :deep(.el-textarea__inner) {
  min-height: 160px;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
}

.result-form :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: var(--space-5);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}

.lab-report-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  visibility: hidden;
}

@media (max-width: 1080px) {
  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .info-item--wide {
    grid-column: 1 / -1;
  }

  .action-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-card,
  .form-card,
  .action-card__body {
    padding: var(--space-4);
  }
}
</style>

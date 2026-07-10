<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import CtViewerPanel from '@/modules/medtech/ct-viewer/components/CtViewerPanel.vue'
import type { CtPatientInfoField } from '@/modules/medtech/ct-viewer/components/CtPatientInfoPanel.vue'
import CtArtifactAnalysisDialog from '@/modules/medtech/ct-viewer/components/CtArtifactAnalysisDialog.vue'
import CtAiSegmentationPanel from '@/modules/medtech/ct-viewer/components/CtAiSegmentationPanel.vue'
import CtDiagnosisReportPanel from '@/modules/medtech/components/CtDiagnosisReportPanel.vue'
import type {
  CtAiModelOption,
  CtAnalyzeResult,
  CtLesionItem,
  CtSegmentResult,
  CtVolumeMeta,
} from '@/shared/api/modules/ctViewer'
import { checkCtViewerHealth } from '@/shared/api/modules/ctViewer'
import { medtechApi } from '@/shared/api/modules/medtech'
import { useCtCheckContext } from '@/modules/medtech/composables/useCtCheckContext'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

const route = useRoute()
const router = useRouter()
const viewerPanelRef = ref<InstanceType<typeof CtViewerPanel>>()
const reportPanelRef = ref<HTMLElement | null>(null)
const volumeMeta = ref<CtVolumeMeta | null>(null)
const analyzing = ref(false)
const segmenting = ref(false)
const aiSegmenting = ref(false)
const analysisDialogVisible = ref(false)
const analysisResult = ref<CtAnalyzeResult | null>(null)
const segmentationResult = ref<CtSegmentResult | null>(null)
const analysisError = ref('')
const segmentationError = ref('')
const aiCtReady = ref(false)
const algoReady = ref(false)
const lungNoduleReady = ref(false)
const availableAiModels = ref<CtAiModelOption[]>([])
const selectedAiModelId = ref<string>('')
const reportPanelVisible = ref(true)
const segmentPanelVisible = ref(false)
const aiSegmentElapsedSeconds = ref(0)
const aiSegmentPhase = ref('')
let aiSegmentTimer: number | undefined
let aiSegmentStartedAt = 0

const id = computed(() => Number(route.query.id || 0))

const {
  loading,
  imagingBinding,
  errorMessage,
  report,
  started,
  imagingVolumeId,
  imagingSourceName,
  hasImaging,
  canInfer,
  loadCheckContext,
  bindImaging,
  clearImaging,
} = useCtCheckContext()

const examTitle = computed(() => report.value?.techName || 'CT 影像检查')

const canEditReport = computed(
  () => started.value && report.value?.checkState === '检查中' && report.value?.paid !== false,
)

const patientInfoFields = computed<CtPatientInfoField[]>(() => {
  const meta = volumeMeta.value
  return [
    { label: '检查号', value: report.value?.id },
    { label: '病历号', value: report.value?.caseNumber },
    { label: '检查部位', value: report.value?.position || examTitle.value },
    { label: '检查日期', value: report.value?.checkTime || report.value?.creationTime },
    { label: '影像来源', value: imagingSourceName.value },
    { label: '矩阵', value: meta?.size_xyz?.length ? meta.size_xyz.join(' × ') : undefined },
    {
      label: '体素间距(mm)',
      value: meta?.spacing_xyz?.length ? meta.spacing_xyz.map((v) => v.toFixed(2)).join(' × ') : undefined,
    },
  ]
})

const technicalSubline = computed(() => {
  const meta = volumeMeta.value
  const parts: string[] = []
  if (imagingSourceName.value) parts.push(`DICOM 来源：${imagingSourceName.value}`)
  if (meta?.spacing_xyz?.length) {
    parts.push(`体素间距 ${meta.spacing_xyz.map((v) => v.toFixed(2)).join(' × ')} mm`)
  }
  if (meta?.size_xyz?.length) {
    parts.push(`矩阵 ${meta.size_xyz.join(' × ')}`)
  }
  if (meta?.file_count) parts.push(`${meta.file_count} 张`)
  return parts.join(' · ')
})

const selectedAiModelIsHeavy = computed(() => {
  const current = availableAiModels.value.find((m) => m.id === selectedAiModelId.value)
  // monai/nnunet 是整卷 3D 推理，CPU 上耗时明显；segnet 逐切片 2D 推理通常很快。
  return current ? current.backend !== 'segnet' : true
})

const aiSegmentStatusText = computed(() => {
  if (!aiSegmenting.value) return ''
  if (aiSegmentPhase.value) return aiSegmentPhase.value
  if (!selectedAiModelIsHeavy.value) {
    return '正在提交 AI 分割请求'
  }
  if (aiSegmentElapsedSeconds.value >= 600) {
    return '3D 模型 CPU 推理仍在运行，较大的 CT 可能需要 10–30 分钟'
  }
  if (aiSegmentElapsedSeconds.value >= 120) {
    return '3D 模型正在整卷推理，期间页面会持续等待结果'
  }
  if (aiSegmentElapsedSeconds.value >= 30) {
    return '正在预处理 CT 并提交 AI 模型推理'
  }
  return '正在提交 AI 分割请求'
})

function startAiSegmentProgress() {
  stopAiSegmentProgress()
  aiSegmentStartedAt = Date.now()
  aiSegmentElapsedSeconds.value = 0
  aiSegmentPhase.value = '正在提交 AI 分割请求'
  aiSegmentTimer = window.setInterval(() => {
    aiSegmentElapsedSeconds.value = Math.floor((Date.now() - aiSegmentStartedAt) / 1000)
    if (aiSegmentElapsedSeconds.value > 0 && aiSegmentElapsedSeconds.value % 5 === 0) {
      void refreshAiSegmentProgress()
    }
  }, 1000)
}

function stopAiSegmentProgress() {
  if (aiSegmentTimer != null) {
    window.clearInterval(aiSegmentTimer)
    aiSegmentTimer = undefined
  }
  aiSegmentStartedAt = 0
}

async function refreshAiSegmentProgress() {
  if (!aiSegmenting.value) return
  try {
    const health = await checkCtViewerHealth()
    const status = health.lungNoduleStatus
    if (status?.inference_phase) {
      aiSegmentPhase.value = status.inference_phase
    }
    if (typeof status?.inference_elapsed_seconds === 'number' && status.inference_elapsed_seconds > 0) {
      aiSegmentElapsedSeconds.value = status.inference_elapsed_seconds
    }
  } catch {
    // 状态轮询失败不影响主请求，继续显示本地计时。
  }
}

function resetImagingDerivedState() {
  segmentationResult.value = null
  segmentationError.value = ''
  analysisResult.value = null
  analysisError.value = ''
  viewerPanelRef.value?.clearSegmentationOverlay()
}

async function handleImagingUploaded(payload: { volumeId: string; sourceName: string }) {
  if (!id.value) return
  resetImagingDerivedState()
  await bindImaging(id.value, payload, () => viewerPanelRef.value?.resetVolumeState())
}

async function handleImagingCleared() {
  if (!id.value) return
  await clearImaging(id.value)
  volumeMeta.value = null
  resetImagingDerivedState()
}

function handleMetaUpdated(meta: CtVolumeMeta | null) {
  volumeMeta.value = meta
}

async function refreshAiCtHealth() {
  try {
    const health = await checkCtViewerHealth()
    aiCtReady.value = Boolean(health.aiCtReady)
    algoReady.value = Boolean(health.algoReady)
    lungNoduleReady.value = Boolean(health.lungNoduleReady)

    const status = health.lungNoduleStatus
    availableAiModels.value = status?.available_models ?? []
    const currentStillValid = availableAiModels.value.some(
      (m) => m.id === selectedAiModelId.value && m.loaded,
    )
    if (!currentStillValid) {
      const preferred =
        availableAiModels.value.find((m) => m.id === status?.default_model_id && m.loaded) ??
        availableAiModels.value.find((m) => m.loaded)
      selectedAiModelId.value = preferred?.id ?? status?.default_model_id ?? ''
    }
  } catch {
    aiCtReady.value = false
    algoReady.value = false
    lungNoduleReady.value = false
  }
}

async function handleRunAnalysis() {
  if (!imagingVolumeId.value || !canInfer.value || !id.value) return

  await refreshAiCtHealth()
  if (!aiCtReady.value) {
    const message = 'CT 伪影分析服务未就绪，请先启动 ai-ct-service（默认端口 8105）'
    analysisError.value = message
    analysisResult.value = null
    analysisDialogVisible.value = true
    ElMessage.warning(message)
    return
  }

  analyzing.value = true
  analysisError.value = ''
  analysisResult.value = null
  analysisDialogVisible.value = true

  try {
    const response = await medtechApi.analyzeCheckImaging(id.value)
    analysisResult.value = response.analysisResult ?? null
    if (!analysisResult.value) {
      analysisError.value = '分析完成但未返回结果数据'
    } else if (report.value) {
      report.value = {
        ...report.value,
        hasImagingAnalysis: true,
        imagingAnalyzedAt: response.analyzedAt,
        imagingAnalysisResult: analysisResult.value,
      }
    }
  } catch (error) {
    analysisError.value = error instanceof Error ? error.message : 'CT 影像分析失败，请稍后重试'
    ElMessage.error(analysisError.value)
  } finally {
    analyzing.value = false
  }
}

async function handleRunAiSegmentation() {
  if (!imagingVolumeId.value || !canInfer.value || !id.value) return

  await refreshAiCtHealth()
  if (!lungNoduleReady.value) {
    const message = 'AI 肺结节分割服务未就绪，请先启动 lung-nodule-seg-service（默认端口 8222）并放置模型权重'
    segmentationError.value = message
    segmentPanelVisible.value = true
    ElMessage.warning(message)
    return
  }

  aiSegmenting.value = true
  segmentationError.value = ''
  segmentPanelVisible.value = true
  startAiSegmentProgress()

  try {
    void refreshAiSegmentProgress()
    const response = await medtechApi.aiSegmentCheckImaging(id.value, selectedAiModelId.value || undefined)
    segmentationResult.value = response.segmentationResult ?? null
    if (!segmentationResult.value) {
      segmentationError.value = 'AI 分割完成但未返回结果数据'
      return
    }

    if (segmentationResult.value.lesions?.length) {
      viewerPanelRef.value?.applySegmentationLesions(segmentationResult.value.lesions)
    }
    // 默认以像素级红色掩码叠加展示病灶（与参考 CT 系统一致），而非矩形标注框，
    // 避免遮挡病灶本身；病灶列表/3D 预览/点击定位仍使用上面的 lesions 数据。
    if (segmentationResult.value.maskVolumeId) {
      viewerPanelRef.value?.loadSegmentationMask?.(segmentationResult.value.maskVolumeId)
    }

    if (report.value) {
      report.value = {
        ...report.value,
        hasImagingSegmentation: true,
        imagingSegmentedAt: response.segmentedAt,
        imagingSegmentationResult: segmentationResult.value,
        imagingSegmentationMaskVolumeId: segmentationResult.value.maskVolumeId,
      }
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'AI 肺结节分割失败，请稍后重试'
    segmentationError.value = message.includes('timeout')
      ? 'AI 分割仍未在 30 分钟内返回结果，请查看 lung-nodule-seg-service 终端日志确认是否还在推理'
      : message
    ElMessage.error(segmentationError.value)
  } finally {
    aiSegmenting.value = false
    stopAiSegmentProgress()
  }
}

function syncSegmentationFromReport() {
  const saved = report.value?.imagingSegmentationResult
  if (!saved) return
  segmentationResult.value = saved
  if (saved.lesions?.length) {
    viewerPanelRef.value?.applySegmentationLesions(saved.lesions)
  }
  if (saved.maskVolumeId) {
    viewerPanelRef.value?.loadSegmentationMask?.(saved.maskVolumeId)
  }
}

function handleViewSegmentation() {
  if (!segmentationResult.value && report.value?.imagingSegmentationResult) {
    segmentationResult.value = report.value.imagingSegmentationResult
  }
  segmentPanelVisible.value = true
}

function handleSelectLesion(lesion: CtLesionItem) {
  viewerPanelRef.value?.navigateToLesion(lesion)
}

function handleToggleMask() {
  if (segmentationResult.value?.maskVolumeId) {
    viewerPanelRef.value?.loadSegmentationMask?.(segmentationResult.value.maskVolumeId)
  }
}

function toggleSegmentPanel() {
  segmentPanelVisible.value = !segmentPanelVisible.value
}

function syncAnalysisFromReport() {
  const saved = report.value?.imagingAnalysisResult
  if (saved) {
    analysisResult.value = saved
  }
}

function handleViewAnalysis() {
  if (!analysisResult.value && report.value?.imagingAnalysisResult) {
    analysisResult.value = report.value.imagingAnalysisResult
  }
  if (!analysisResult.value) {
    ElMessage.info('暂无已保存的分析结果')
    return
  }
  analysisError.value = ''
  analysisDialogVisible.value = true
}

function toggleReportPanel() {
  reportPanelVisible.value = !reportPanelVisible.value
  if (reportPanelVisible.value) {
    requestAnimationFrame(() => {
      reportPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    })
  }
}

function handleReportSubmitted() {
  router.push('/medtech/check-queue')
}

function goBack() {
  router.push('/medtech/check-queue')
}

onMounted(async () => {
  void refreshAiCtHealth()
  if (!id.value) return
  await loadCheckContext(id.value)
  syncAnalysisFromReport()
  syncSegmentationFromReport()
})

onBeforeUnmount(() => {
  stopAiSegmentProgress()
})
</script>

<template>
  <div class="ct-exam-page ct-imaging-theme">
    <header class="ct-exam-header">
      <div class="ct-exam-header__row">
        <div class="ct-exam-header__patient">
          <ElButton class="ct-exam-back" :icon="ArrowLeft" text @click="goBack">返回</ElButton>
          <div v-if="report" class="ct-exam-patient-info">
            <span class="ct-exam-patient-name">{{ report.patientName || '-' }}</span>
            <span class="ct-exam-meta-item">病历号 {{ report.caseNumber || '-' }}</span>
            <span class="ct-exam-meta-item">检查单 #{{ report.id }}</span>
          </div>
        </div>

        <div v-if="report" class="ct-exam-header__exam">
          <span class="ct-exam-exam-type">{{ examTitle }}</span>
          <span v-if="report.checkTime || report.creationTime" class="ct-exam-exam-time">
            {{ report.checkTime || report.creationTime }}
          </span>
        </div>

        <div class="ct-exam-header__actions">
          <span v-if="hasImaging" class="ct-exam-status ct-exam-status--bound">影像已绑定</span>
          <span v-else-if="started" class="ct-exam-status ct-exam-status--pending">待上传影像</span>
          <span v-if="report?.hasImagingAnalysis" class="ct-exam-status ct-exam-status--analyzed">已质控</span>
          <span v-if="report?.hasImagingSegmentation" class="ct-exam-status ct-exam-status--segmented">已分割</span>

          <ElButton
            v-if="report?.hasImagingAnalysis"
            class="ct-exam-btn-secondary"
            @click="handleViewAnalysis"
          >
            查看质控
          </ElButton>
          <ElButton
            class="ct-exam-btn-secondary"
            :type="segmentPanelVisible ? 'primary' : 'default'"
            @click="toggleSegmentPanel"
          >
            AI 分割面板
          </ElButton>
          <ElButton
            class="ct-exam-btn-secondary"
            :type="reportPanelVisible ? 'primary' : 'default'"
            @click="toggleReportPanel"
          >
            {{ reportPanelVisible ? '收起报告' : '诊断报告' }}
          </ElButton>

          <ElButton
            class="ct-exam-btn-primary"
            type="primary"
            :loading="analyzing"
            :disabled="!canInfer"
            @click="handleRunAnalysis"
          >
            影像质控
          </ElButton>
        </div>
      </div>

      <div v-if="technicalSubline" class="ct-exam-header__sub">
        <span class="ct-exam-header__sub-dot" />
        <span>{{ technicalSubline }}</span>
      </div>
    </header>

    <main v-loading="loading || imagingBinding" class="ct-exam-main">
      <ElEmpty v-if="!id" description="请从医技申请列表选择一条 CT 检查记录">
        <ElButton type="primary" @click="goBack">返回医技申请</ElButton>
      </ElEmpty>

      <template v-else>
        <ElAlert
          v-if="errorMessage"
          type="error"
          :title="errorMessage"
          show-icon
          :closable="false"
          class="ct-exam-alert"
        />
        <ElAlert
          v-if="started && !hasImaging && !errorMessage"
          type="info"
          title="请先上传 DICOM 文件夹或 NRRD/NIfTI，完成阅片后再运行 CT 影像分析"
          show-icon
          :closable="false"
          class="ct-exam-alert"
        />
        <ElAlert
          v-if="started && !lungNoduleReady && !errorMessage"
          type="warning"
          title="AI 肺结节分割服务未就绪：请启动 lung-nodule-seg-service（端口 8222）并放置模型权重"
          show-icon
          :closable="false"
          class="ct-exam-alert"
        />
        <ElAlert
          v-if="started && !aiCtReady && !errorMessage"
          type="warning"
          title="CT 伪影分析服务未就绪：请启动 ai-ct-service（端口 8105），再点击「影像质控」"
          show-icon
          :closable="false"
          class="ct-exam-alert"
        />

        <div v-if="started && !errorMessage" class="ct-exam-body">
          <div class="ct-exam-viewer">
            <CtViewerPanel
              ref="viewerPanelRef"
              fullscreen
              :show-save="false"
              :show-tech-bar="false"
              :initial-volume-id="imagingVolumeId"
              :patient-name="report?.patientName"
              :patient-fields="patientInfoFields"
              @uploaded="handleImagingUploaded"
              @cleared="handleImagingCleared"
              @meta-updated="handleMetaUpdated"
              @toggle-report="toggleReportPanel"
            />
          </div>

          <div
            v-show="segmentPanelVisible"
            class="ct-exam-seg-panel"
          >
            <CtAiSegmentationPanel
              :loading="aiSegmenting"
              :progress-message="aiSegmentStatusText"
              :elapsed-seconds="aiSegmentElapsedSeconds"
              :error-message="segmentationError"
              :result="segmentationResult"
              :available-models="availableAiModels"
              :model-id="selectedAiModelId"
              @run-ai-segment="handleRunAiSegmentation"
              @select-lesion="handleSelectLesion"
              @toggle-mask="handleToggleMask"
              @update:model-id="(value) => (selectedAiModelId = value)"
            />
          </div>

          <div
            v-show="reportPanelVisible"
            ref="reportPanelRef"
            class="ct-exam-report"
          >
            <CtDiagnosisReportPanel
              :check-request-id="id"
              :register-id="report?.registerId"
              :tech-name="report?.techName"
              :can-edit="canEditReport"
              :has-imaging="hasImaging"
              :analysis-result="analysisResult"
              @submitted="handleReportSubmitted"
            />
          </div>
        </div>
      </template>
    </main>

    <CtArtifactAnalysisDialog
      v-model:visible="analysisDialogVisible"
      :loading="analyzing"
      :error-message="analysisError"
      :result="analysisResult"
    />

  </div>
</template>

<style scoped>
.ct-exam-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ct-bg);
  color: var(--ct-text);
}

.ct-exam-header {
  flex-shrink: 0;
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-surface);
}

.ct-exam-header__row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
}

.ct-exam-header__patient {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ct-exam-back {
  flex-shrink: 0;
  --el-button-text-color: var(--ct-text-muted);
  --el-button-hover-text-color: var(--ct-accent);
}

.ct-exam-patient-info {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 14px;
  min-width: 0;
}

.ct-exam-patient-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ct-text);
}

.ct-exam-meta-item {
  font-size: 12px;
  color: var(--ct-text-muted);
}

.ct-exam-header__exam {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ct-exam-exam-type {
  font-size: 13px;
  font-weight: 500;
  color: var(--ct-text);
}

.ct-exam-exam-time {
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text-dim);
}

.ct-exam-header__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.ct-exam-status {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 999px;
}

.ct-exam-status--bound {
  color: var(--ct-success);
  background: rgba(52, 211, 153, 0.12);
}

.ct-exam-status--pending {
  color: var(--ct-warning);
  background: rgba(251, 191, 36, 0.12);
}

.ct-exam-status--analyzed {
  color: var(--el-color-success);
  background: rgba(103, 194, 58, 0.12);
}

.ct-exam-status--segmented {
  color: #50dc78;
  background: rgba(80, 220, 120, 0.12);
}

.ct-exam-btn-secondary {
  --el-button-bg-color: var(--ct-surface-elevated);
  --el-button-border-color: var(--ct-border-strong);
  --el-button-text-color: var(--ct-text-muted);
  --el-button-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-button-hover-border-color: var(--ct-border-strong);
  --el-button-hover-text-color: var(--ct-text);
}

.ct-exam-btn-primary {
  font-weight: 600;
  padding-inline: 18px;
}

.ct-exam-header__sub {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 16px 8px;
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text-dim);
  border-block-start: 1px solid var(--ct-border);
  background: var(--ct-bg-soft);
}

.ct-exam-header__sub-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ct-accent);
}

.ct-exam-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ct-exam-alert {
  flex-shrink: 0;
  margin: 10px 14px 0;
}

.ct-exam-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.ct-exam-viewer {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.ct-exam-seg-panel {
  flex-shrink: 0;
  width: min(320px, 32vw);
  min-height: 0;
  overflow: hidden;
  border-inline-start: 1px solid var(--ct-border);
}

.ct-exam-report {
  flex-shrink: 0;
  width: min(380px, 38vw);
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 960px) {
  .ct-exam-header__row {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .ct-exam-header__actions {
    flex-wrap: wrap;
  }

  .ct-exam-body {
    flex-direction: column;
  }

  .ct-exam-seg-panel {
    width: 100%;
    max-height: 50vh;
    border-block-start: 1px solid var(--ct-border);
    border-inline-start: none;
  }

  .ct-exam-report {
    width: 100%;
    max-height: 42vh;
    border-block-start: 1px solid var(--ct-border);
    border-inline-start: none;
  }
}
</style>

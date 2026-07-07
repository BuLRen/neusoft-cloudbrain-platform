<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import CtViewerPanel from '@/modules/medtech/ct-viewer/components/CtViewerPanel.vue'
import CtArtifactAnalysisDialog from '@/modules/medtech/ct-viewer/components/CtArtifactAnalysisDialog.vue'
import CtSegmentationDialog from '@/modules/medtech/ct-viewer/components/CtSegmentationDialog.vue'
import CtDiagnosisReportPanel from '@/modules/medtech/components/CtDiagnosisReportPanel.vue'
import type { CtAnalyzeResult, CtLesionItem, CtSegmentResult, CtVolumeMeta } from '@/shared/api/modules/ctViewer'
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
const analysisDialogVisible = ref(false)
const segmentationDialogVisible = ref(false)
const analysisResult = ref<CtAnalyzeResult | null>(null)
const segmentationResult = ref<CtSegmentResult | null>(null)
const analysisError = ref('')
const segmentationError = ref('')
const aiCtReady = ref(false)
const algoReady = ref(false)
const reportPanelVisible = ref(true)

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

async function handleImagingUploaded(payload: { volumeId: string; sourceName: string }) {
  if (!id.value) return
  await bindImaging(id.value, payload, () => viewerPanelRef.value?.resetVolumeState())
}

async function handleImagingCleared() {
  if (!id.value) return
  await clearImaging(id.value)
  volumeMeta.value = null
  analysisResult.value = null
  segmentationResult.value = null
  viewerPanelRef.value?.clearSegmentationOverlay()
}

function handleMetaUpdated(meta: CtVolumeMeta | null) {
  volumeMeta.value = meta
}

async function refreshAiCtHealth() {
  try {
    const health = await checkCtViewerHealth()
    aiCtReady.value = Boolean(health.aiCtReady)
    algoReady.value = Boolean(health.algoReady)
  } catch {
    aiCtReady.value = false
    algoReady.value = false
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

async function handleRunSegmentation() {
  if (!imagingVolumeId.value || !canInfer.value || !id.value) return

  await refreshAiCtHealth()
  if (!algoReady.value) {
    const message = 'CT 算法服务未就绪，请先启动 ct-viewer-algo（默认端口 8106）'
    segmentationError.value = message
    segmentationResult.value = null
    segmentationDialogVisible.value = true
    ElMessage.warning(message)
    return
  }

  segmenting.value = true
  segmentationError.value = ''
  segmentationResult.value = null
  segmentationDialogVisible.value = true

  try {
    const response = await medtechApi.segmentCheckImaging(id.value)
    segmentationResult.value = response.segmentationResult ?? null
    if (!segmentationResult.value) {
      segmentationError.value = '分割完成但未返回结果数据'
      return
    }

    if (segmentationResult.value.lesions?.length) {
      viewerPanelRef.value?.applySegmentationLesions(segmentationResult.value.lesions)
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
    segmentationError.value = error instanceof Error ? error.message : 'CT 病灶分割失败，请稍后重试'
    ElMessage.error(segmentationError.value)
  } finally {
    segmenting.value = false
  }
}

function syncSegmentationFromReport() {
  const saved = report.value?.imagingSegmentationResult
  if (!saved) return
  segmentationResult.value = saved
  if (saved.lesions?.length) {
    viewerPanelRef.value?.applySegmentationLesions(saved.lesions)
  }
}

function handleViewSegmentation() {
  if (!segmentationResult.value && report.value?.imagingSegmentationResult) {
    segmentationResult.value = report.value.imagingSegmentationResult
  }
  if (!segmentationResult.value) {
    ElMessage.info('暂无已保存的分割结果')
    return
  }
  segmentationError.value = ''
  segmentationDialogVisible.value = true
}

function handleSelectLesion(lesion: CtLesionItem) {
  viewerPanelRef.value?.navigateToLesion(lesion)
  segmentationDialogVisible.value = false
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
            v-if="report?.hasImagingSegmentation"
            class="ct-exam-btn-secondary"
            @click="handleViewSegmentation"
          >
            查看分割
          </ElButton>
          <ElButton
            class="ct-exam-btn-secondary"
            :type="reportPanelVisible ? 'primary' : 'default'"
            @click="toggleReportPanel"
          >
            {{ reportPanelVisible ? '收起报告' : '诊断报告' }}
          </ElButton>
          <ElButton
            class="ct-exam-btn-secondary"
            :loading="segmenting"
            :disabled="!canInfer"
            @click="handleRunSegmentation"
          >
            AI 病灶分割
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
          v-if="started && !algoReady && !errorMessage"
          type="warning"
          title="CT 算法服务未就绪：请启动 ct-viewer-algo（端口 8106），再点击「AI 病灶分割」"
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
              @uploaded="handleImagingUploaded"
              @cleared="handleImagingCleared"
              @meta-updated="handleMetaUpdated"
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

    <CtSegmentationDialog
      v-model:visible="segmentationDialogVisible"
      :loading="segmenting"
      :error-message="segmentationError"
      :result="segmentationResult"
      @select-lesion="handleSelectLesion"
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

  .ct-exam-report {
    width: 100%;
    max-height: 42vh;
    border-block-start: 1px solid var(--ct-border);
    border-inline-start: none;
  }
}
</style>

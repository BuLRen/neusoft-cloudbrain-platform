<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import CtViewerPanel from '@/modules/medtech/ct-viewer/components/CtViewerPanel.vue'
import CtArtifactAnalysisDialog from '@/modules/medtech/ct-viewer/components/CtArtifactAnalysisDialog.vue'
import type { CtAnalyzeResult, CtVolumeMeta } from '@/shared/api/modules/ctViewer'
import { analyzeCtVolume, checkCtViewerHealth } from '@/shared/api/modules/ctViewer'
import { useCtCheckContext } from '@/modules/medtech/composables/useCtCheckContext'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

const route = useRoute()
const router = useRouter()
const viewerPanelRef = ref<InstanceType<typeof CtViewerPanel>>()
const volumeMeta = ref<CtVolumeMeta | null>(null)
const analyzing = ref(false)
const analysisDialogVisible = ref(false)
const analysisResult = ref<CtAnalyzeResult | null>(null)
const analysisError = ref('')
const aiCtReady = ref(false)

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
}

function handleMetaUpdated(meta: CtVolumeMeta | null) {
  volumeMeta.value = meta
}

async function refreshAiCtHealth() {
  try {
    const health = await checkCtViewerHealth()
    aiCtReady.value = Boolean(health.aiCtReady)
  } catch {
    aiCtReady.value = false
  }
}

async function handleRunAnalysis() {
  if (!imagingVolumeId.value || !canInfer.value) return

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
    analysisResult.value = await analyzeCtVolume(imagingVolumeId.value)
  } catch (error) {
    analysisError.value = error instanceof Error ? error.message : 'CT 影像分析失败，请稍后重试'
    ElMessage.error(analysisError.value)
  } finally {
    analyzing.value = false
  }
}

function goBack() {
  router.push('/medtech/check-queue')
}

onMounted(() => {
  void refreshAiCtHealth()
  if (!id.value) return
  void loadCheckContext(id.value)
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

          <ElButton class="ct-exam-btn-secondary" @click="goBack">报告</ElButton>
          <ElButton
            class="ct-exam-btn-primary"
            type="primary"
            :loading="analyzing"
            :disabled="!canInfer"
            @click="handleRunAnalysis"
          >
            影像分析
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
          v-if="started && !aiCtReady && !errorMessage"
          type="warning"
          title="CT 伪影分析服务未就绪：请启动 ai-ct-service（端口 8105），再点击「影像分析」"
          show-icon
          :closable="false"
          class="ct-exam-alert"
        />

        <div v-if="started && !errorMessage" class="ct-exam-viewer">
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

.ct-exam-viewer {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

@media (max-width: 960px) {
  .ct-exam-header__row {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .ct-exam-header__actions {
    flex-wrap: wrap;
  }
}
</style>

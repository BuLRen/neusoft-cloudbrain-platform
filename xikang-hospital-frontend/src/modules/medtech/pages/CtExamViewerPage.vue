<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElMessage, ElTag } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import CtViewerPanel from '@/modules/medtech/ct-viewer/components/CtViewerPanel.vue'
import {
  saveCtDraft,
  useCtCheckContext,
} from '@/modules/medtech/composables/useCtCheckContext'

const route = useRoute()
const router = useRouter()
const viewerPanelRef = ref<InstanceType<typeof CtViewerPanel>>()

const id = computed(() => Number(route.query.id || 0))

const {
  loading,
  imagingBinding,
  simulating,
  errorMessage,
  simulateError,
  report,
  started,
  imagingVolumeId,
  imagingSourceName,
  hasImaging,
  canInfer,
  loadCheckContext,
  bindImaging,
  clearImaging,
  runCtInfer,
} = useCtCheckContext()

const toolbarTitle = computed(() => report.value?.techName || 'CT 影像检查')

async function handleImagingUploaded(payload: { volumeId: string; sourceName: string }) {
  if (!id.value) return
  await bindImaging(id.value, payload, () => viewerPanelRef.value?.resetVolumeState())
}

async function handleImagingCleared() {
  if (!id.value) return
  await clearImaging(id.value)
}

async function handleRunAnalysis() {
  if (!id.value) return
  const result = await runCtInfer(id.value)
  if (!result) return

  if (result.simulatedValues) {
    saveCtDraft(id.value, { simulatedValues: result.simulatedValues, savedAt: Date.now() })
  }

  ElMessage.success('CT 影像分析完成，请继续录入结果')
  router.push({ path: '/medtech/check-start', query: { id: String(id.value), phase: 'submit' } })
}

function goBack() {
  router.push('/medtech/check-queue')
}

onMounted(() => {
  if (!id.value) return
  void loadCheckContext(id.value)
})
</script>

<template>
  <div class="ct-exam-page">
    <header class="ct-exam-toolbar">
      <div class="ct-exam-toolbar__left">
        <ElButton :icon="ArrowLeft" text @click="goBack">返回医技申请</ElButton>
        <div class="ct-exam-toolbar__info">
          <h1 class="ct-exam-toolbar__title">{{ toolbarTitle }}</h1>
          <p v-if="report" class="ct-exam-toolbar__meta">
            <span>{{ report.patientName || '-' }}</span>
            <span class="ct-exam-toolbar__sep">·</span>
            <span>病历号 {{ report.caseNumber || '-' }}</span>
            <span class="ct-exam-toolbar__sep">·</span>
            <span>{{ report.statusText || report.checkState || '-' }}</span>
          </p>
        </div>
      </div>
      <div class="ct-exam-toolbar__right">
        <ElTag v-if="hasImaging" type="success" size="small">影像已绑定</ElTag>
        <ElTag v-else-if="started" type="warning" size="small">待上传影像</ElTag>
        <ElButton
          type="primary"
          :loading="simulating"
          :disabled="!canInfer"
          @click="handleRunAnalysis"
        >
          运行 CT 影像分析
        </ElButton>
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
          v-if="simulateError"
          type="warning"
          :title="simulateError"
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
        <p v-if="hasImaging && imagingSourceName" class="ct-exam-source">影像来源：{{ imagingSourceName }}</p>

        <div v-if="started && !errorMessage" class="ct-exam-viewer">
          <CtViewerPanel
            ref="viewerPanelRef"
            fullscreen
            :show-save="false"
            :initial-volume-id="imagingVolumeId"
            @uploaded="handleImagingUploaded"
            @cleared="handleImagingCleared"
          />
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.ct-exam-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.ct-exam-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-shrink: 0;
  padding: var(--space-3) var(--space-4);
  border-block-end: 1px solid var(--color-border);
  background: var(--color-surface);
}

.ct-exam-toolbar__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.ct-exam-toolbar__info {
  min-width: 0;
}

.ct-exam-toolbar__title {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ct-exam-toolbar__meta {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.ct-exam-toolbar__sep {
  margin-inline: var(--space-2);
}

.ct-exam-toolbar__right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}

.ct-exam-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-3) var(--space-4);
  overflow: hidden;
}

.ct-exam-alert {
  flex-shrink: 0;
  margin-block-end: var(--space-3);
}

.ct-exam-source {
  flex-shrink: 0;
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.ct-exam-viewer {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>

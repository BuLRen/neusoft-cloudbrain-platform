<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElTabPane, ElTabs } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import CtViewerPanel from '@/modules/medtech/ct-viewer/components/CtViewerPanel.vue'
import CtFilmSheetView from '@/modules/medtech/ct-viewer/components/CtFilmSheetView.vue'
import CtDiagnosisReportPanel from '@/modules/medtech/components/CtDiagnosisReportPanel.vue'
import CtAiSegmentationPanel from '@/modules/medtech/ct-viewer/components/CtAiSegmentationPanel.vue'
import CtFilmPrintSheet from '@/shared/components/CtFilmPrintSheet.vue'
import CtDiagnosisReportPrintSheet from '@/shared/components/CtDiagnosisReportPrintSheet.vue'
import { useEncounterStore } from '@/app/stores/encounter'
import { usePhysicianCtCheckContext } from '@/modules/physician/composables/usePhysicianCtCheckContext'
import { useCtReportExport } from '@/shared/composables/useCtReportExport'
import {
  buildCtDiagnosisReportPdfContext,
  buildCtFilmPdfContext,
} from '@/shared/types/ctReportPdf'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

const route = useRoute()
const router = useRouter()
const encounterStore = useEncounterStore()

const activeImagingTab = ref<'film' | 'viewer'>('film')
const segmentPanelVisible = ref(false)
const filmPrintRef = ref<InstanceType<typeof CtFilmPrintSheet> | null>(null)
const diagnosisPrintRef = ref<InstanceType<typeof CtDiagnosisReportPrintSheet> | null>(null)

const {
  filmExportContext,
  diagnosisExportContext,
  exportingFilm,
  exportingReport,
  exportFilmPdf,
  exportDiagnosisPdf,
} = useCtReportExport()

const registerId = computed(() => Number(route.query.registerId || encounterStore.registerId || 0))
const checkRequestId = computed(() => Number(route.query.checkRequestId || 0))

const {
  loading,
  errorMessage,
  checkResult,
  resultFormSchema,
  volumeMeta,
  canViewImaging,
  loadCheckContext,
  fetchNrrd,
} = usePhysicianCtCheckContext()

const patientInfo = computed(() => ({
  hospitalName: '熙康云医院',
  patientName: encounterStore.patientSummary?.realName,
  gender: encounterStore.patientSummary?.gender,
  age: encounterStore.patientSummary?.age,
  caseNumber: encounterStore.patientSummary?.caseNumber,
}))

const segmentationResult = computed(() => checkResult.value?.imagingSegmentationResult ?? null)

const technicalSubline = computed(() => {
  const meta = volumeMeta.value
  const parts: string[] = []
  if (checkResult.value?.imagingSourceName) {
    parts.push(`来源：${checkResult.value.imagingSourceName}`)
  }
  if (meta?.spacing_xyz?.length) {
    parts.push(`体素间距 ${meta.spacing_xyz.map((v) => v.toFixed(2)).join(' × ')} mm`)
  }
  if (meta?.size_xyz?.length) {
    parts.push(`矩阵 ${meta.size_xyz.join(' × ')}`)
  }
  if (meta?.file_count) parts.push(`${meta.file_count} 张`)
  return parts.join(' · ')
})

function goBack() {
  router.push({ path: '/physician/results' })
}

async function handleExportFilm() {
  if (!checkResult.value) return
  await exportFilmPdf(
    buildCtFilmPdfContext(checkResult.value, encounterStore.patientSummary),
    filmPrintRef,
    fetchNrrd,
  )
}

async function handleExportReport() {
  if (!checkResult.value || !resultFormSchema.value) return
  await exportDiagnosisPdf(
    buildCtDiagnosisReportPdfContext(
      checkResult.value,
      resultFormSchema.value,
      encounterStore.patientSummary,
    ),
    diagnosisPrintRef,
  )
}

onMounted(() => {
  if (!registerId.value || !checkRequestId.value) {
    errorMessage.value = '缺少挂号或检查单参数'
    return
  }
  void loadCheckContext(registerId.value, checkRequestId.value)
})
</script>

<template>
  <div class="physician-ct-page ct-imaging-theme">
    <header class="physician-ct-page__header">
      <div class="physician-ct-page__row">
        <div class="physician-ct-page__patient">
          <ElButton class="physician-ct-page__back" :icon="ArrowLeft" text @click="goBack">返回结果页</ElButton>
          <div v-if="checkResult" class="physician-ct-page__patient-info">
            <span class="physician-ct-page__patient-name">{{ patientInfo.patientName || '-' }}</span>
            <span class="physician-ct-page__meta-item">病历号 {{ patientInfo.caseNumber || '-' }}</span>
            <span class="physician-ct-page__meta-item">检查单 #{{ checkResult.id }}</span>
          </div>
        </div>

        <div v-if="checkResult" class="physician-ct-page__exam">
          <span class="physician-ct-page__exam-type">{{ checkResult.techName }}</span>
          <span v-if="checkResult.checkTime" class="physician-ct-page__exam-time">{{ checkResult.checkTime }}</span>
        </div>

        <div class="physician-ct-page__actions">
          <div class="physician-ct-page__status">
            <span v-if="checkResult?.hasImaging" class="physician-ct-page__badge physician-ct-page__badge--bound">影像已采集</span>
            <span v-if="checkResult?.hasImagingAnalysis" class="physician-ct-page__badge physician-ct-page__badge--analyzed">已质控分析</span>
            <span v-if="checkResult?.checkState === '已完成'" class="physician-ct-page__badge physician-ct-page__badge--done">报告已提交</span>
          </div>
          <div class="physician-ct-page__export">
            <ElButton
              v-if="segmentationResult"
              size="small"
              :type="segmentPanelVisible ? 'primary' : 'default'"
              @click="segmentPanelVisible = !segmentPanelVisible"
            >
              AI 分割结果
            </ElButton>
            <ElButton
              size="small"
              :loading="exportingFilm"
              :disabled="!canViewImaging"
              @click="handleExportFilm"
            >
              导出胶片 PDF
            </ElButton>
            <ElButton
              size="small"
              :loading="exportingReport"
              :disabled="!resultFormSchema"
              @click="handleExportReport"
            >
              导出报告 PDF
            </ElButton>
          </div>
        </div>
      </div>

      <div v-if="technicalSubline" class="physician-ct-page__sub">
        <span class="physician-ct-page__sub-dot" />
        {{ technicalSubline }}
      </div>
    </header>

    <main v-loading="loading" class="physician-ct-page__main">
      <ElAlert
        v-if="errorMessage"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
        class="physician-ct-page__alert"
      />

      <ElEmpty v-else-if="!checkResult" description="未加载到 CT 检查信息" />

      <div v-else class="physician-ct-page__body">
        <section class="physician-ct-page__imaging">
          <ElTabs v-model="activeImagingTab" class="physician-ct-page__tabs">
            <ElTabPane label="胶片预览" name="film">
              <CtFilmSheetView
                v-if="canViewImaging"
                :nrrd-fetcher="fetchNrrd"
                :volume-meta="volumeMeta"
              />
              <ElEmpty v-else description="该检查尚未绑定 CT 影像" />
            </ElTabPane>
            <ElTabPane label="完整阅片" name="viewer">
              <div v-if="canViewImaging && checkResult.imagingVolumeId" class="physician-ct-page__viewer">
                <CtViewerPanel
                  :key="checkResult.id"
                  embedded
                  fullscreen
                  read-only
                  :allow-upload="false"
                  :show-save="false"
                  :show-tech-bar="false"
                  :initial-volume-id="checkResult.imagingVolumeId"
                  :nrrd-fetcher="(_volumeId: string) => fetchNrrd()"
                />
              </div>
              <ElEmpty v-else description="该检查尚未绑定 CT 影像" />
            </ElTabPane>
          </ElTabs>
        </section>

        <div
          v-if="segmentPanelVisible"
          class="physician-ct-page__seg-panel"
        >
          <CtAiSegmentationPanel
            :result="segmentationResult"
            :readonly="true"
          />
        </div>

        <aside class="physician-ct-page__report">
          <CtDiagnosisReportPanel
            v-if="checkResult.checkState === '已完成' && resultFormSchema"
            :key="resultFormSchema.checkRequestId"
            embedded
            :check-request-id="checkResult.id"
            :can-edit="false"
            :readonly-schema="resultFormSchema"
            :analysis-result="checkResult.imagingAnalysisResult"
          />
          <ElEmpty v-else description="医技尚未提交诊断报告" />
        </aside>
      </div>
    </main>

    <div class="ct-report-print-host" aria-hidden="true">
      <CtFilmPrintSheet
        ref="filmPrintRef"
        :context="filmExportContext"
        :volume-meta="volumeMeta"
      />
      <CtDiagnosisReportPrintSheet ref="diagnosisPrintRef" :context="diagnosisExportContext" />
    </div>
  </div>
</template>

<style scoped>
.physician-ct-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--ct-bg, #0d1117);
  color: var(--ct-text, #e8edf5);
}

.physician-ct-page__header {
  flex-shrink: 0;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ct-border, rgba(255, 255, 255, 0.12));
  background: var(--ct-surface, #151b24);
}

.physician-ct-page__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.physician-ct-page__patient {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.physician-ct-page__back {
  color: var(--ct-text-muted, #9aa7b8);
}

.physician-ct-page__patient-info {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.physician-ct-page__patient-name {
  font-size: 16px;
  font-weight: 600;
}

.physician-ct-page__meta-item,
.physician-ct-page__exam-time {
  font-size: 12px;
  color: var(--ct-text-dim, #7f8b9a);
}

.physician-ct-page__exam {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.physician-ct-page__exam-type {
  font-size: 14px;
  font-weight: 600;
}

.physician-ct-page__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.physician-ct-page__status {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.physician-ct-page__export {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ct-report-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
}

.physician-ct-page__badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
}

.physician-ct-page__badge--bound {
  background: rgba(64, 158, 255, 0.16);
  color: #8ec5ff;
}

.physician-ct-page__badge--analyzed {
  background: rgba(103, 194, 58, 0.16);
  color: #a8e08f;
}

.physician-ct-page__badge--done {
  background: rgba(230, 162, 60, 0.16);
  color: #f3c27a;
}

.physician-ct-page__sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--ct-text-dim, #7f8b9a);
}

.physician-ct-page__sub-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #67c23a;
}

.physician-ct-page__main {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
}

.physician-ct-page__alert {
  margin-bottom: 12px;
  flex-shrink: 0;
}

.physician-ct-page__body {
  display: flex;
  gap: 10px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.physician-ct-page__imaging {
  flex: 1;
  min-width: 0;
}

.physician-ct-page__seg-panel {
  flex-shrink: 0;
  width: 300px;
  min-height: 0;
  border: 1px solid var(--ct-border, rgba(255, 255, 255, 0.12));
  border-radius: 10px;
  overflow: hidden;
}

.physician-ct-page__imaging,
.physician-ct-page__report {
  min-height: 0;
  height: 100%;
  border: 1px solid var(--ct-border, rgba(255, 255, 255, 0.12));
  border-radius: 10px;
  background: var(--ct-surface, #151b24);
  overflow: hidden;
  flex-shrink: 0;
}

.physician-ct-page__report {
  width: 340px;
}

.physician-ct-page__tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.physician-ct-page__tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
  margin: 0;
  padding: 8px 12px 0;
}

.physician-ct-page__tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.physician-ct-page__tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow: auto;
}

.physician-ct-page__viewer {
  height: 100%;
  min-height: 0;
}

.physician-ct-page__report {
  display: flex;
  flex-direction: column;
}

@media (max-width: 1100px) {
  .physician-ct-page__body {
    flex-direction: column;
    overflow: auto;
  }

  .physician-ct-page__imaging,
  .physician-ct-page__seg-panel,
  .physician-ct-page__report {
    width: 100% !important;
    flex-shrink: 0;
  }

  .physician-ct-page__imaging {
    min-height: 60vh;
  }

  .physician-ct-page__seg-panel {
    min-height: 280px;
  }

  .physician-ct-page__report {
    min-height: 320px;
  }
}
</style>

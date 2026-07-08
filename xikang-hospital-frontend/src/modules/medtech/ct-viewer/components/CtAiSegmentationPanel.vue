<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { ElAlert, ElButton, ElTag, ElTooltip } from 'element-plus'
import type { CtAiModelOption, CtLesionItem, CtRiskLevel, CtSegmentResult } from '@/shared/api/modules/ctViewer'
import { fetchCtVolumeNrrd } from '@/shared/api/modules/ctViewer'
import { parseNrrdArrayBuffer } from '@/modules/medtech/ct-viewer/lib/nrrdToVtkImageData'
import { cropVolumeAroundWorldPoint } from '@/modules/medtech/ct-viewer/lib/lesionVolumeUtils'
import VtkVolumeViewer from '@/modules/medtech/ct-viewer/components/VtkVolumeViewer.vue'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

/** 病灶 3D 预览高亮色，取自主题青绿色 --ct-accent (#2a9d8f) 的 0~1 归一化 RGB */
const LESION_PREVIEW_COLOR: [number, number, number] = [0.165, 0.616, 0.561]

const props = defineProps<{
  loading?: boolean
  progressMessage?: string
  elapsedSeconds?: number
  errorMessage?: string
  result?: CtSegmentResult | null
  readonly?: boolean
  /** 可选的 AI 分割模型列表（来自 lung-nodule-seg-service /health） */
  availableModels?: CtAiModelOption[]
  /** 当前选中的模型 id（v-model:modelId） */
  modelId?: string
}>()

const emit = defineEmits<{
  'run-ai-segment': []
  'select-lesion': [lesion: CtLesionItem]
  'toggle-mask': []
  'update:modelId': [value: string]
}>()

const hasModelChoices = computed(() => (props.availableModels?.length ?? 0) > 0)

const selectedModelLabel = computed(() => {
  const current = props.availableModels?.find((item) => item.id === props.modelId)
  return current?.label || current?.id || ''
})

function handleModelChange(value: string) {
  emit('update:modelId', value)
}

const modelMenuOpen = ref(false)
const modelPickerRef = ref<HTMLElement | null>(null)

function toggleModelMenu() {
  if (props.loading) return
  modelMenuOpen.value = !modelMenuOpen.value
}

function selectModel(item: CtAiModelOption) {
  if (!item.loaded || props.loading) return
  handleModelChange(item.id)
  modelMenuOpen.value = false
}

function handleDocumentClick(event: MouseEvent) {
  if (!modelMenuOpen.value) return
  const root = modelPickerRef.value
  if (root && !root.contains(event.target as Node)) {
    modelMenuOpen.value = false
  }
}

watch(
  () => props.loading,
  (loading) => {
    if (loading) modelMenuOpen.value = false
  },
)

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})

const lesions = computed(() => props.result?.lesions ?? [])
const summary = computed(() => props.result?.summary)

const overallRisk = computed<CtRiskLevel | undefined>(
  () =>
    props.result?.overallRiskLevel ||
    (props.result?.summary?.overallRiskLevel as CtRiskLevel | undefined),
)

const modelVersion = computed(
  () => props.result?.modelVersion || props.result?.summary?.modelVersion,
)

const processingTimeMs = computed(
  () => props.result?.processingTimeMs ?? props.result?.summary?.processingTimeMs,
)

const avgDensityHU = computed(() => {
  const withDensity = lesions.value.filter((l) => l.meanDensityHU != null)
  if (!withDensity.length) return null
  const sum = withDensity.reduce((acc, l) => acc + (l.meanDensityHU ?? 0), 0)
  return Math.round(sum / withDensity.length)
})

function lesionLocationLabel(lesion: CtLesionItem): string {
  return lesion.label || `轴位第 ${lesion.sliceIndex + 1} 层`
}

/* ---- 3D 病灶预览 ---- */
const selectedLesionId = ref<number | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const previewVolume = shallowRef<ReturnType<typeof cropVolumeAroundWorldPoint> | null>(null)
let maskVolumeCache: { id: string; volume: ReturnType<typeof parseNrrdArrayBuffer> } | null = null

const selectedLesion = computed(
  () => lesions.value.find((l) => l.id === selectedLesionId.value) ?? lesions.value[0] ?? null,
)

watch(
  lesions,
  (list) => {
    if (!list.length) {
      selectedLesionId.value = null
      return
    }
    if (!list.some((l) => l.id === selectedLesionId.value)) {
      selectedLesionId.value = list[0].id
    }
  },
  { immediate: true },
)

async function loadLesionPreview() {
  const maskVolumeId = props.result?.maskVolumeId
  const lesion = selectedLesion.value
  if (!maskVolumeId || !lesion?.centroidXyz || lesion.centroidXyz.length < 3) {
    previewVolume.value = null
    previewError.value = ''
    return
  }

  previewLoading.value = true
  previewError.value = ''
  try {
    if (!maskVolumeCache || maskVolumeCache.id !== maskVolumeId) {
      const buffer = await fetchCtVolumeNrrd(maskVolumeId)
      maskVolumeCache = { id: maskVolumeId, volume: parseNrrdArrayBuffer(buffer) }
    }
    const halfExtentMm = Math.max((lesion.diameterMm ?? 10) * 0.9, 12)
    previewVolume.value = cropVolumeAroundWorldPoint(
      maskVolumeCache.volume,
      lesion.centroidXyz as [number, number, number],
      halfExtentMm,
    )
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '病灶 3D 预览加载失败'
    previewVolume.value = null
  } finally {
    previewLoading.value = false
  }
}

watch(
  () => props.result?.maskVolumeId ?? null,
  (maskVolumeId) => {
    if (!maskVolumeId) {
      maskVolumeCache = null
      previewVolume.value = null
      previewError.value = ''
    }
  },
)

watch(
  () => [props.result?.maskVolumeId, selectedLesion.value?.id],
  () => void loadLesionPreview(),
  { immediate: true },
)

function handleSelectLesion(lesion: CtLesionItem) {
  selectedLesionId.value = lesion.id
  emit('select-lesion', lesion)
}

function riskClass(level?: CtRiskLevel | string) {
  if (level === '高风险') return 'risk--high'
  if (level === '中风险') return 'risk--medium'
  return 'risk--low'
}

function riskTagType(level?: CtRiskLevel | string) {
  if (level === '高风险') return 'danger'
  if (level === '中风险') return 'warning'
  return 'success'
}

function generateAiHint(): string {
  if (!props.result || lesions.value.length === 0) {
    return '未检出疑似病灶。如已上传 CT 且服务就绪，可点击「AI 肺结节分割」重新运行。'
  }
  const count = lesions.value.length
  const maxD = summary.value?.maxDiameterMm ?? 0
  const risk = overallRisk.value ?? '低风险'
  const highRisk = lesions.value.filter((l) => l.riskLevel === '高风险')
  const midRisk = lesions.value.filter((l) => l.riskLevel === '中风险')

  let hint = `AI 模型共检出 ${count} 处疑似肺部病灶，最大径约 ${maxD} mm，整体风险评估为${risk}。`

  if (highRisk.length > 0) {
    hint += ` 其中 ${highRisk.length} 处最大径 ≥ 15mm，建议优先关注，结合临床影像综合评估。`
  } else if (midRisk.length > 0) {
    hint += ` 其中 ${midRisk.length} 处最大径 6–15mm，建议随访复查。`
  } else {
    hint += ' 所有病灶最大径均 < 6mm，建议定期随访。'
  }

  hint += ' ⚠️ 以上为 AI 辅助分析，仅供参考，非临床诊断依据。'
  return hint
}

const aiHint = computed(() => generateAiHint())

function formatProcessingTime(ms?: number): string {
  if (ms == null) return '--'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

const selectedModelIsHeavy = computed(() => {
  const current = props.availableModels?.find((m) => m.id === props.modelId)
  // monai/nnunet 是整卷 3D 推理，CPU 上耗时明显；segnet 逐切片 2D 推理通常很快。
  return current ? current.backend !== 'segnet' : true
})

function formatElapsedSeconds(seconds?: number): string {
  if (seconds == null || seconds <= 0) return '0s'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (mins <= 0) return `${secs}s`
  return `${mins}m ${secs.toString().padStart(2, '0')}s`
}
</script>

<template>
  <aside class="ai-seg-panel ct-imaging-theme">
    <!-- ========== 顶栏 ========== -->
    <header class="ai-seg-panel__header">
      <h2 class="ai-seg-panel__title">AI CT 影像分析</h2>

      <div v-if="!readonly" class="ai-seg-panel__controls">
        <div v-if="hasModelChoices" class="ai-seg-panel__field">
          <label class="ai-seg-panel__field-label">分割模型</label>
          <div
            ref="modelPickerRef"
            class="ai-seg-panel__model-picker"
            :class="{ 'is-open': modelMenuOpen }"
          >
            <button
              type="button"
              class="ai-seg-panel__model-trigger"
              :disabled="loading"
              :title="selectedModelLabel"
              :aria-expanded="modelMenuOpen"
              @click.stop="toggleModelMenu"
            >
              <span class="ai-seg-panel__model-trigger-text">
                {{ selectedModelLabel || '选择模型' }}
              </span>
              <svg
                class="ai-seg-panel__model-trigger-icon"
                viewBox="0 0 16 16"
                aria-hidden="true"
              >
                <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>
            </button>

            <ul v-show="modelMenuOpen" class="ai-seg-panel__model-menu" role="listbox">
              <li
                v-for="item in availableModels"
                :key="item.id"
                role="option"
                class="ai-seg-panel__model-menu-item"
                :class="{
                  'is-selected': item.id === modelId,
                  'is-disabled': !item.loaded,
                }"
                :aria-selected="item.id === modelId"
                @click.stop="selectModel(item)"
              >
                <span class="ai-seg-panel__model-menu-label">{{ item.label || item.id }}</span>
                <span v-if="!item.loaded" class="ai-seg-panel__model-menu-hint">未加载</span>
                <span v-else-if="item.id === modelId" class="ai-seg-panel__model-menu-check">✓</span>
              </li>
            </ul>
          </div>
        </div>

        <div class="ai-seg-panel__btn-row">
          <ElButton
            size="small"
            type="primary"
            :loading="loading"
            class="ai-seg-panel__run-btn"
            @click="emit('run-ai-segment')"
          >
            {{ loading ? '分析中…' : 'AI 肺结节分割' }}
          </ElButton>
          <ElTooltip v-if="result" content="切换掩码叠加显示" placement="bottom">
            <ElButton size="small" class="ai-seg-panel__mask-btn" @click="emit('toggle-mask')">
              掩码
            </ElButton>
          </ElTooltip>
        </div>
      </div>
    </header>

    <!-- ========== 加载中 ========== -->
    <div v-if="loading" class="ai-seg-panel__loading">
      <div class="ai-seg-panel__loading-spinner" />
      <p class="ai-seg-panel__loading-text">
        {{ progressMessage || 'AI 模型推理中，请稍候…' }}
      </p>
      <p class="ai-seg-panel__loading-hint">
        已等待 {{ formatElapsedSeconds(elapsedSeconds) }}<template v-if="selectedModelIsHeavy">，3D 模型 CPU 推理可能需要 10–30 分钟</template>
      </p>
      <div class="ai-seg-panel__progress-track">
        <span class="ai-seg-panel__progress-bar" />
      </div>
      <p v-if="selectedModelIsHeavy && (elapsedSeconds ?? 0) >= 600" class="ai-seg-panel__loading-warn">
        已超过 10 分钟，请保持服务终端运行；若最终超时，可查看 lung-nodule-seg-service 日志确认是否仍在推理。
      </p>
    </div>

    <!-- ========== 错误 ========== -->
    <ElAlert
      v-else-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
      class="ai-seg-panel__alert"
    />

    <!-- ========== 有结果 ========== -->
    <template v-else-if="result">
      <!-- 汇总卡片：病灶数量 / 最大径 / 体积 / 平均密度 -->
      <section class="ai-seg-panel__summary-grid">
        <div class="ai-seg-panel__stat-card">
          <span class="ai-seg-panel__stat-label">病灶数量</span>
          <strong class="ai-seg-panel__stat-value">{{ summary?.lesionCount ?? 0 }}</strong>
        </div>
        <div class="ai-seg-panel__stat-card">
          <span class="ai-seg-panel__stat-label">最大径</span>
          <strong class="ai-seg-panel__stat-value">{{ summary?.maxDiameterMm ?? '--' }}</strong>
          <span class="ai-seg-panel__stat-unit">mm</span>
        </div>
        <div class="ai-seg-panel__stat-card">
          <span class="ai-seg-panel__stat-label">体积</span>
          <strong class="ai-seg-panel__stat-value">
            {{ summary?.totalVolumeCm3 != null ? summary.totalVolumeCm3.toFixed(2) : '--' }}
          </strong>
          <span class="ai-seg-panel__stat-unit">cm³</span>
        </div>
        <div class="ai-seg-panel__stat-card">
          <span class="ai-seg-panel__stat-label">平均密度</span>
          <strong class="ai-seg-panel__stat-value">{{ avgDensityHU ?? '--' }}</strong>
          <span class="ai-seg-panel__stat-unit">HU</span>
        </div>
      </section>

      <!-- 元信息卡：风险等级 / 分割模型 / 处理时间 -->
      <section class="ai-seg-panel__meta-grid">
        <div class="ai-seg-panel__meta-card ai-seg-panel__meta-card--risk" :class="riskClass(overallRisk)">
          <span class="ai-seg-panel__meta-card-label">风险等级</span>
          <strong class="ai-seg-panel__meta-card-value ai-seg-panel__meta-card-value--risk">
            {{ overallRisk ?? '--' }}
          </strong>
        </div>
        <div class="ai-seg-panel__meta-card">
          <span class="ai-seg-panel__meta-card-label">分割模型</span>
          <strong class="ai-seg-panel__meta-card-value ai-seg-panel__meta-card-value--small" :title="modelVersion">
            {{ modelVersion ?? '--' }}
          </strong>
        </div>
        <div class="ai-seg-panel__meta-card">
          <span class="ai-seg-panel__meta-card-label">处理时间</span>
          <strong class="ai-seg-panel__meta-card-value">{{ formatProcessingTime(processingTimeMs) }}</strong>
        </div>
      </section>

      <!-- 病灶列表 -->
      <section v-if="lesions.length" class="ai-seg-panel__lesion-section">
        <h4 class="ai-seg-panel__section-title">病灶列表</h4>
        <div class="ai-seg-panel__table-wrap">
          <table class="ai-seg-panel__table">
            <thead>
              <tr>
                <th>ID</th>
                <th>位置</th>
                <th>最大径(mm)</th>
                <th>体积(cm³)</th>
                <th>密度(HU)</th>
                <th>风险</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="lesion in lesions"
                :key="lesion.id"
                class="ai-seg-panel__table-row"
                :class="{ 'ai-seg-panel__table-row--active': lesion.id === selectedLesion?.id }"
                @click="handleSelectLesion(lesion)"
              >
                <td>{{ lesion.id }}</td>
                <td>{{ lesionLocationLabel(lesion) }}</td>
                <td>{{ lesion.diameterMm }}</td>
                <td>{{ lesion.volumeCm3 != null ? lesion.volumeCm3.toFixed(2) : '--' }}</td>
                <td>{{ lesion.meanDensityHU ?? '--' }}</td>
                <td>
                  <ElTag v-if="lesion.riskLevel" :type="riskTagType(lesion.riskLevel)" size="small" effect="dark">
                    {{ lesion.riskLevel }}
                  </ElTag>
                  <span v-else>--</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <ElAlert
        v-else
        type="info"
        title="未检出疑似病灶"
        :closable="false"
        show-icon
        class="ai-seg-panel__alert"
      />

      <!-- AI 提示文字 -->
      <section class="ai-seg-panel__hint-section">
        <h4 class="ai-seg-panel__section-title">AI 提示</h4>
        <p class="ai-seg-panel__hint-text">{{ aiHint }}</p>
      </section>

      <!-- 3D 病灶预览 -->
      <section v-if="selectedLesion" class="ai-seg-panel__preview-section">
        <h4 class="ai-seg-panel__section-title">3D 病灶预览</h4>
        <div class="ai-seg-panel__preview-body">
          <div class="ai-seg-panel__preview-viewport">
            <div v-if="previewLoading" class="ai-seg-panel__preview-status">加载中…</div>
            <div v-else-if="previewError" class="ai-seg-panel__preview-status ai-seg-panel__preview-status--error">
              预览不可用
            </div>
            <VtkVolumeViewer
              v-else-if="previewVolume"
              :volume-data="previewVolume"
              :is-mask="true"
              :mask-color="LESION_PREVIEW_COLOR"
              :mask-data-max="1"
              :mask-opacity="0.85"
            />
          </div>
          <div class="ai-seg-panel__preview-metrics">
            <div class="ai-seg-panel__preview-metric">
              <span class="ai-seg-panel__preview-metric-label">体积</span>
              <strong class="ai-seg-panel__preview-metric-val">
                {{ selectedLesion.volumeCm3 != null ? selectedLesion.volumeCm3.toFixed(2) : '--' }} cm³
              </strong>
            </div>
            <div class="ai-seg-panel__preview-metric">
              <span class="ai-seg-panel__preview-metric-label">最大径</span>
              <strong class="ai-seg-panel__preview-metric-val">{{ selectedLesion.diameterMm }} mm</strong>
            </div>
          </div>
        </div>
      </section>

      <!-- 免责声明 -->
      <p class="ai-seg-panel__disclaimer">
        {{ summary?.note ?? 'AI 辅助结果仅供参考，非临床诊断依据，请结合临床综合判断。' }}
      </p>
    </template>

    <!-- ========== 无结果（初始态） ========== -->
    <div v-else class="ai-seg-panel__empty">
      <div class="ai-seg-panel__empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="11" cy="11" r="8" />
          <path d="M21 21l-4.35-4.35M11 8v6M8 11h6" />
        </svg>
      </div>
      <p class="ai-seg-panel__empty-text">暂无分割结果</p>
      <p class="ai-seg-panel__empty-hint">
        {{ readonly ? '该检查暂未执行 AI 分割' : '点击「AI 肺结节分割」按钮开始分析' }}
      </p>
    </div>
  </aside>
</template>

<style scoped>
.ai-seg-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--ct-surface);
  border-inline-start: 1px solid var(--ct-border);
  font-size: 13px;
  color: var(--ct-text);
  container-type: inline-size;
  container-name: ai-seg-panel;
}

/* ---- 顶栏 ---- */
.ai-seg-panel__header {
  position: sticky;
  top: 0;
  z-index: 2;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  padding: 12px 14px;
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-surface-elevated);
  overflow: visible;
}

.ai-seg-panel__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--ct-text);
  letter-spacing: 0.02em;
}

.ai-seg-panel__controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.ai-seg-panel__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  min-width: 0;
}

.ai-seg-panel__field-label {
  font-size: 11px;
  line-height: 1.2;
  color: var(--ct-text-dim);
}

.ai-seg-panel__model-picker {
  position: relative;
  width: 100%;
  min-width: 0;
}

.ai-seg-panel__model-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  padding: 7px 10px;
  border: 1px solid var(--ct-border-strong);
  border-radius: 8px;
  background: var(--ct-bg-soft);
  color: var(--ct-text);
  font-size: 12px;
  line-height: 1.4;
  text-align: start;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease,
    box-shadow 0.15s ease;
}

.ai-seg-panel__model-trigger:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--ct-accent) 45%, var(--ct-border-strong));
  background: var(--ct-surface-elevated);
}

.ai-seg-panel__model-picker.is-open .ai-seg-panel__model-trigger {
  border-color: var(--ct-accent);
  background: var(--ct-surface-elevated);
  box-shadow: 0 0 0 2px var(--ct-accent-soft);
}

.ai-seg-panel__model-trigger:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ai-seg-panel__model-trigger-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-seg-panel__model-trigger-icon {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  color: var(--ct-text-muted);
  transition: transform 0.15s ease;
}

.ai-seg-panel__model-picker.is-open .ai-seg-panel__model-trigger-icon {
  transform: rotate(180deg);
  color: var(--ct-accent);
}

.ai-seg-panel__model-menu {
  position: absolute;
  inset-inline: 0;
  top: calc(100% + 6px);
  z-index: 20;
  margin: 0;
  padding: 6px;
  list-style: none;
  border: 1px solid var(--ct-border-strong);
  border-radius: 10px;
  background: var(--ct-surface-elevated);
  box-shadow:
    0 10px 28px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(255, 255, 255, 0.04);
  max-height: 220px;
  overflow-y: auto;
}

.ai-seg-panel__model-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--ct-text);
  font-size: 12px;
  line-height: 1.45;
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}

.ai-seg-panel__model-menu-item:hover:not(.is-disabled) {
  background: var(--ct-accent-soft);
}

.ai-seg-panel__model-menu-item.is-selected {
  background: color-mix(in srgb, var(--ct-accent) 18%, transparent);
  color: var(--ct-text);
}

.ai-seg-panel__model-menu-item.is-disabled {
  color: var(--ct-text-dim);
  cursor: not-allowed;
  opacity: 0.72;
}

.ai-seg-panel__model-menu-label {
  flex: 1;
  min-width: 0;
}

.ai-seg-panel__model-menu-hint {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--ct-text-dim);
}

.ai-seg-panel__model-menu-check {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--ct-accent);
}

.ai-seg-panel__btn-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  width: 100%;
}

@container ai-seg-panel (min-width: 260px) {
  .ai-seg-panel__btn-row:has(.ai-seg-panel__mask-btn) {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}

.ai-seg-panel__run-btn,
.ai-seg-panel__mask-btn {
  width: 100%;
  margin: 0;
  font-size: 12px;
}

/* ---- 加载 ---- */
.ai-seg-panel__loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 16px;
}

.ai-seg-panel__loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--ct-border);
  border-top-color: var(--ct-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.ai-seg-panel__loading-text {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--ct-text);
}

.ai-seg-panel__loading-hint {
  margin: 0;
  font-size: 11px;
  color: var(--ct-text-dim);
}

.ai-seg-panel__progress-track {
  position: relative;
  width: min(220px, 80%);
  height: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--ct-bg-soft);
}

.ai-seg-panel__progress-bar {
  position: absolute;
  inset-block: 0;
  width: 42%;
  border-radius: inherit;
  background: linear-gradient(90deg, transparent, var(--ct-accent), transparent);
  animation: indeterminate-progress 1.4s ease-in-out infinite;
}

@keyframes indeterminate-progress {
  from { transform: translateX(-110%); }
  to { transform: translateX(260%); }
}

.ai-seg-panel__loading-warn {
  max-width: 260px;
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 1.6;
  text-align: center;
  color: #fbbf24;
}

/* ---- alert ---- */
.ai-seg-panel__alert {
  margin: 12px;
}

/* ---- 汇总卡片：病灶数量/最大径/体积/平均密度 ---- */
.ai-seg-panel__summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  padding: 12px 12px 0;
  flex-shrink: 0;
}

.ai-seg-panel__stat-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 6px;
  border-radius: var(--ct-radius);
  border: 1px solid var(--ct-border);
  background: var(--ct-bg-soft);
  overflow: hidden;
}

.ai-seg-panel__stat-label {
  font-size: 9px;
  color: var(--ct-text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-seg-panel__stat-value {
  font-size: 16px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text);
  line-height: 1.3;
}

.ai-seg-panel__stat-unit {
  font-size: 9px;
  color: var(--ct-text-muted);
}

/* ---- 元信息卡：风险等级/分割模型/处理时间 ---- */
.ai-seg-panel__meta-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  padding: 8px 12px 0;
  flex-shrink: 0;
}

.ai-seg-panel__meta-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 8px;
  border-radius: var(--ct-radius);
  border: 1px solid var(--ct-border);
  background: var(--ct-bg-soft);
  min-width: 0;
}

.ai-seg-panel__meta-card--risk.risk--high {
  border-color: rgba(248, 113, 113, 0.35);
  background: rgba(248, 113, 113, 0.08);
}

.ai-seg-panel__meta-card--risk.risk--medium {
  border-color: rgba(251, 191, 36, 0.35);
  background: rgba(251, 191, 36, 0.08);
}

.ai-seg-panel__meta-card--risk.risk--low {
  border-color: rgba(52, 211, 153, 0.35);
  background: rgba(52, 211, 153, 0.08);
}

.ai-seg-panel__meta-card-label {
  font-size: 9px;
  color: var(--ct-text-dim);
}

.ai-seg-panel__meta-card-value {
  font-size: 13px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-seg-panel__meta-card-value--small {
  font-size: 11px;
}

.risk--high .ai-seg-panel__meta-card-value--risk {
  color: #f87171;
}

.risk--medium .ai-seg-panel__meta-card-value--risk {
  color: #fbbf24;
}

.risk--low .ai-seg-panel__meta-card-value--risk {
  color: #34d399;
}

/* ---- 分区标题 ---- */
.ai-seg-panel__section-title {
  margin: 0 0 8px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ct-text-muted);
}

/* ---- 病灶列表（表格） ---- */
.ai-seg-panel__lesion-section {
  flex-shrink: 0;
  padding: 12px 12px 0;
}

.ai-seg-panel__table-wrap {
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius);
}

.ai-seg-panel__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.ai-seg-panel__table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 6px 8px;
  text-align: left;
  font-weight: 500;
  font-size: 10px;
  color: var(--ct-text-dim);
  background: var(--ct-surface-elevated);
  border-block-end: 1px solid var(--ct-border);
  white-space: nowrap;
}

.ai-seg-panel__table-row {
  cursor: pointer;
  transition: background 0.12s;
}

.ai-seg-panel__table-row td {
  padding: 7px 8px;
  color: var(--ct-text-muted);
  font-family: var(--ct-font-mono);
  border-block-end: 1px solid var(--ct-border);
  white-space: nowrap;
}

.ai-seg-panel__table-row:last-child td {
  border-block-end: none;
}

.ai-seg-panel__table-row:hover {
  background: rgba(42, 157, 143, 0.06);
}

.ai-seg-panel__table-row--active {
  background: var(--ct-accent-soft);
}

.ai-seg-panel__table-row--active td {
  color: var(--ct-text);
}

/* ---- 3D 病灶预览 ---- */
.ai-seg-panel__preview-section {
  flex-shrink: 0;
  padding: 12px 12px 0;
}

.ai-seg-panel__preview-body {
  display: flex;
  align-items: stretch;
  gap: 10px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius);
  background: var(--ct-canvas-bg);
  overflow: hidden;
}

.ai-seg-panel__preview-viewport {
  position: relative;
  width: 128px;
  height: 128px;
  flex-shrink: 0;
}

.ai-seg-panel__preview-status {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--ct-text-dim);
  text-align: center;
  padding: 8px;
}

.ai-seg-panel__preview-status--error {
  color: var(--ct-text-dim);
}

.ai-seg-panel__preview-metrics {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  padding: 8px 10px 8px 0;
  min-width: 0;
}

.ai-seg-panel__preview-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-seg-panel__preview-metric-label {
  font-size: 10px;
  color: var(--ct-text-dim);
}

.ai-seg-panel__preview-metric-val {
  font-size: 15px;
  font-family: var(--ct-font-mono);
  color: var(--ct-accent);
}

/* ---- AI 提示 ---- */
.ai-seg-panel__hint-section {
  flex-shrink: 0;
  padding: 0 12px 12px;
}

.ai-seg-panel__hint-text {
  margin: 0;
  font-size: 11px;
  line-height: 1.7;
  color: var(--ct-text-muted);
  background: var(--ct-bg-soft);
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius);
  padding: 10px 12px;
}

/* ---- 免责声明 ---- */
.ai-seg-panel__disclaimer {
  margin: 0;
  padding: 0 12px 14px;
  font-size: 10px;
  line-height: 1.6;
  color: var(--ct-text-dim);
}

/* ---- 空态 ---- */
.ai-seg-panel__empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 20px;
  text-align: center;
}

.ai-seg-panel__empty-icon {
  width: 44px;
  height: 44px;
  color: var(--ct-text-dim);
}

.ai-seg-panel__empty-icon svg {
  width: 100%;
  height: 100%;
}

.ai-seg-panel__empty-text {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--ct-text-muted);
}

.ai-seg-panel__empty-hint {
  margin: 0;
  font-size: 11px;
  color: var(--ct-text-dim);
  line-height: 1.5;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElDialog, ElProgress, ElTag } from 'element-plus'
import type { CtAnalyzeResult, CtArtifactSeverity } from '@/shared/api/modules/ctViewer'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

const props = defineProps<{
  visible: boolean
  loading?: boolean
  errorMessage?: string
  result?: CtAnalyzeResult | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const severityMeta: Record<
  CtArtifactSeverity,
  { label: string; type: 'success' | 'info' | 'warning' | 'danger' }
> = {
  clean: { label: '无伪影', type: 'success' },
  mild: { label: '轻微', type: 'info' },
  moderate: { label: '中等', type: 'warning' },
  severe: { label: '严重', type: 'danger' },
}

const artifactTypeRows = computed(() => {
  const types = props.result?.artifact_types
  if (!types) return []
  return [
    { key: 'metal', label: '金属伪影', value: types.metal, highlight: true },
    { key: 'beam_hardening', label: '线束硬化', value: types.beam_hardening },
    { key: 'partial_volume', label: '部分容积效应', value: types.partial_volume },
    { key: 'ring', label: '环形伪影', value: types.ring },
  ]
})

const conclusionText = computed(() => {
  if (!props.result) return ''
  return props.result.has_artifact ? '检测到伪影' : '未见明显伪影'
})

const metalProbability = computed(() => {
  const value = props.result?.artifact_types?.metal ?? 0
  return Math.round(value * 1000) / 10
})

const volumeRatioPercent = computed(() => {
  const value = props.result?.artifact_volume_ratio ?? 0
  return Math.round(value * 10000) / 100
})

const inferenceSeconds = computed(() => {
  const ms = props.result?.inference_ms ?? 0
  return (ms / 1000).toFixed(1)
})

function formatPercent(value: number) {
  return `${Math.round(value * 1000) / 10}%`
}

function progressStatus(value: number) {
  if (value >= 0.7) return 'exception'
  if (value >= 0.4) return 'warning'
  return undefined
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    class="ct-artifact-dialog ct-imaging-theme"
    title="CT 伪影分析结果"
    width="520px"
    align-center
    destroy-on-close
  >
    <div v-if="loading" class="ct-artifact-dialog__loading">
      <p class="ct-artifact-dialog__loading-title">正在分析影像…</p>
      <p class="ct-artifact-dialog__loading-hint">CPU 推理约需 15 秒至 2 分钟，请耐心等待</p>
    </div>

    <ElAlert
      v-else-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
    />

    <template v-else-if="result">
      <section class="ct-artifact-dialog__summary" :class="{ 'ct-artifact-dialog__summary--alert': result.has_artifact }">
        <div class="ct-artifact-dialog__summary-main">
          <span class="ct-artifact-dialog__summary-label">分析结论</span>
          <strong class="ct-artifact-dialog__summary-value">{{ conclusionText }}</strong>
        </div>
        <ElTag
          :type="severityMeta[result.severity].type"
          effect="dark"
          size="small"
        >
          {{ severityMeta[result.severity].label }}
        </ElTag>
      </section>

      <section class="ct-artifact-dialog__metal">
        <div class="ct-artifact-dialog__metal-header">
          <span class="ct-artifact-dialog__metal-label">金属伪影概率</span>
          <strong class="ct-artifact-dialog__metal-value">{{ metalProbability }}%</strong>
        </div>
        <ElProgress
          :percentage="metalProbability"
          :stroke-width="10"
          :status="progressStatus(result.artifact_types.metal)"
        />
        <p class="ct-artifact-dialog__metal-hint">模型重点关注是否存在类似金属的高密度伪影</p>
      </section>

      <section class="ct-artifact-dialog__types">
        <h4 class="ct-artifact-dialog__section-title">伪影类型概率</h4>
        <div
          v-for="row in artifactTypeRows"
          :key="row.key"
          class="ct-artifact-dialog__type-row"
          :class="{ 'ct-artifact-dialog__type-row--highlight': row.highlight }"
        >
          <div class="ct-artifact-dialog__type-label">{{ row.label }}</div>
          <div class="ct-artifact-dialog__type-bar">
            <ElProgress
              :percentage="Math.round(row.value * 1000) / 10"
              :stroke-width="8"
              :show-text="false"
              :status="progressStatus(row.value)"
            />
          </div>
          <span class="ct-artifact-dialog__type-value">{{ formatPercent(row.value) }}</span>
        </div>
      </section>

      <section class="ct-artifact-dialog__meta">
        <div class="ct-artifact-dialog__meta-item">
          <span>伪影体素占比</span>
          <strong>{{ volumeRatioPercent }}%</strong>
        </div>
        <div class="ct-artifact-dialog__meta-item">
          <span>推理耗时</span>
          <strong>{{ inferenceSeconds }} s</strong>
        </div>
      </section>

      <p class="ct-artifact-dialog__disclaimer">
        本结果由 AI 模型辅助生成，仅供阅片参考，不能替代医师诊断。
      </p>
    </template>
  </ElDialog>
</template>

<style scoped>
.ct-artifact-dialog__loading {
  padding: 28px 8px;
  text-align: center;
}

.ct-artifact-dialog__loading-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ct-text);
}

.ct-artifact-dialog__loading-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--ct-text-muted);
}

.ct-artifact-dialog__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--ct-radius);
  border: 1px solid var(--ct-border);
  background: var(--ct-surface-elevated);
}

.ct-artifact-dialog__summary--alert {
  border-color: rgba(248, 113, 113, 0.35);
  background: rgba(248, 113, 113, 0.08);
}

.ct-artifact-dialog__summary-label {
  display: block;
  font-size: 11px;
  color: var(--ct-text-dim);
  margin-bottom: 4px;
}

.ct-artifact-dialog__summary-value {
  font-size: 16px;
  color: var(--ct-text);
}

.ct-artifact-dialog__metal {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: var(--ct-radius);
  border: 1px solid rgba(42, 157, 143, 0.35);
  background: var(--ct-accent-soft);
}

.ct-artifact-dialog__metal-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ct-artifact-dialog__metal-label {
  font-size: 12px;
  color: var(--ct-text-muted);
}

.ct-artifact-dialog__metal-value {
  font-size: 22px;
  color: var(--ct-accent);
}

.ct-artifact-dialog__metal-hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--ct-text-dim);
}

.ct-artifact-dialog__types {
  margin-top: 16px;
}

.ct-artifact-dialog__section-title {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ct-text-muted);
}

.ct-artifact-dialog__type-row {
  display: grid;
  grid-template-columns: 96px 1fr 52px;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}

.ct-artifact-dialog__type-row--highlight .ct-artifact-dialog__type-label {
  color: var(--ct-accent);
  font-weight: 600;
}

.ct-artifact-dialog__type-label {
  font-size: 12px;
  color: var(--ct-text-muted);
}

.ct-artifact-dialog__type-value {
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text-dim);
  text-align: right;
}

.ct-artifact-dialog__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 16px;
}

.ct-artifact-dialog__meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: var(--ct-radius);
  border: 1px solid var(--ct-border);
  background: var(--ct-surface);
  font-size: 11px;
  color: var(--ct-text-dim);
}

.ct-artifact-dialog__meta-item strong {
  font-size: 14px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text);
}

.ct-artifact-dialog__disclaimer {
  margin: 14px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ct-text-dim);
}
</style>

<style>
.ct-artifact-dialog.el-dialog {
  --el-dialog-bg-color: var(--ct-surface);
  --el-dialog-title-font-size: 15px;
  --el-text-color-primary: var(--ct-text);
  --el-border-color-lighter: var(--ct-border);
}

.ct-artifact-dialog .el-dialog__header {
  border-bottom: 1px solid var(--ct-border);
  margin-right: 0;
  padding-bottom: 12px;
}

.ct-artifact-dialog .el-dialog__body {
  color: var(--ct-text);
}
</style>

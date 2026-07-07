<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElButton, ElTag, ElTooltip } from 'element-plus'
import type { CtLesionItem, CtRiskLevel, CtSegmentResult } from '@/shared/api/modules/ctViewer'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

const props = defineProps<{
  loading?: boolean
  errorMessage?: string
  result?: CtSegmentResult | null
  readonly?: boolean
}>()

const emit = defineEmits<{
  'run-ai-segment': []
  'select-lesion': [lesion: CtLesionItem]
  'toggle-mask': []
}>()

const lesions = computed(() => props.result?.lesions ?? [])
const summary = computed(() => props.result?.summary)
const isAiResult = computed(() =>
  props.result?.modelVersion != null ||
  props.result?.summary?.modelVersion != null ||
  lesions.value.some((l) => l.source === 'deep_learning'),
)

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
</script>

<template>
  <aside class="ai-seg-panel ct-imaging-theme">
    <!-- ========== 顶栏 ========== -->
    <header class="ai-seg-panel__header">
      <div class="ai-seg-panel__title-row">
        <span class="ai-seg-panel__title">AI 肺结节分割</span>
        <ElTag
          v-if="isAiResult"
          size="small"
          type="success"
          effect="dark"
          class="ai-seg-panel__badge"
        >
          深度学习
        </ElTag>
        <ElTag
          v-else-if="result"
          size="small"
          type="warning"
          effect="dark"
          class="ai-seg-panel__badge"
        >
          规则算法
        </ElTag>
      </div>
      <div v-if="!readonly" class="ai-seg-panel__actions">
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
          <ElButton size="small" @click="emit('toggle-mask')">掩码</ElButton>
        </ElTooltip>
      </div>
    </header>

    <!-- ========== 加载中 ========== -->
    <div v-if="loading" class="ai-seg-panel__loading">
      <div class="ai-seg-panel__loading-spinner" />
      <p class="ai-seg-panel__loading-text">AI 模型推理中，请稍候…</p>
      <p class="ai-seg-panel__loading-hint">首次分析约需 20–60 秒</p>
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
      <!-- 汇总卡片 -->
      <section class="ai-seg-panel__summary-grid">
        <div class="ai-seg-panel__stat-card">
          <span class="ai-seg-panel__stat-label">检出病灶</span>
          <strong class="ai-seg-panel__stat-value">{{ summary?.lesionCount ?? 0 }}</strong>
          <span class="ai-seg-panel__stat-unit">处</span>
        </div>
        <div class="ai-seg-panel__stat-card">
          <span class="ai-seg-panel__stat-label">最大径</span>
          <strong class="ai-seg-panel__stat-value">{{ summary?.maxDiameterMm ?? '--' }}</strong>
          <span class="ai-seg-panel__stat-unit">mm</span>
        </div>
        <div class="ai-seg-panel__stat-card">
          <span class="ai-seg-panel__stat-label">总体积</span>
          <strong class="ai-seg-panel__stat-value">
            {{ summary?.totalVolumeCm3 != null ? summary.totalVolumeCm3.toFixed(2) : '--' }}
          </strong>
          <span class="ai-seg-panel__stat-unit">cm³</span>
        </div>
        <div
          class="ai-seg-panel__stat-card ai-seg-panel__stat-card--risk"
          :class="riskClass(overallRisk)"
        >
          <span class="ai-seg-panel__stat-label">整体风险</span>
          <strong class="ai-seg-panel__stat-value ai-seg-panel__stat-value--risk">
            {{ overallRisk ?? '--' }}
          </strong>
        </div>
      </section>

      <!-- 元信息条 -->
      <div v-if="modelVersion || processingTimeMs != null" class="ai-seg-panel__meta-bar">
        <span v-if="modelVersion" class="ai-seg-panel__meta-item">
          <span class="ai-seg-panel__meta-label">模型</span>
          <span class="ai-seg-panel__meta-val">{{ modelVersion }}</span>
        </span>
        <span v-if="processingTimeMs != null" class="ai-seg-panel__meta-item">
          <span class="ai-seg-panel__meta-label">耗时</span>
          <span class="ai-seg-panel__meta-val">{{ formatProcessingTime(processingTimeMs) }}</span>
        </span>
      </div>

      <!-- 病灶列表 -->
      <section v-if="lesions.length" class="ai-seg-panel__lesion-section">
        <h4 class="ai-seg-panel__section-title">病灶清单</h4>
        <div class="ai-seg-panel__lesion-list">
          <button
            v-for="lesion in lesions"
            :key="lesion.id"
            type="button"
            class="ai-seg-panel__lesion-row"
            @click="emit('select-lesion', lesion)"
          >
            <div class="ai-seg-panel__lesion-left">
              <span class="ai-seg-panel__lesion-id">{{ lesion.label }}</span>
              <span class="ai-seg-panel__lesion-loc">
                轴位第 {{ lesion.sliceIndex + 1 }} 层
              </span>
            </div>

            <div class="ai-seg-panel__lesion-metrics">
              <span class="ai-seg-panel__lesion-metric">
                <span class="ai-seg-panel__lesion-metric-label">直径</span>
                <span class="ai-seg-panel__lesion-metric-val">{{ lesion.diameterMm }} mm</span>
              </span>
              <span v-if="lesion.volumeCm3 != null" class="ai-seg-panel__lesion-metric">
                <span class="ai-seg-panel__lesion-metric-label">体积</span>
                <span class="ai-seg-panel__lesion-metric-val">{{ lesion.volumeCm3.toFixed(2) }} cm³</span>
              </span>
              <span v-if="lesion.meanDensityHU != null" class="ai-seg-panel__lesion-metric">
                <span class="ai-seg-panel__lesion-metric-label">密度</span>
                <span class="ai-seg-panel__lesion-metric-val">{{ lesion.meanDensityHU }} HU</span>
              </span>
            </div>

            <div class="ai-seg-panel__lesion-right">
              <ElTag
                v-if="lesion.riskLevel"
                :type="riskTagType(lesion.riskLevel)"
                size="small"
                effect="dark"
              >
                {{ lesion.riskLevel }}
              </ElTag>
              <span class="ai-seg-panel__lesion-conf">
                {{ Math.round(lesion.confidence * 100) }}%
              </span>
              <span class="ai-seg-panel__lesion-jump">定位 ›</span>
            </div>
          </button>
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
  overflow: hidden;
  background: var(--ct-surface);
  border-inline-start: 1px solid var(--ct-border);
  font-size: 13px;
  color: var(--ct-text);
}

/* ---- 顶栏 ---- */
.ai-seg-panel__header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-surface-elevated);
}

.ai-seg-panel__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-seg-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
  letter-spacing: 0.02em;
}

.ai-seg-panel__badge {
  font-size: 10px;
}

.ai-seg-panel__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-seg-panel__run-btn {
  font-size: 12px;
}

/* ---- 滚动区域 ---- */
.ai-seg-panel > *:not(.ai-seg-panel__header) {
  overflow-y: auto;
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

/* ---- alert ---- */
.ai-seg-panel__alert {
  margin: 12px;
}

/* ---- 汇总卡片 ---- */
.ai-seg-panel__summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px;
  flex-shrink: 0;
}

.ai-seg-panel__stat-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border-radius: var(--ct-radius);
  border: 1px solid var(--ct-border);
  background: var(--ct-bg-soft);
}

.ai-seg-panel__stat-card--risk.risk--high {
  border-color: rgba(248, 113, 113, 0.35);
  background: rgba(248, 113, 113, 0.08);
}

.ai-seg-panel__stat-card--risk.risk--medium {
  border-color: rgba(251, 191, 36, 0.35);
  background: rgba(251, 191, 36, 0.08);
}

.ai-seg-panel__stat-card--risk.risk--low {
  border-color: rgba(52, 211, 153, 0.35);
  background: rgba(52, 211, 153, 0.08);
}

.ai-seg-panel__stat-label {
  font-size: 10px;
  color: var(--ct-text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.ai-seg-panel__stat-value {
  font-size: 20px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text);
  line-height: 1;
}

.ai-seg-panel__stat-value--risk {
  font-size: 15px;
}

.risk--high .ai-seg-panel__stat-value--risk {
  color: #f87171;
}

.risk--medium .ai-seg-panel__stat-value--risk {
  color: #fbbf24;
}

.risk--low .ai-seg-panel__stat-value--risk {
  color: #34d399;
}

.ai-seg-panel__stat-unit {
  font-size: 10px;
  color: var(--ct-text-muted);
}

/* ---- 元信息条 ---- */
.ai-seg-panel__meta-bar {
  display: flex;
  gap: 14px;
  padding: 6px 12px 10px;
  flex-shrink: 0;
}

.ai-seg-panel__meta-item {
  display: flex;
  gap: 4px;
  font-size: 10px;
}

.ai-seg-panel__meta-label {
  color: var(--ct-text-dim);
}

.ai-seg-panel__meta-val {
  color: var(--ct-text-muted);
  font-family: var(--ct-font-mono);
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

/* ---- 病灶列表 ---- */
.ai-seg-panel__lesion-section {
  flex-shrink: 0;
  padding: 0 12px 12px;
}

.ai-seg-panel__lesion-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
}

.ai-seg-panel__lesion-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius);
  background: var(--ct-bg-soft);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.ai-seg-panel__lesion-row:hover {
  border-color: rgba(42, 157, 143, 0.4);
  background: rgba(42, 157, 143, 0.06);
}

.ai-seg-panel__lesion-left {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 64px;
  flex-shrink: 0;
}

.ai-seg-panel__lesion-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--ct-text);
}

.ai-seg-panel__lesion-loc {
  font-size: 10px;
  color: var(--ct-text-dim);
  font-family: var(--ct-font-mono);
}

.ai-seg-panel__lesion-metrics {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ai-seg-panel__lesion-metric {
  display: flex;
  gap: 4px;
  font-size: 11px;
}

.ai-seg-panel__lesion-metric-label {
  color: var(--ct-text-dim);
  min-width: 24px;
}

.ai-seg-panel__lesion-metric-val {
  font-family: var(--ct-font-mono);
  color: var(--ct-text-muted);
}

.ai-seg-panel__lesion-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.ai-seg-panel__lesion-conf {
  font-size: 12px;
  font-family: var(--ct-font-mono);
  color: var(--ct-accent);
}

.ai-seg-panel__lesion-jump {
  font-size: 10px;
  color: var(--ct-text-dim);
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

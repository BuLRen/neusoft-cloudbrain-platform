<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElButton, ElDialog, ElTag } from 'element-plus'
import type { CtLesionItem, CtSegmentResult } from '@/shared/api/modules/ctViewer'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

const props = defineProps<{
  visible: boolean
  loading?: boolean
  errorMessage?: string
  result?: CtSegmentResult | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'select-lesion': [lesion: CtLesionItem]
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const lesions = computed(() => props.result?.lesions ?? [])
const summary = computed(() => props.result?.summary)
const methodLabel = computed(() => {
  const method = summary.value?.method
  if (method === 'rule_based') return '规则检测'
  if (method === 'synthetic_fallback') return '演示合成'
  return '演示'
})

function handleSelectLesion(lesion: CtLesionItem) {
  emit('select-lesion', lesion)
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    class="ct-segment-dialog ct-imaging-theme"
    title="AI 病灶分割结果"
    width="560px"
    align-center
    destroy-on-close
  >
    <div v-if="loading" class="ct-segment-dialog__loading">
      <p class="ct-segment-dialog__loading-title">正在执行病灶分割…</p>
      <p class="ct-segment-dialog__loading-hint">算法分析中，请稍候</p>
    </div>

    <ElAlert
      v-else-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
    />

    <template v-else-if="result">
      <section class="ct-segment-dialog__summary">
        <div>
          <span class="ct-segment-dialog__summary-label">检出病灶</span>
          <strong class="ct-segment-dialog__summary-value">{{ summary?.lesionCount ?? 0 }} 处</strong>
        </div>
        <ElTag type="warning" effect="dark" size="small">{{ methodLabel }}</ElTag>
      </section>

      <section v-if="lesions.length" class="ct-segment-dialog__list">
        <h4 class="ct-segment-dialog__section-title">病灶清单</h4>
        <button
          v-for="lesion in lesions"
          :key="lesion.id"
          type="button"
          class="ct-segment-dialog__lesion-row"
          @click="handleSelectLesion(lesion)"
        >
          <div class="ct-segment-dialog__lesion-main">
            <span class="ct-segment-dialog__lesion-label">{{ lesion.label }} #{{ lesion.id }}</span>
            <span class="ct-segment-dialog__lesion-meta">
              轴位第 {{ lesion.sliceIndex + 1 }} 层 · 直径约 {{ lesion.diameterMm }} mm
            </span>
          </div>
          <div class="ct-segment-dialog__lesion-side">
            <span class="ct-segment-dialog__confidence">{{ Math.round(lesion.confidence * 100) }}%</span>
            <span class="ct-segment-dialog__jump-hint">定位</span>
          </div>
        </button>
      </section>

      <ElAlert
        v-else
        type="info"
        title="未检出疑似病灶"
        :closable="false"
        show-icon
      />

      <section v-if="summary" class="ct-segment-dialog__meta">
        <div class="ct-segment-dialog__meta-item">
          <span>最大径</span>
          <strong>{{ summary.maxDiameterMm }} mm</strong>
        </div>
        <div class="ct-segment-dialog__meta-item">
          <span>分析方式</span>
          <strong>{{ methodLabel }}</strong>
        </div>
      </section>

      <p class="ct-segment-dialog__disclaimer">
        {{ summary?.note ?? '仅用于教学演示，非临床诊断依据。' }}
      </p>
    </template>
  </ElDialog>
</template>

<style scoped>
.ct-segment-dialog__loading {
  padding: 28px 8px;
  text-align: center;
}

.ct-segment-dialog__loading-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ct-text);
}

.ct-segment-dialog__loading-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--ct-text-muted);
}

.ct-segment-dialog__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--ct-radius);
  border: 1px solid rgba(80, 220, 120, 0.35);
  background: rgba(80, 220, 120, 0.08);
}

.ct-segment-dialog__summary-label {
  display: block;
  font-size: 11px;
  color: var(--ct-text-dim);
  margin-bottom: 4px;
}

.ct-segment-dialog__summary-value {
  font-size: 18px;
  color: var(--ct-text);
}

.ct-segment-dialog__list {
  margin-top: 16px;
}

.ct-segment-dialog__section-title {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ct-text-muted);
}

.ct-segment-dialog__lesion-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  margin-bottom: 8px;
  padding: 12px 14px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius);
  background: var(--ct-surface);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.ct-segment-dialog__lesion-row:hover {
  border-color: rgba(80, 220, 120, 0.45);
  background: rgba(80, 220, 120, 0.06);
}

.ct-segment-dialog__lesion-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}

.ct-segment-dialog__lesion-meta {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--ct-text-dim);
}

.ct-segment-dialog__lesion-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.ct-segment-dialog__confidence {
  font-size: 14px;
  font-family: var(--ct-font-mono);
  color: #50dc78;
}

.ct-segment-dialog__jump-hint {
  font-size: 10px;
  color: var(--ct-text-dim);
}

.ct-segment-dialog__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 16px;
}

.ct-segment-dialog__meta-item {
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

.ct-segment-dialog__meta-item strong {
  font-size: 14px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text);
}

.ct-segment-dialog__disclaimer {
  margin: 14px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ct-text-dim);
}
</style>

<style>
.ct-segment-dialog.el-dialog {
  --el-dialog-bg-color: var(--ct-surface);
  --el-dialog-title-font-size: 15px;
  --el-text-color-primary: var(--ct-text);
  --el-border-color-lighter: var(--ct-border);
}

.ct-segment-dialog .el-dialog__header {
  border-bottom: 1px solid var(--ct-border);
  margin-right: 0;
  padding-bottom: 12px;
}

.ct-segment-dialog .el-dialog__body {
  color: var(--ct-text);
}
</style>

<script setup lang="ts">
import { ElMessage, ElOption, ElSelect } from 'element-plus'
import type { CtViewTool } from '@/modules/medtech/ct-viewer/components/CtSliceViewPanel.vue'

export type CtLayoutMode = 'quad' | 'axial' | '3d'

const props = withDefaults(
  defineProps<{
    tool: CtViewTool
    layoutMode: CtLayoutMode
    crosshairEnabled: boolean
    maskOverlayEnabled: boolean
    hasMaskOverlay: boolean
  }>(),
  {
    hasMaskOverlay: false,
  },
)

const emit = defineEmits<{
  'update:tool': [tool: CtViewTool]
  'update:layoutMode': [mode: CtLayoutMode]
  'toggle-crosshair': []
  'toggle-mask-overlay': []
  'reset-view': []
  export: []
  print: []
  'toggle-report': []
}>()

function notImplemented() {
  ElMessage.info('该工具在当前演示版本暂未开放')
}

function selectTool(tool: CtViewTool) {
  emit('update:tool', tool)
}
</script>

<template>
  <div class="ct-toolbar">
    <div class="ct-toolbar__group">
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': tool === 'wlww' }"
        title="拖拽调整窗宽窗位"
        @click="selectTool('wlww')"
      >
        <span class="ct-toolbar__icon">◐</span>
        <span class="ct-toolbar__label">窗宽窗位</span>
      </button>
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': tool === 'zoom' }"
        title="拖拽缩放（上下移动）"
        @click="selectTool('zoom')"
      >
        <span class="ct-toolbar__icon">⊕</span>
        <span class="ct-toolbar__label">缩放</span>
      </button>
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': tool === 'pan' }"
        title="拖拽平移画面"
        @click="selectTool('pan')"
      >
        <span class="ct-toolbar__icon">✥</span>
        <span class="ct-toolbar__label">平移</span>
      </button>
      <button type="button" class="ct-toolbar__btn" title="旋转（仅 3D 视图）" @click="notImplemented">
        <span class="ct-toolbar__icon">↻</span>
        <span class="ct-toolbar__label">旋转</span>
      </button>
    </div>

    <div class="ct-toolbar__divider" />

    <div class="ct-toolbar__group">
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': tool === 'measure' }"
        title="拖拽测量真实距离"
        @click="selectTool('measure')"
      >
        <span class="ct-toolbar__icon">↔</span>
        <span class="ct-toolbar__label">测量</span>
      </button>
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': tool === 'roi' }"
        title="拖拽圈定矩形 ROI，统计区域 HU 值"
        @click="selectTool('roi')"
      >
        <span class="ct-toolbar__icon">▭</span>
        <span class="ct-toolbar__label">ROI</span>
      </button>
      <button type="button" class="ct-toolbar__btn" title="箭头标注" @click="notImplemented">
        <span class="ct-toolbar__icon">↗</span>
        <span class="ct-toolbar__label">箭头</span>
      </button>
      <button type="button" class="ct-toolbar__btn" title="角度测量" @click="notImplemented">
        <span class="ct-toolbar__icon">∠</span>
        <span class="ct-toolbar__label">角度</span>
      </button>
    </div>

    <div class="ct-toolbar__divider" />

    <div class="ct-toolbar__group">
      <button type="button" class="ct-toolbar__btn" title="双期对比（暂未开放）" @click="notImplemented">
        <span class="ct-toolbar__icon">◧</span>
        <span class="ct-toolbar__label">对比</span>
      </button>
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': crosshairEnabled }"
        title="切换三视图十字线联动定位"
        @click="emit('toggle-crosshair')"
      >
        <span class="ct-toolbar__icon">⋈</span>
        <span class="ct-toolbar__label">同层</span>
      </button>
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': maskOverlayEnabled }"
        :disabled="!hasMaskOverlay"
        title="切换 AI 分割掩码叠加显示"
        @click="emit('toggle-mask-overlay')"
      >
        <span class="ct-toolbar__icon">◱</span>
        <span class="ct-toolbar__label">分层显示</span>
      </button>
    </div>

    <div class="ct-toolbar__divider" />

    <div class="ct-toolbar__group">
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': layoutMode === 'quad' }"
        title="四联视图（轴位/冠状/矢状/3D）"
        @click="emit('update:layoutMode', 'quad')"
      >
        <span class="ct-toolbar__icon ct-toolbar__icon--text">MPR</span>
        <span class="ct-toolbar__label">MPR</span>
      </button>
      <button
        type="button"
        class="ct-toolbar__btn"
        :class="{ 'ct-toolbar__btn--active': layoutMode === '3d' }"
        title="单幅 3D 体渲染视图"
        @click="emit('update:layoutMode', '3d')"
      >
        <span class="ct-toolbar__icon ct-toolbar__icon--text">3D</span>
        <span class="ct-toolbar__label">3D</span>
      </button>
      <button type="button" class="ct-toolbar__btn" title="重置当前所有视图的缩放/平移" @click="emit('reset-view')">
        <span class="ct-toolbar__icon">⟲</span>
        <span class="ct-toolbar__label">重建</span>
      </button>
    </div>

    <div class="ct-toolbar__divider" />

    <div class="ct-toolbar__group">
      <button type="button" class="ct-toolbar__btn" title="导出当前轴位切片 PNG" @click="emit('export')">
        <span class="ct-toolbar__icon">⇩</span>
        <span class="ct-toolbar__label">导出</span>
      </button>
      <button type="button" class="ct-toolbar__btn" title="打印当前视图" @click="emit('print')">
        <span class="ct-toolbar__icon">⎙</span>
        <span class="ct-toolbar__label">打印</span>
      </button>
      <button type="button" class="ct-toolbar__btn" title="打开 / 收起诊断报告面板" @click="emit('toggle-report')">
        <span class="ct-toolbar__icon">▤</span>
        <span class="ct-toolbar__label">报告</span>
      </button>
    </div>

    <div class="ct-toolbar__spacer" />

    <ElSelect
      class="ct-toolbar__layout-select"
      size="small"
      :model-value="layoutMode"
      @update:model-value="(value) => emit('update:layoutMode', value as CtLayoutMode)"
    >
      <ElOption label="默认布局（四联）" value="quad" />
      <ElOption label="单幅轴位" value="axial" />
      <ElOption label="单幅 3D" value="3d" />
    </ElSelect>
  </div>
</template>

<style scoped>
.ct-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-surface);
  overflow-x: auto;
  flex-shrink: 0;
}

.ct-toolbar__group {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.ct-toolbar__divider {
  width: 1px;
  align-self: stretch;
  margin: 4px 6px;
  background: var(--ct-border);
  flex-shrink: 0;
}

.ct-toolbar__spacer {
  flex: 1;
  min-width: 12px;
}

.ct-toolbar__btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 52px;
  padding: 5px 2px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--ct-text-muted);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.12s, color 0.12s, border-color 0.12s;
}

.ct-toolbar__btn:hover {
  background: var(--ct-accent-soft);
  color: var(--ct-accent);
}

.ct-toolbar__btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.ct-toolbar__btn:disabled:hover {
  background: transparent;
  color: var(--ct-text-muted);
}

.ct-toolbar__btn--active {
  background: var(--ct-accent-soft);
  border-color: var(--ct-accent);
  color: var(--ct-accent);
}

.ct-toolbar__icon {
  font-size: 15px;
  line-height: 1;
}

.ct-toolbar__icon--text {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.ct-toolbar__label {
  font-size: 10px;
  white-space: nowrap;
  line-height: 1;
}

.ct-toolbar__layout-select {
  width: 168px;
  flex-shrink: 0;
}

.ct-toolbar__layout-select :deep(.el-select__wrapper) {
  font-size: 12px;
  background: var(--ct-surface-elevated);
}
</style>

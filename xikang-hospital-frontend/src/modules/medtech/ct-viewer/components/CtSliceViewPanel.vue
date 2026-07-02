<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    plane: 'axial' | 'coronal' | 'sagittal'
    sliceIndex: number
    sliceTotal: number
    windowCenter: number
    windowWidth: number
    spacingMm?: number
    seriesLabel?: string
    zoomLabel?: string
  }>(),
  {
    spacingMm: 0.5,
    seriesLabel: '1',
    zoomLabel: '1:1',
  },
)

const sliceLabel = computed(() => {
  if (!props.sliceTotal) return 'Im: -/-'
  return `Im: ${props.sliceIndex + 1}/${props.sliceTotal}`
})

const orientation = computed(() => {
  switch (props.plane) {
    case 'axial':
      return { top: 'A', bottom: 'P', left: 'R', right: 'L' }
    case 'coronal':
      return { top: 'H', bottom: 'F', left: 'R', right: 'L' }
    case 'sagittal':
      return { top: 'H', bottom: 'F', left: 'A', right: 'P' }
  }
})

const scaleBarMm = 50
const scaleBarPx = computed(() => Math.max(Math.round(scaleBarMm / Math.max(props.spacingMm, 0.01)), 24))
</script>

<template>
  <div class="ct-slice-panel">
    <header class="ct-slice-panel__header">
      <span class="ct-slice-panel__title">{{ title }}</span>
      <div class="ct-slice-panel__header-actions">
        <span class="ct-slice-panel__zoom">{{ zoomLabel }}</span>
      </div>
    </header>

    <div class="ct-slice-panel__viewport">
      <slot />

      <div class="ct-slice-panel__overlay ct-slice-panel__overlay--tl">
        <span>{{ sliceLabel }}</span>
        <span>Se: {{ seriesLabel }}</span>
      </div>

      <span class="ct-slice-panel__dir ct-slice-panel__dir--top">{{ orientation.top }}</span>
      <span class="ct-slice-panel__dir ct-slice-panel__dir--bottom">{{ orientation.bottom }}</span>
      <span class="ct-slice-panel__dir ct-slice-panel__dir--left">{{ orientation.left }}</span>
      <span class="ct-slice-panel__dir ct-slice-panel__dir--right">{{ orientation.right }}</span>

      <div class="ct-slice-panel__scale" :style="{ height: `${scaleBarPx}px` }">
        <div class="ct-slice-panel__scale-bar" />
        <span>{{ scaleBarMm }} mm</span>
      </div>

      <div class="ct-slice-panel__wl">
        WL: {{ windowCenter }} · WW: {{ windowWidth }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.ct-slice-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-panel);
  overflow: hidden;
}

.ct-slice-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-surface);
}

.ct-slice-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-text);
}

.ct-slice-panel__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ct-slice-panel__zoom {
  font-size: 11px;
  color: var(--ct-text-dim);
  font-family: var(--ct-font-mono);
}

.ct-slice-panel__viewport {
  position: relative;
  flex: 1;
  min-height: 120px;
  background: var(--ct-canvas-bg);
}

.ct-slice-panel__viewport :slotted(canvas) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.ct-slice-panel__overlay {
  position: absolute;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text-muted);
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.8);
}

.ct-slice-panel__overlay--tl {
  top: 8px;
  left: 8px;
}

.ct-slice-panel__dir {
  position: absolute;
  z-index: 2;
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.45);
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.9);
}

.ct-slice-panel__dir--top {
  top: 6px;
  left: 50%;
  transform: translateX(-50%);
}

.ct-slice-panel__dir--bottom {
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
}

.ct-slice-panel__dir--left {
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
}

.ct-slice-panel__dir--right {
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
}

.ct-slice-panel__scale {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  pointer-events: none;
}

.ct-slice-panel__scale-bar {
  width: 3px;
  flex: 1;
  background: linear-gradient(to bottom, var(--ct-accent), rgba(42, 157, 143, 0.4));
  border-radius: 2px;
  box-shadow: 0 0 6px var(--ct-accent-glow);
}

.ct-slice-panel__scale span {
  font-size: 9px;
  color: var(--ct-text-dim);
  font-family: var(--ct-font-mono);
  writing-mode: vertical-rl;
}

.ct-slice-panel__wl {
  position: absolute;
  left: 8px;
  bottom: 8px;
  z-index: 2;
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-accent);
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.8);
}
</style>

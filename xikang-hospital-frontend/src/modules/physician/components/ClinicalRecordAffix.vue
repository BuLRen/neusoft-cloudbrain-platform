<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElBadge, ElIcon, ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import ClinicalRecordDrawer from './ClinicalRecordDrawer.vue'

const POSITION_KEY = 'clinical-record-affix-position'
const DRAG_THRESHOLD = 6
const VIEWPORT_MARGIN = 8

const props = withDefaults(defineProps<{
  registerId: number | null
  mode: 'physician' | 'patient'
  disabled?: boolean
  disabledHint?: string
  subtitle?: string
}>(), {
  disabled: false,
  disabledHint: '请先选择就诊记录',
})

const drawerVisible = ref(false)
const drawerRef = ref<InstanceType<typeof ClinicalRecordDrawer> | null>(null)
const anchorRef = ref<HTMLElement | null>(null)
const position = ref<{ x: number; y: number } | null>(null)
const isDragging = ref(false)

const badgeText = computed(() => {
  if (props.disabled || !props.registerId) return ''
  return props.mode === 'physician' ? '' : ''
})

const anchorStyle = computed(() => {
  if (!position.value) return undefined
  return {
    left: `${position.value.x}px`,
    top: `${position.value.y}px`,
  }
})

function loadPosition(): { x: number; y: number } | null {
  try {
    const raw = localStorage.getItem(POSITION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { x?: number; y?: number }
    if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
      return { x: parsed.x, y: parsed.y }
    }
  } catch {
    // ignore invalid persisted position
  }
  return null
}

function savePosition(pos: { x: number; y: number }) {
  localStorage.setItem(POSITION_KEY, JSON.stringify(pos))
}

function getDefaultMargin() {
  return window.innerWidth <= 640 ? 16 : 24
}

function getDefaultPosition(el: HTMLElement) {
  const rect = el.getBoundingClientRect()
  const margin = getDefaultMargin()
  return {
    x: window.innerWidth - rect.width - margin,
    y: window.innerHeight - rect.height - margin,
  }
}

function clampPosition(x: number, y: number, el: HTMLElement) {
  const rect = el.getBoundingClientRect()
  const maxX = window.innerWidth - rect.width - VIEWPORT_MARGIN
  const maxY = window.innerHeight - rect.height - VIEWPORT_MARGIN
  return {
    x: Math.max(VIEWPORT_MARGIN, Math.min(x, maxX)),
    y: Math.max(VIEWPORT_MARGIN, Math.min(y, maxY)),
  }
}

function applyPosition(x: number, y: number) {
  const el = anchorRef.value
  if (!el) return
  position.value = clampPosition(x, y, el)
}

function initPosition() {
  const el = anchorRef.value
  if (!el) return
  const saved = loadPosition()
  const fallback = getDefaultPosition(el)
  applyPosition(saved?.x ?? fallback.x, saved?.y ?? fallback.y)
}

function onWindowResize() {
  if (!position.value || !anchorRef.value) return
  position.value = clampPosition(position.value.x, position.value.y, anchorRef.value)
}

let dragState: {
  startX: number
  startY: number
  originX: number
  originY: number
  moved: boolean
  pointerId: number
} | null = null

function onPointerMove(event: PointerEvent) {
  if (!dragState || !anchorRef.value) return

  const dx = event.clientX - dragState.startX
  const dy = event.clientY - dragState.startY

  if (!dragState.moved && Math.hypot(dx, dy) >= DRAG_THRESHOLD) {
    dragState.moved = true
  }

  applyPosition(dragState.originX + dx, dragState.originY + dy)
}

function onPointerUp(event: PointerEvent) {
  if (!dragState || !anchorRef.value) return

  const wasDrag = dragState.moved
  if (wasDrag && position.value) {
    savePosition(position.value)
  }

  anchorRef.value.releasePointerCapture(dragState.pointerId)
  isDragging.value = false
  dragState = null

  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)

  if (!wasDrag) {
    openDrawer()
  }
}

function onPointerDown(event: PointerEvent) {
  if (!anchorRef.value || !position.value || event.button !== 0) return

  dragState = {
    startX: event.clientX,
    startY: event.clientY,
    originX: position.value.x,
    originY: position.value.y,
    moved: false,
    pointerId: event.pointerId,
  }

  isDragging.value = true
  anchorRef.value.setPointerCapture(event.pointerId)

  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
  window.addEventListener('pointercancel', onPointerUp)
}

function openDrawer() {
  if (props.disabled || !props.registerId) {
    ElMessage.info(props.disabledHint)
    return
  }
  drawerVisible.value = true
}

onMounted(() => {
  nextTick(initPosition)
  window.addEventListener('resize', onWindowResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
})

defineExpose({
  open: openDrawer,
  reload: () => drawerRef.value?.reload(),
})
</script>

<template>
  <Teleport to="body">
    <div
      ref="anchorRef"
      class="clinical-affix-anchor"
      :class="{
        'clinical-affix-anchor--ready': position,
        'clinical-affix-anchor--dragging': isDragging,
      }"
      :style="anchorStyle"
    >
      <button
        type="button"
        class="clinical-affix__trigger"
        aria-label="打开病历本，可拖动调整位置"
        title="拖动可调整位置"
        :class="{
          'clinical-affix__trigger--disabled': disabled || !registerId,
          'clinical-affix__trigger--dragging': isDragging,
        }"
        @pointerdown="onPointerDown"
      >
        <ElBadge :hidden="!badgeText" :value="badgeText">
          <span class="clinical-affix__icon-wrap">
            <ElIcon :size="22"><Document /></ElIcon>
          </span>
        </ElBadge>
        <span class="clinical-affix__label">病历本</span>
      </button>
    </div>
  </Teleport>

  <ClinicalRecordDrawer
    ref="drawerRef"
    v-model:visible="drawerVisible"
    :register-id="registerId"
    :mode="mode"
    :subtitle="subtitle"
  />
</template>

<style scoped>
.clinical-affix-anchor {
  position: fixed;
  z-index: 1200;
  touch-action: none;
}

.clinical-affix-anchor:not(.clinical-affix-anchor--ready) {
  visibility: hidden;
}

.clinical-affix__trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 48px;
  padding: 0 var(--space-4);
  border: none;
  border-radius: 999px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: grab;
  user-select: none;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-strong, #1677ff));
  box-shadow: 0 8px 24px rgba(31, 140, 255, 0.35);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.clinical-affix__trigger:hover:not(.clinical-affix__trigger--disabled):not(.clinical-affix__trigger--dragging) {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(31, 140, 255, 0.42);
}

.clinical-affix__trigger--dragging,
.clinical-affix-anchor--dragging .clinical-affix__trigger {
  cursor: grabbing;
  transform: none;
  box-shadow: 0 10px 28px rgba(31, 140, 255, 0.45);
}

.clinical-affix__trigger--disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.clinical-affix__icon-wrap {
  display: grid;
  place-items: center;
}

@media (max-width: 640px) {
  .clinical-affix__label {
    display: none;
  }

  .clinical-affix__trigger {
    width: 52px;
    height: 52px;
    padding: 0;
    justify-content: center;
    border-radius: 50%;
  }
}
</style>

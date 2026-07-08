<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { clamp, computeContainFit, computeDistanceMm, type RegionStats } from '@/modules/medtech/ct-viewer/lib/imageInteraction'

export type CtViewTool = 'wlww' | 'zoom' | 'pan' | 'measure' | 'roi'

/** AI 病灶标注框（归一化 0~1 坐标，由父组件按当前平面/切片计算好传入） */
export interface CtLesionOverlayBox {
  left: number
  top: number
  width: number
  height: number
  label: string
  confidence: number
}

const MONO_FONT = 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace'
const DRAG_THRESHOLD_PX = 4
const MIN_LESION_BOX_PX = 18

const props = withDefaults(
  defineProps<{
    title: string
    plane: 'axial' | 'coronal' | 'sagittal'
    sliceIndex: number
    sliceTotal: number
    windowCenter: number
    windowWidth: number
    /** 该平面行/列方向的真实体素间距（mm），用于测量/ROI/比例尺换算 */
    rowSpacingMm?: number
    colSpacingMm?: number
    seriesLabel?: string
    /** 该平面切片的图像像素尺寸（用于交互坐标换算） */
    naturalWidth?: number
    naturalHeight?: number
    tool?: CtViewTool
    /** 十字线位置（该平面归一化 0~1 坐标），为空则不显示 */
    crosshair?: { x: number; y: number } | null
    /** 上一次 ROI 请求的统计结果，用于在框旁显示数值 */
    roiResult?: RegionStats | null
    active?: boolean
    /** AI 病灶标注框（归一化坐标），为空则不显示 */
    lesionBoxes?: CtLesionOverlayBox[]
  }>(),
  {
    rowSpacingMm: 0.7,
    colSpacingMm: 0.7,
    seriesLabel: '1',
    naturalWidth: 512,
    naturalHeight: 512,
    tool: 'wlww',
    crosshair: null,
    roiResult: null,
    active: false,
    lesionBoxes: () => [],
  },
)

const emit = defineEmits<{
  'wheel-slice': [delta: number]
  'window-delta': [dx: number, dy: number]
  'crosshair-set': [point: { x: number; y: number }]
  'roi-request': [rect: { x0: number; y0: number; x1: number; y1: number }]
  focus: []
}>()

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

const viewportRef = ref<HTMLElement | null>(null)
const stageRef = ref<HTMLElement | null>(null)
const overlayCanvas = ref<HTMLCanvasElement | null>(null)

const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)
const fit = ref({ width: 0, height: 0, offsetX: 0, offsetY: 0 })

function recomputeFit() {
  const el = viewportRef.value
  if (!el) return
  // 按真实物理尺寸（mm）计算 contain 比例，避免层厚与层内间距不一致时冠状/矢状图被压扁
  const physWidth = props.naturalWidth * props.colSpacingMm
  const physHeight = props.naturalHeight * props.rowSpacingMm
  fit.value = computeContainFit(el.clientWidth, el.clientHeight, physWidth, physHeight)
}

let resizeObserver: ResizeObserver | null = null
onMounted(() => {
  recomputeFit()
  resizeObserver = new ResizeObserver(() => {
    recomputeFit()
    drawOverlay()
  })
  if (viewportRef.value) resizeObserver.observe(viewportRef.value)
  void nextTick().then(drawOverlay)
})
onBeforeUnmount(() => resizeObserver?.disconnect())
watch(() => [props.naturalWidth, props.naturalHeight, props.colSpacingMm, props.rowSpacingMm], () => {
  recomputeFit()
  void nextTick().then(drawOverlay)
})
watch(() => props.lesionBoxes, () => drawOverlay(), { deep: true })

const stageStyle = computed(() => ({
  left: `${fit.value.offsetX}px`,
  top: `${fit.value.offsetY}px`,
  width: `${fit.value.width}px`,
  height: `${fit.value.height}px`,
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`,
}))

const zoomPercentLabel = computed(() => `${Math.round(zoom.value * 100)}%`)

const scaleBarMm = 50
const scaleBarPx = computed(() => {
  const spacing = Math.max(props.colSpacingMm, 0.01)
  return Math.max(Math.round((scaleBarMm / spacing) * zoom.value), 24)
})

interface NormPoint {
  x: number
  y: number
}
interface Measurement {
  p0: NormPoint
  p1: NormPoint
  distanceMm: number
}
interface RoiRectNorm {
  x0: number
  y0: number
  x1: number
  y1: number
}

const measurement = ref<Measurement | null>(null)
const roiRect = ref<RoiRectNorm | null>(null)

let dragging = false
let didDrag = false
let startClientX = 0
let startClientY = 0
let lastClientX = 0
let lastClientY = 0
let startNorm: NormPoint = { x: 0.5, y: 0.5 }

function eventToNorm(e: PointerEvent): NormPoint {
  const stage = stageRef.value
  if (!stage) return { x: 0.5, y: 0.5 }
  const rect = stage.getBoundingClientRect()
  if (!rect.width || !rect.height) return { x: 0.5, y: 0.5 }
  return {
    x: clamp((e.clientX - rect.left) / rect.width, 0, 1),
    y: clamp((e.clientY - rect.top) / rect.height, 0, 1),
  }
}

function handleWheel(e: WheelEvent) {
  emit('wheel-slice', e.deltaY > 0 ? 1 : -1)
}

function handlePointerDown(e: PointerEvent) {
  if (e.button !== 0) return
  emit('focus')
  dragging = true
  didDrag = false
  startClientX = e.clientX
  startClientY = e.clientY
  lastClientX = e.clientX
  lastClientY = e.clientY
  startNorm = eventToNorm(e)
  ;(e.currentTarget as Element)?.setPointerCapture?.(e.pointerId)

  if (props.tool === 'measure') {
    measurement.value = { p0: startNorm, p1: startNorm, distanceMm: 0 }
  } else if (props.tool === 'roi') {
    roiRect.value = { x0: startNorm.x, y0: startNorm.y, x1: startNorm.x, y1: startNorm.y }
  }
  drawOverlay()
}

function handlePointerMove(e: PointerEvent) {
  if (!dragging) return
  const dx = e.clientX - lastClientX
  const dy = e.clientY - lastClientY
  lastClientX = e.clientX
  lastClientY = e.clientY

  if (!didDrag) {
    const totalDx = e.clientX - startClientX
    const totalDy = e.clientY - startClientY
    if (Math.abs(totalDx) > DRAG_THRESHOLD_PX || Math.abs(totalDy) > DRAG_THRESHOLD_PX) {
      didDrag = true
    }
  }

  if (props.tool === 'wlww') {
    if (didDrag) emit('window-delta', dx, dy)
  } else if (props.tool === 'zoom') {
    if (didDrag) zoom.value = clamp(zoom.value * (1 - dy * 0.006), 0.2, 8)
  } else if (props.tool === 'pan') {
    if (didDrag) {
      panX.value += dx
      panY.value += dy
    }
  } else if (props.tool === 'measure' && measurement.value) {
    const p1 = eventToNorm(e)
    const distanceMm = computeDistanceMm(
      measurement.value.p0,
      p1,
      props.naturalWidth,
      props.naturalHeight,
      props.colSpacingMm,
      props.rowSpacingMm,
    )
    measurement.value = { p0: measurement.value.p0, p1, distanceMm }
    drawOverlay()
  } else if (props.tool === 'roi' && roiRect.value) {
    const p1 = eventToNorm(e)
    roiRect.value = { x0: roiRect.value.x0, y0: roiRect.value.y0, x1: p1.x, y1: p1.y }
    drawOverlay()
  }
}

function handlePointerUp() {
  if (!dragging) return
  dragging = false

  if (props.tool === 'roi') {
    if (roiRect.value && didDrag) {
      emit('roi-request', { ...roiRect.value })
    } else {
      roiRect.value = null
    }
  } else if (props.tool === 'measure') {
    if (!didDrag) measurement.value = null
  } else if (!didDrag) {
    emit('crosshair-set', startNorm)
  }
  drawOverlay()
}

function handleDoubleClick() {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
}

function clearAnnotations() {
  measurement.value = null
  roiRect.value = null
  drawOverlay()
}

function resetView() {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
}

watch(() => props.sliceIndex, () => clearAnnotations())
watch([() => props.crosshair, () => props.roiResult, zoom], () => drawOverlay(), { deep: true })

function drawOverlay() {
  const canvas = overlayCanvas.value
  if (!canvas) return
  // 叠加层画布按“实际显示尺寸”设置分辨率（而非体素分辨率），避免冠状/矢状图因
  // 物理比例非等比缩放（层厚 ≠ 层内间距）导致标注框线宽/字体被拉花变形。
  const displayWidth = Math.max(1, Math.round(fit.value.width) || Math.round(props.naturalWidth))
  const displayHeight = Math.max(1, Math.round(fit.value.height) || Math.round(props.naturalHeight))
  if (canvas.width !== displayWidth) canvas.width = displayWidth
  if (canvas.height !== displayHeight) canvas.height = displayHeight
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const invZoom = 1 / Math.max(zoom.value, 0.001)

  if (props.crosshair) {
    const cx = props.crosshair.x * canvas.width
    const cy = props.crosshair.y * canvas.height
    const gap = 9 * invZoom
    ctx.save()
    ctx.strokeStyle = 'rgba(52, 211, 153, 0.85)'
    ctx.lineWidth = Math.max(1 * invZoom, 0.6)
    ctx.setLineDash([4 * invZoom, 3 * invZoom])
    ctx.beginPath()
    ctx.moveTo(0, cy)
    ctx.lineTo(cx - gap, cy)
    ctx.moveTo(cx + gap, cy)
    ctx.lineTo(canvas.width, cy)
    ctx.moveTo(cx, 0)
    ctx.lineTo(cx, cy - gap)
    ctx.moveTo(cx, cy + gap)
    ctx.lineTo(cx, canvas.height)
    ctx.stroke()
    ctx.setLineDash([])
    ctx.beginPath()
    ctx.arc(cx, cy, 2.5 * invZoom, 0, Math.PI * 2)
    ctx.fillStyle = 'rgba(52, 211, 153, 0.9)'
    ctx.fill()
    ctx.restore()
  }

  if (measurement.value) {
    const { p0, p1, distanceMm } = measurement.value
    const x0 = p0.x * canvas.width
    const y0 = p0.y * canvas.height
    const x1 = p1.x * canvas.width
    const y1 = p1.y * canvas.height
    ctx.save()
    ctx.strokeStyle = '#fbbf24'
    ctx.fillStyle = '#fbbf24'
    ctx.lineWidth = Math.max(1.5 * invZoom, 0.8)
    ctx.beginPath()
    ctx.moveTo(x0, y0)
    ctx.lineTo(x1, y1)
    ctx.stroke()
    for (const [x, y] of [[x0, y0], [x1, y1]] as const) {
      ctx.beginPath()
      ctx.arc(x, y, 3 * invZoom, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.font = `${12 * invZoom}px ${MONO_FONT}`
    ctx.shadowColor = 'rgba(0,0,0,0.85)'
    ctx.shadowBlur = 3 * invZoom
    ctx.fillText(`${distanceMm.toFixed(1)} mm`, (x0 + x1) / 2 + 6 * invZoom, (y0 + y1) / 2 - 6 * invZoom)
    ctx.restore()
  }

  if (roiRect.value) {
    const { x0, y0, x1, y1 } = roiRect.value
    const left = Math.min(x0, x1) * canvas.width
    const top = Math.min(y0, y1) * canvas.height
    const w = Math.abs(x1 - x0) * canvas.width
    const h = Math.abs(y1 - y0) * canvas.height
    ctx.save()
    ctx.strokeStyle = '#60a5fa'
    ctx.lineWidth = Math.max(1.5 * invZoom, 0.8)
    ctx.setLineDash([5 * invZoom, 3 * invZoom])
    ctx.strokeRect(left, top, w, h)
    ctx.setLineDash([])
    if (props.roiResult) {
      ctx.font = `${11 * invZoom}px ${MONO_FONT}`
      ctx.fillStyle = '#60a5fa'
      ctx.shadowColor = 'rgba(0,0,0,0.85)'
      ctx.shadowBlur = 3 * invZoom
      const lines = [
        `均值 ${props.roiResult.mean.toFixed(1)} HU`,
        `范围 ${props.roiResult.min.toFixed(0)}~${props.roiResult.max.toFixed(0)}`,
        `面积 ${props.roiResult.areaMm2.toFixed(1)} mm²`,
      ]
      lines.forEach((line, i) => {
        ctx.fillText(line, left + w + 6 * invZoom, top + 12 * invZoom + i * 14 * invZoom)
      })
    }
    ctx.restore()
  }

  if (props.lesionBoxes?.length) {
    drawLesionBoxes(ctx, canvas, props.lesionBoxes, invZoom)
  }
}

function drawLesionBoxes(
  ctx: CanvasRenderingContext2D,
  canvas: HTMLCanvasElement,
  boxes: CtLesionOverlayBox[],
  invZoom: number,
) {
  boxes.forEach((box, index) => {
    let left = box.left * canvas.width
    let top = box.top * canvas.height
    let width = box.width * canvas.width
    let height = box.height * canvas.height

    const minPx = MIN_LESION_BOX_PX * invZoom
    if (width < minPx) {
      const cx = left + width / 2
      left = cx - minPx / 2
      width = minPx
    }
    if (height < minPx) {
      const cy = top + height / 2
      top = cy - minPx / 2
      height = minPx
    }

    ctx.save()
    ctx.strokeStyle = '#ff4d4f'
    ctx.fillStyle = '#ff4d4f'
    ctx.lineWidth = Math.max(1.5 * invZoom, 0.8)
    ctx.strokeRect(left, top, width, height)

    const percent = `${Math.round(box.confidence * 100)}%`
    const labelX = clamp(left + width + 6 * invZoom, 0, canvas.width - 4 * invZoom)
    const labelY = Math.max(14 * invZoom, top - 4 * invZoom + index * 30 * invZoom)

    ctx.font = `600 ${13 * invZoom}px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`
    ctx.shadowColor = 'rgba(0,0,0,0.85)'
    ctx.shadowBlur = 3 * invZoom
    ctx.fillText(box.label, labelX, labelY)
    ctx.font = `${12 * invZoom}px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`
    ctx.fillText(percent, labelX, labelY + 15 * invZoom)
    ctx.restore()
  })
}

defineExpose({ clearAnnotations, resetView })
</script>

<template>
  <div class="ct-slice-panel" :class="{ 'ct-slice-panel--active': active }">
    <header class="ct-slice-panel__header">
      <span class="ct-slice-panel__title">{{ title }}</span>
      <div class="ct-slice-panel__header-actions">
        <slot name="header-extra" />
        <span class="ct-slice-panel__zoom">{{ zoomPercentLabel }}</span>
      </div>
    </header>

    <div
      ref="viewportRef"
      class="ct-slice-panel__viewport"
      @wheel.prevent="handleWheel"
      @pointerdown="handlePointerDown"
      @pointermove="handlePointerMove"
      @pointerup="handlePointerUp"
      @pointercancel="handlePointerUp"
      @dblclick="handleDoubleClick"
      @contextmenu.prevent
    >
      <div ref="stageRef" class="ct-slice-panel__stage" :class="`ct-slice-panel__stage--${tool}`" :style="stageStyle">
        <slot />
        <canvas ref="overlayCanvas" class="ct-slice-panel__overlay-canvas" />
      </div>

      <div class="ct-slice-panel__overlay ct-slice-panel__overlay--tl">
        <span>{{ sliceLabel }}</span>
        <span>Se: {{ seriesLabel }}</span>
      </div>

      <span class="ct-slice-panel__dir ct-slice-panel__dir--top">{{ orientation?.top }}</span>
      <span class="ct-slice-panel__dir ct-slice-panel__dir--bottom">{{ orientation?.bottom }}</span>
      <span class="ct-slice-panel__dir ct-slice-panel__dir--left">{{ orientation?.left }}</span>
      <span class="ct-slice-panel__dir ct-slice-panel__dir--right">{{ orientation?.right }}</span>

      <div class="ct-slice-panel__scale" :style="{ height: `${scaleBarPx}px` }">
        <div class="ct-slice-panel__scale-bar" />
        <span>{{ scaleBarMm }} mm</span>
      </div>

      <div class="ct-slice-panel__wl">
        WL: {{ Math.round(windowCenter) }} · WW: {{ Math.round(windowWidth) }}
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
  transition: border-color 0.15s;
}

.ct-slice-panel--active {
  border-color: var(--ct-accent);
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
  /* 不设固定 min-height：由父级网格精确分配尺寸，contain-fit 会自适应任意尺寸，
     固定下限反而会在网格分配高度较小时把面板撑大后被裁切。 */
  min-height: 0;
  background: var(--ct-canvas-bg);
  overflow: hidden;
  touch-action: none;
}

.ct-slice-panel__stage {
  position: absolute;
  will-change: transform;
}

.ct-slice-panel__stage--wlww { cursor: ns-resize; }
.ct-slice-panel__stage--zoom { cursor: zoom-in; }
.ct-slice-panel__stage--pan { cursor: grab; }
.ct-slice-panel__stage--measure,
.ct-slice-panel__stage--roi { cursor: crosshair; }

.ct-slice-panel__stage :slotted(canvas) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
}

.ct-slice-panel__overlay-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
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
  max-height: 70%;
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

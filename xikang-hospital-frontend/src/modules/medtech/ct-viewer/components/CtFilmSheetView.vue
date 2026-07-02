<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElAlert } from 'element-plus'
import type { CtVolumeMeta } from '@/shared/api/modules/ctViewer'
import { parseNrrdArrayBuffer } from '@/modules/medtech/ct-viewer/lib/nrrdToVtkImageData'
import {
  computeCtDisplayWindow,
  extractCoronalSlice,
  extractSliceZyx,
  windowToUint8,
} from '@/modules/medtech/ct-viewer/lib/volumeUtils'

const props = withDefaults(
  defineProps<{
    nrrdFetcher: () => Promise<ArrayBuffer>
    volumeMeta?: CtVolumeMeta | null
    windowCenter?: number
    windowWidth?: number
  }>(),
  {
    volumeMeta: null,
  },
)

const GRID_ROWS = 5
const GRID_COLS = 5
const GRID_SIZE = GRID_ROWS * GRID_COLS

const loading = ref(false)
const errorMessage = ref('')
const canvasRefs = ref<(HTMLCanvasElement | null)[]>(Array.from({ length: GRID_SIZE }, () => null))
const volume = ref<ReturnType<typeof parseNrrdArrayBuffer> | null>(null)

const effectiveWindowCenter = ref(40)
const effectiveWindowWidth = ref(400)

const sliceThickness = computed(() => {
  const spacing = props.volumeMeta?.spacing_xyz?.[2] ?? volume.value?.spacing?.[2]
  if (spacing == null) return '-'
  return `${spacing.toFixed(3)}mm`
})

function setCanvasRef(index: number, el: HTMLCanvasElement | null) {
  canvasRefs.value[index] = el
}

function drawSlice(
  canvas: HTMLCanvasElement | null,
  pixelData: Uint8ClampedArray,
  width: number,
  height: number,
) {
  if (!canvas) return
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const imageData = ctx.createImageData(width, height)
  for (let i = 0; i < pixelData.length; i += 1) {
    const offset = i * 4
    const g = pixelData[i]
    imageData.data[offset] = g
    imageData.data[offset + 1] = g
    imageData.data[offset + 2] = g
    imageData.data[offset + 3] = 255
  }
  ctx.putImageData(imageData, 0, 0)
}

function buildSlicePlan(zDim: number, yDim: number) {
  const axialCount = GRID_SIZE - 1
  const axialIndices: number[] = []
  if (axialCount <= 1 || zDim <= 1) {
    axialIndices.push(0)
  } else {
    for (let i = 0; i < axialCount; i += 1) {
      const ratio = i / (axialCount - 1)
      axialIndices.push(Math.round(ratio * (zDim - 1)))
    }
  }
  const scoutIndex = Math.floor(Math.max(yDim - 1, 0) / 2)
  return { axialIndices, scoutIndex }
}

function renderFilm() {
  if (!volume.value) return
  const { dimensions, scalars } = volume.value
  const [, yDim, zDim] = dimensions
  const volumeData = { scalars, dimensions }
  const { axialIndices, scoutIndex } = buildSlicePlan(zDim, yDim)

  const scoutSlice = extractCoronalSlice(volumeData, scoutIndex)
  const scoutGray = windowToUint8(
    scoutSlice.data,
    effectiveWindowCenter.value,
    effectiveWindowWidth.value,
  )
  drawSlice(canvasRefs.value[0], scoutGray, scoutSlice.width, scoutSlice.height)

  axialIndices.forEach((sliceIndex, offset) => {
    const canvasIndex = offset + 1
    const axialSlice = extractSliceZyx(volumeData, sliceIndex)
    const gray = windowToUint8(
      axialSlice.data,
      effectiveWindowCenter.value,
      effectiveWindowWidth.value,
    )
    drawSlice(canvasRefs.value[canvasIndex], gray, axialSlice.width, axialSlice.height)
  })
}

function applyWindowFromVolume(parsed: ReturnType<typeof parseNrrdArrayBuffer>) {
  if (props.windowCenter != null && props.windowWidth != null) {
    effectiveWindowCenter.value = props.windowCenter
    effectiveWindowWidth.value = props.windowWidth
    return
  }
  const { windowCenter, windowWidth } = computeCtDisplayWindow({
    min: props.volumeMeta?.min ?? null,
    max: props.volumeMeta?.max ?? null,
    scalars: parsed.scalars,
  })
  effectiveWindowCenter.value = windowCenter
  effectiveWindowWidth.value = windowWidth
}

async function loadVolume() {
  loading.value = true
  errorMessage.value = ''
  volume.value = null
  try {
    const buffer = await props.nrrdFetcher()
    const parsed = parseNrrdArrayBuffer(buffer)
    volume.value = parsed
    applyWindowFromVolume(parsed)
    await nextTick()
    renderFilm()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'CT 胶片加载失败'
  } finally {
    loading.value = false
  }
}

function cellOverlay(index: number) {
  if (!volume.value) return { series: '1', image: '-', total: '-' }
  const [, , zDim] = volume.value.dimensions
  if (index === 0) {
    return { series: 'Scout', image: 'Loc', total: '' }
  }
  const axialCount = GRID_SIZE - 1
  const axialIndex = index - 1
  const ratio = axialCount <= 1 ? 0 : axialIndex / (axialCount - 1)
  const sliceNo = Math.round(ratio * Math.max(zDim - 1, 0)) + 1
  return { series: '3', image: String(sliceNo), total: String(zDim) }
}

watch(
  () => [props.nrrdFetcher, props.windowCenter, props.windowWidth] as const,
  () => {
    void loadVolume()
  },
)

watch(
  () => props.volumeMeta,
  () => {
    if (!volume.value) return
    applyWindowFromVolume(volume.value)
    void nextTick().then(() => renderFilm())
  },
)

onMounted(() => {
  void loadVolume()
})
</script>

<template>
  <div v-loading="loading" class="ct-film-sheet">
    <ElAlert
      v-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      :closable="false"
      class="ct-film-sheet__alert"
    />

    <div class="ct-film-sheet__grid">
      <figure
        v-for="index in GRID_SIZE"
        :key="index - 1"
        class="ct-film-sheet__cell"
      >
        <canvas :ref="(el) => setCanvasRef(index - 1, el as HTMLCanvasElement | null)" />
        <figcaption class="ct-film-sheet__overlay">
          <span>Se: {{ cellOverlay(index - 1).series }}</span>
          <span>
            Im: {{ cellOverlay(index - 1).image }}
            <template v-if="cellOverlay(index - 1).total">/{{ cellOverlay(index - 1).total }}</template>
          </span>
          <span>WL: {{ effectiveWindowCenter }} WW: {{ effectiveWindowWidth }}</span>
          <span>{{ sliceThickness }}</span>
        </figcaption>
      </figure>
    </div>
  </div>
</template>

<style scoped>
.ct-film-sheet {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 320px;
  padding: 12px;
  background: #0b0f14;
  color: #e8edf5;
  border-radius: 10px;
}

.ct-film-sheet__alert {
  margin: 0;
}

.ct-film-sheet__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
}

.ct-film-sheet__cell {
  position: relative;
  margin: 0;
  overflow: hidden;
  aspect-ratio: 1;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: #000;
}

.ct-film-sheet__cell canvas {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.ct-film-sheet__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4px 5px;
  font-size: 9px;
  line-height: 1.25;
  color: #f3f7ff;
  text-shadow: 0 0 3px rgba(0, 0, 0, 0.9);
  pointer-events: none;
}

@media (max-width: 960px) {
  .ct-film-sheet__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>

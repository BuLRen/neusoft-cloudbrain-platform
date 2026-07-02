import { computed, nextTick, ref, type Ref } from 'vue'
import type { CtVolumeMeta } from '@/shared/api/modules/ctViewer'
import { parseNrrdArrayBuffer } from '@/modules/medtech/ct-viewer/lib/nrrdToVtkImageData'
import {
  computeCtDisplayWindow,
  extractCoronalSlice,
  extractSliceZyx,
  windowToUint8,
} from '@/modules/medtech/ct-viewer/lib/volumeUtils'

export const CT_FILM_GRID_ROWS = 5
export const CT_FILM_GRID_COLS = 5
export const CT_FILM_GRID_SIZE = CT_FILM_GRID_ROWS * CT_FILM_GRID_COLS

const DEFAULT_CELL_PX = 256

function resolveCanvasTargetSize(
  canvas: HTMLCanvasElement,
  fixedCellSize?: number,
): { width: number; height: number } {
  if (fixedCellSize && fixedCellSize > 0) {
    return { width: fixedCellSize, height: fixedCellSize }
  }

  const width = canvas.clientWidth || canvas.offsetWidth
  const height = canvas.clientHeight || canvas.offsetHeight
  if (width > 0 && height > 0) {
    return { width, height }
  }

  const parent = canvas.parentElement
  if (parent) {
    const parentWidth = parent.clientWidth || parent.offsetWidth
    const parentHeight = parent.clientHeight || parent.offsetHeight
    if (parentWidth > 0 && parentHeight > 0) {
      return { width: parentWidth, height: parentHeight }
    }
  }

  return { width: DEFAULT_CELL_PX, height: DEFAULT_CELL_PX }
}

function drawSlice(
  canvas: HTMLCanvasElement | null,
  pixelData: Uint8ClampedArray,
  width: number,
  height: number,
  fixedCellSize?: number,
) {
  if (!canvas) return

  const { width: targetW, height: targetH } = resolveCanvasTargetSize(canvas, fixedCellSize)
  canvas.width = targetW
  canvas.height = targetH

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const offscreen = document.createElement('canvas')
  offscreen.width = width
  offscreen.height = height
  const offCtx = offscreen.getContext('2d')
  if (!offCtx) return

  const imageData = offCtx.createImageData(width, height)
  for (let i = 0; i < pixelData.length; i += 1) {
    const offset = i * 4
    const g = pixelData[i]
    imageData.data[offset] = g
    imageData.data[offset + 1] = g
    imageData.data[offset + 2] = g
    imageData.data[offset + 3] = 255
  }
  offCtx.putImageData(imageData, 0, 0)

  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, targetW, targetH)

  const scale = Math.min(targetW / width, targetH / height)
  const drawW = width * scale
  const drawH = height * scale
  const offsetX = (targetW - drawW) / 2
  const offsetY = (targetH - drawH) / 2

  ctx.imageSmoothingEnabled = false
  ctx.drawImage(offscreen, offsetX, offsetY, drawW, drawH)
}

function buildSlicePlan(zDim: number, yDim: number) {
  const axialCount = CT_FILM_GRID_SIZE - 1
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

export function useCtFilmGrid(options: {
  nrrdFetcher: () => Promise<ArrayBuffer>
  volumeMeta: Ref<CtVolumeMeta | null | undefined>
  windowCenter?: Ref<number | undefined>
  windowWidth?: Ref<number | undefined>
  fixedCellSize?: Ref<number | undefined>
}) {
  const loading = ref(false)
  const errorMessage = ref('')
  const canvasRefs = ref<(HTMLCanvasElement | null)[]>(
    Array.from({ length: CT_FILM_GRID_SIZE }, () => null),
  )
  const volume = ref<ReturnType<typeof parseNrrdArrayBuffer> | null>(null)
  const effectiveWindowCenter = ref(40)
  const effectiveWindowWidth = ref(400)

  const sliceThickness = computed(() => {
    const spacing = options.volumeMeta.value?.spacing_xyz?.[2] ?? volume.value?.spacing?.[2]
    if (spacing == null) return '-'
    return `${spacing.toFixed(3)}mm`
  })

  function setCanvasRef(index: number, el: HTMLCanvasElement | null) {
    canvasRefs.value[index] = el
  }

  function applyWindowFromVolume(parsed: ReturnType<typeof parseNrrdArrayBuffer>) {
    if (options.windowCenter?.value != null && options.windowWidth?.value != null) {
      effectiveWindowCenter.value = options.windowCenter.value
      effectiveWindowWidth.value = options.windowWidth.value
      return
    }
    const { windowCenter, windowWidth } = computeCtDisplayWindow({
      min: options.volumeMeta.value?.min ?? null,
      max: options.volumeMeta.value?.max ?? null,
      scalars: parsed.scalars,
    })
    effectiveWindowCenter.value = windowCenter
    effectiveWindowWidth.value = windowWidth
  }

  async function renderFilm() {
    if (!volume.value) return
    await nextTick()
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => resolve())
    })

    const fixedCellSize = options.fixedCellSize?.value
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
    drawSlice(canvasRefs.value[0], scoutGray, scoutSlice.width, scoutSlice.height, fixedCellSize)

    axialIndices.forEach((sliceIndex, offset) => {
      const canvasIndex = offset + 1
      const axialSlice = extractSliceZyx(volumeData, sliceIndex)
      const gray = windowToUint8(
        axialSlice.data,
        effectiveWindowCenter.value,
        effectiveWindowWidth.value,
      )
      drawSlice(canvasRefs.value[canvasIndex], gray, axialSlice.width, axialSlice.height, fixedCellSize)
    })
  }

  function cellOverlay(index: number) {
    if (!volume.value) return { series: '1', image: '-', total: '-' }
    const [, , zDim] = volume.value.dimensions
    if (index === 0) {
      return { series: 'Scout', image: 'Loc', total: '' }
    }
    const axialCount = CT_FILM_GRID_SIZE - 1
    const axialIndex = index - 1
    const ratio = axialCount <= 1 ? 0 : axialIndex / (axialCount - 1)
    const sliceNo = Math.round(ratio * Math.max(zDim - 1, 0)) + 1
    return { series: '3', image: String(sliceNo), total: String(zDim) }
  }

  async function loadVolume(fetcher?: () => Promise<ArrayBuffer>) {
    const resolveNrrd = fetcher ?? options.nrrdFetcher
    loading.value = true
    errorMessage.value = ''
    volume.value = null
    try {
      const buffer = await resolveNrrd()
      const parsed = parseNrrdArrayBuffer(buffer)
      volume.value = parsed
      applyWindowFromVolume(parsed)
      await nextTick()
      await renderFilm()
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : 'CT 胶片加载失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refreshWindowAndRender() {
    if (!volume.value) return
    applyWindowFromVolume(volume.value)
    await renderFilm()
  }

  async function ensureRendered(fetcher?: () => Promise<ArrayBuffer>) {
    if (!volume.value) {
      await loadVolume(fetcher)
      return
    }
    await refreshWindowAndRender()
  }

  return {
    loading,
    errorMessage,
    canvasRefs,
    volume,
    effectiveWindowCenter,
    effectiveWindowWidth,
    sliceThickness,
    setCanvasRef,
    cellOverlay,
    loadVolume,
    ensureRendered,
    refreshWindowAndRender,
    renderFilm,
  }
}

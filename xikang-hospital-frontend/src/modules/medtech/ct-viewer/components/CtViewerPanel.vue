<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElOption,
  ElScrollbar,
  ElSelect,
  ElSlider,
  ElTag,
} from 'element-plus'
import {
  checkCtViewerHealth,
  downloadCtVolume,
  fetchCtVolumeNrrd,
  runCtFilter,
  uploadCtDicomFiles,
  uploadCtNrrdFile,
  type CtVolumeMeta,
} from '@/shared/api/modules/ctViewer'
import CtSliceViewPanel, { type CtViewTool } from '@/modules/medtech/ct-viewer/components/CtSliceViewPanel.vue'
import CtToolbar, { type CtLayoutMode } from '@/modules/medtech/ct-viewer/components/CtToolbar.vue'
import CtFilmstrip from '@/modules/medtech/ct-viewer/components/CtFilmstrip.vue'
import CtPatientInfoPanel, { type CtPatientInfoField } from '@/modules/medtech/ct-viewer/components/CtPatientInfoPanel.vue'
import VtkVolumeViewer from '@/modules/medtech/ct-viewer/components/VtkVolumeViewer.vue'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'
import { parseNrrdArrayBuffer } from '@/modules/medtech/ct-viewer/lib/nrrdToVtkImageData'
import { isSupportedVolumeFile, parseVolumeFile, type ParsedVolumeData } from '@/modules/medtech/ct-viewer/lib/volumeFileParser'
import {
  extractAxialThumbnail,
  extractCoronalSlice,
  extractSagittalSlice,
  extractSliceZyx,
  maskOverlayToRgb,
  windowToUint8,
} from '@/modules/medtech/ct-viewer/lib/volumeUtils'
import { computeRegionStats, type RegionStats } from '@/modules/medtech/ct-viewer/lib/imageInteraction'
import {
  getLesionsForAxialSlice,
  getLesionsForCoronalSlice,
  getLesionsForSagittalSlice,
} from '@/modules/medtech/ct-viewer/lib/lesionAnnotationUtils'
import type { CtLesionItem } from '@/shared/api/modules/ctViewer'

const METAL_MASK_FILTER_NAME = '金属伪影掩码 Metal Artifact Mask'
const LESION_DEMO_FILTER_NAME = '病灶分割演示 Lesion Segmentation (Demo)'
const METAL_OVERLAY_COLOR: [number, number, number] = [255, 80, 40]
// 与参考 CT 系统一致的病灶掩码高亮色：半透明红色直接叠加在病灶像素上，
// 不使用矩形框标注，避免遮挡病灶本身及周边解剖结构。
const LESION_OVERLAY_COLOR: [number, number, number] = [230, 45, 45]

const props = withDefaults(
  defineProps<{
    embedded?: boolean
    fullscreen?: boolean
    initialVolumeId?: string
    allowUpload?: boolean
    showSave?: boolean
    showTechBar?: boolean
    readOnly?: boolean
    nrrdFetcher?: (volumeId: string) => Promise<ArrayBuffer>
    patientName?: string
    patientFields?: CtPatientInfoField[]
  }>(),
  {
    embedded: false,
    fullscreen: false,
    initialVolumeId: '',
    allowUpload: true,
    showSave: true,
    showTechBar: true,
    readOnly: false,
    patientName: '',
    patientFields: () => [],
  },
)

const emit = defineEmits<{
  uploaded: [payload: { volumeId: string; sourceName: string; meta: CtVolumeMeta }]
  cleared: []
  'meta-updated': [meta: CtVolumeMeta | null]
  'toggle-report': []
}>()

const statusText = ref('正在检查 CT 影像服务…')
const backendReady = ref(false)
const algoReady = ref(false)
const isLoading = ref(false)

const sourceVolumeId = ref('')
const filteredVolumeId = ref('')
const originalVolume = ref<ParsedVolumeData | null>(null)
const filteredVolume = ref<ParsedVolumeData | null>(null)
const originalMeta = ref<CtVolumeMeta | null>(null)
const filteredMeta = ref<CtVolumeMeta | null>(null)
const filteredIsMask = ref(false)
const maskOverlayColor = ref<[number, number, number]>(METAL_OVERLAY_COLOR)
/** 'lesion' = AI 病灶掩码（红色半透明），'metal' = 金属伪影掩码，用于标签显示 */
const maskOverlayType = ref<'lesion' | 'metal'>('metal')
const segmentationLesions = ref<CtLesionItem[]>([])
const showLesionBoxAnnotations = ref(false)

const axialSlice = ref(0)
const coronalSlice = ref(0)
const sagittalSlice = ref(0)
const windowCenter = ref(50)
const windowWidth = ref(350)
const filterName = ref('无滤波')

const activeTool = ref<CtViewTool>('wlww')
const layoutMode = ref<CtLayoutMode>('quad')
const crosshairEnabled = ref(true)
const maskOverlayVisible = ref(true)
const roiResults = reactive<Record<'axial' | 'coronal' | 'sagittal', RegionStats | null>>({
  axial: null,
  coronal: null,
  sagittal: null,
})
const filmstripThumbnails = ref<string[]>([])
const filmstripPlaying = ref(false)
let filmstripTimer: number | undefined

const axialPanelRef = ref<InstanceType<typeof CtSliceViewPanel> | null>(null)
const coronalPanelRef = ref<InstanceType<typeof CtSliceViewPanel> | null>(null)
const sagittalPanelRef = ref<InstanceType<typeof CtSliceViewPanel> | null>(null)

const spatialSigma = ref(1.0)
const rangeSigma = ref(50.0)
const medianRadius = ref(1)
const iterations = ref(5)
const timeStep = ref(0.0625)
const conductance = ref(3.0)
const metalThresholdLower = ref(1000)
const metalThresholdUpper = ref(4000)
const metalGradientThreshold = ref(100)
const metalOpeningRadius = ref(1)
const metalClosingRadius = ref(2)
const metalMinComponentSize = ref(50)

const nrrdInput = ref<HTMLInputElement | null>(null)
const dicomInput = ref<HTMLInputElement | null>(null)

const axialCanvas = ref<HTMLCanvasElement | null>(null)
const coronalCanvas = ref<HTMLCanvasElement | null>(null)
const sagittalCanvas = ref<HTMLCanvasElement | null>(null)

const activeVolume = computed(() => filteredVolume.value ?? originalVolume.value)
const activeMeta = computed(() => filteredMeta.value ?? originalMeta.value)
const displayIsMask = computed(
  () =>
    filteredIsMask.value &&
    !!filteredVolume.value &&
    !showLesionBoxAnnotations.value &&
    maskOverlayVisible.value,
)
const effectiveAllowUpload = computed(() => props.allowUpload && !props.readOnly)
const showFilterSection = computed(() => !props.readOnly)
const effectiveShowSave = computed(() => props.showSave && !props.readOnly)

const xCount = computed(() => originalVolume.value?.dimensions?.[0] ?? 0)
const yCount = computed(() => originalVolume.value?.dimensions?.[1] ?? 0)
const zCount = computed(() => originalVolume.value?.dimensions?.[2] ?? 0)

const volumeDescription = computed(() => {
  const meta = originalMeta.value
  if (!meta) return '尚未加载影像'
  return `Size ${meta.size_xyz?.join(' x ') ?? '-'} | Spacing ${meta.spacing_xyz?.map((v) => v.toFixed(3)).join(', ') ?? '-'}`
})

const spacingX = computed(() => originalMeta.value?.spacing_xyz?.[0] ?? 0.7)
const spacingY = computed(() => originalMeta.value?.spacing_xyz?.[1] ?? 0.7)
const spacingZ = computed(() => originalMeta.value?.spacing_xyz?.[2] ?? spacingY.value)

const hasMaskOverlay = computed(() => filteredIsMask.value || showLesionBoxAnnotations.value)

/** 三视图十字线联动：axial/coronal/sagittal 归一化坐标（0~1），基于当前三个切片索引换算 */
const axialCrosshair = computed(() => {
  if (!crosshairEnabled.value || !xCount.value || !yCount.value) return null
  return {
    x: sagittalSlice.value / Math.max(xCount.value - 1, 1),
    y: coronalSlice.value / Math.max(yCount.value - 1, 1),
  }
})

const coronalCrosshair = computed(() => {
  if (!crosshairEnabled.value || !xCount.value || !zCount.value) return null
  return {
    x: sagittalSlice.value / Math.max(xCount.value - 1, 1),
    y: 1 - axialSlice.value / Math.max(zCount.value - 1, 1),
  }
})

const sagittalCrosshair = computed(() => {
  if (!crosshairEnabled.value || !yCount.value || !zCount.value) return null
  return {
    x: coronalSlice.value / Math.max(yCount.value - 1, 1),
    y: 1 - axialSlice.value / Math.max(zCount.value - 1, 1),
  }
})

const seriesLabel = computed(() => {
  const seriesId = originalMeta.value?.series_id
  if (!seriesId) return '1'
  return seriesId.slice(-4) || '1'
})

const technicalMetaLine = computed(() => {
  const meta = originalMeta.value
  if (!meta?.size_xyz?.length) return ''
  const spacing = meta.spacing_xyz?.map((v) => v.toFixed(2)).join(' × ') ?? '-'
  const matrix = meta.size_xyz.join(' × ')
  const files = meta.file_count ? ` · ${meta.file_count} 张` : ''
  return `体素间距 ${spacing} mm · 矩阵 ${matrix}${files}`
})

function applyWindowPreset(preset: 'brain' | 'soft' | 'bone') {
  if (preset === 'brain') {
    windowCenter.value = 40
    windowWidth.value = 80
  } else if (preset === 'soft') {
    windowCenter.value = 40
    windowWidth.value = 400
  } else {
    windowCenter.value = 400
    windowWidth.value = 1800
  }
}

const dataRange = computed(() => {
  const meta = activeMeta.value
  if (!meta) return '-'
  return `${meta.min?.toFixed?.(1) ?? meta.min} ~ ${meta.max?.toFixed?.(1) ?? meta.max}`
})

const showSpatial = computed(
  () => filterName.value === '高斯滤波 Gaussian' || filterName.value === '双边滤波 Bilateral',
)
const showRange = computed(() => filterName.value === '双边滤波 Bilateral')
const showMedian = computed(() => filterName.value === '中值滤波 Median')
const showIter = computed(
  () =>
    filterName.value === '曲率流平滑 Curvature Flow' ||
    filterName.value === '各向异性扩散 Anisotropic Diffusion',
)
const showConductance = computed(() => filterName.value === '各向异性扩散 Anisotropic Diffusion')
const showMetal = computed(() => filterName.value === METAL_MASK_FILTER_NAME)
const showLesionDemo = computed(() => filterName.value === LESION_DEMO_FILTER_NAME)
const maskOverlayLabel = computed(() =>
  maskOverlayType.value === 'lesion' ? '病灶掩码' : '金属掩码',
)
/** 当前显示的像素级掩码是否为 AI 病灶分割结果（区别于金属伪影演示掩码） */
const isLesionMaskActive = computed(
  () => displayIsMask.value && maskOverlayType.value === 'lesion',
)

/** 三维体渲染：CT 底图 + 半透明掩码叠加（与 2D 病灶可视化一致） */
const volume3dMaskOverlay = computed(() =>
  displayIsMask.value && filteredVolume.value ? filteredVolume.value : null,
)

const normalizedMaskColor = computed((): [number, number, number] => {
  const [r, g, b] = maskOverlayColor.value
  return [r / 255, g / 255, b / 255]
})

function getFilterParams() {
  return {
    spatial_sigma: spatialSigma.value,
    range_sigma: rangeSigma.value,
    median_radius: medianRadius.value,
    iterations: iterations.value,
    time_step: timeStep.value,
    conductance: conductance.value,
    metal_threshold_lower: metalThresholdLower.value,
    metal_threshold_upper: metalThresholdUpper.value,
    metal_gradient_threshold: metalGradientThreshold.value,
    metal_opening_radius: metalOpeningRadius.value,
    metal_closing_radius: metalClosingRadius.value,
    metal_min_component_size: metalMinComponentSize.value,
  }
}

function resetVolumeState() {
  sourceVolumeId.value = ''
  originalVolume.value = null
  originalMeta.value = null
  clearSegmentationOverlay()
}

async function checkBackend() {
  try {
    const health = await checkCtViewerHealth()
    backendReady.value = Boolean(health.ok)
    algoReady.value = Boolean(health.algoReady)
    if (backendReady.value && algoReady.value) {
      statusText.value = 'CT 影像服务已就绪，可加载 DICOM / NRRD。'
    } else if (backendReady.value) {
      statusText.value = 'CT 影像服务已连接，但图像算法 worker 未就绪，请启动 ct-viewer-algo。'
    } else {
      statusText.value = 'CT 影像服务未连接，请启动 ct-viewer-service 与网关。'
    }
  } catch {
    backendReady.value = false
    algoReady.value = false
    statusText.value = 'CT 影像服务未连接，请启动 ct-viewer-service、ct-viewer-algo 与 gateway。'
  }
}

function applyLocalVolumeData(
  volumeData: ParsedVolumeData,
  meta: CtVolumeMeta | null | undefined,
  target: 'original' | 'filtered',
) {
  if (target === 'original') {
    originalVolume.value = volumeData
    const [sx, sy, sz] = volumeData.spacing
    const [dx, dy, dz] = volumeData.dimensions
    if (meta) {
      originalMeta.value = {
        ...meta,
        spacing_xyz: meta.spacing_xyz?.length ? meta.spacing_xyz : [sx, sy, sz],
        size_xyz: meta.size_xyz?.length ? meta.size_xyz : [dx, dy, dz],
      }
    } else {
      originalMeta.value = { spacing_xyz: [sx, sy, sz], size_xyz: [dx, dy, dz] }
    }
  } else {
    filteredVolume.value = volumeData
  }
}

async function loadVolumeById(volumeId: string, target: 'original' | 'filtered') {
  const fetchNrrd = props.nrrdFetcher ?? fetchCtVolumeNrrd
  const arrayBuffer = await fetchNrrd(volumeId)
  const volumeData = parseNrrdArrayBuffer(arrayBuffer)
  if (target === 'original') {
    applyLocalVolumeData(volumeData, originalMeta.value, 'original')
  } else {
    filteredVolume.value = volumeData
  }
}

function applyDefaultWindow(meta: CtVolumeMeta) {
  const min = meta.min
  const max = meta.max
  if (min == null || max == null) return
  if (max - min > 800) {
    windowCenter.value = 40
    windowWidth.value = 400
  } else {
    windowCenter.value = Math.round((min + max) / 2)
    windowWidth.value = Math.max(Math.round(max - min), 1)
  }
}

async function loadBoundVolume(volumeId: string, sourceName?: string) {
  if (!volumeId) return
  const isNewVolume = volumeId !== sourceVolumeId.value
  isLoading.value = true
  try {
    if (isNewVolume) {
      clearSegmentationOverlay()
    }
    sourceVolumeId.value = volumeId
    originalMeta.value = sourceName ? { source_name: sourceName } : null
    await loadVolumeById(volumeId, 'original')
    resetSlicePositions()
    statusText.value = sourceName ? `已加载：${sourceName}` : `已加载 volume ${volumeId}`
  } catch (error) {
    statusText.value = `加载失败：${error instanceof Error ? error.message : '未知错误'}`
  } finally {
    isLoading.value = false
  }
}

async function handleNrrdUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const [file] = input.files ?? []
  if (!file) return

  if (!isSupportedVolumeFile(file.name)) {
    statusText.value = '仅支持 .nrrd / .nii / .nii.gz 格式的体数据文件'
    input.value = ''
    return
  }

  isLoading.value = true
  try {
    statusText.value = '正在上传并本地解析影像（无需再从服务器下载）…'
    const [result, volumeData] = await Promise.all([
      uploadCtNrrdFile(file),
      parseVolumeFile(file),
    ])
    clearSegmentationOverlay()
    sourceVolumeId.value = result.volume_id
    applyLocalVolumeData(volumeData, result.meta, 'original')
    applyDefaultWindow(result.meta)
    resetSlicePositions()
    statusText.value = `加载成功：${file.name}`
    emit('uploaded', { volumeId: result.volume_id, sourceName: file.name, meta: result.meta })
  } catch (error) {
    statusText.value = `加载失败：${error instanceof Error ? error.message : '未知错误'}`
  } finally {
    isLoading.value = false
    input.value = ''
  }
}

async function handleDicomFolderUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (!files.length) return

  isLoading.value = true
  try {
    statusText.value = '正在上传 DICOM 并在服务器转换（转换完成后需下载 NRRD）…'
    const result = await uploadCtDicomFiles(files)
    const sourceName = `DICOM (${result.meta.series_id || 'series'}, ${result.meta.file_count || files.length} files)`
    clearSegmentationOverlay()
    sourceVolumeId.value = result.volume_id
    originalMeta.value = result.meta
    statusText.value = '正在从服务器下载转换后的影像…'
    await loadVolumeById(sourceVolumeId.value, 'original')
    applyDefaultWindow(result.meta)
    resetSlicePositions()
    statusText.value = `DICOM 加载成功（Series: ${result.meta.series_id || '-'}, 文件数: ${result.meta.file_count || files.length}）`
    emit('uploaded', { volumeId: result.volume_id, sourceName, meta: result.meta })
  } catch (error) {
    statusText.value = `DICOM 加载失败：${error instanceof Error ? error.message : '未知错误'}`
  } finally {
    isLoading.value = false
    input.value = ''
  }
}

function handleClearUpload() {
  resetVolumeState()
  statusText.value = '已清除当前影像，请重新上传。'
  emit('cleared')
}

async function applyFilter() {
  if (!sourceVolumeId.value) {
    statusText.value = '请先加载数据。'
    return
  }
  isLoading.value = true
  try {
    const params: Record<string, unknown> = { ...getFilterParams() }
    if (filterName.value === LESION_DEMO_FILTER_NAME) {
      params.source_name = originalMeta.value?.source_name ?? sourceVolumeId.value
    }
    const result = await runCtFilter(sourceVolumeId.value, filterName.value, params)
    filteredVolumeId.value = result.volume_id
    filteredIsMask.value = Boolean(result.is_mask)
    maskOverlayColor.value = filterName.value === LESION_DEMO_FILTER_NAME
      ? LESION_OVERLAY_COLOR
      : METAL_OVERLAY_COLOR
    maskOverlayType.value = filterName.value === LESION_DEMO_FILTER_NAME ? 'lesion' : 'metal'
    filteredMeta.value = result.meta
    await loadVolumeById(filteredVolumeId.value, 'filtered')
    applyDefaultWindow(result.meta)
    statusText.value = result.message
  } catch (error) {
    statusText.value = `滤波失败：${error instanceof Error ? error.message : '未知错误'}`
  } finally {
    isLoading.value = false
  }
}

async function loadSegmentationMask(maskVolumeId: string) {
  if (!sourceVolumeId.value || !maskVolumeId) return
  isLoading.value = true
  try {
    filteredVolumeId.value = maskVolumeId
    filteredIsMask.value = true
    // 不清空 segmentationLesions / showLesionBoxAnnotations：
    // 像素级掩码叠加与病灶列表数据相互独立，掩码用于精确展示病灶轮廓，
    // 病灶列表数据仍用于右侧面板统计、3D 预览与点击定位。
    maskOverlayColor.value = LESION_OVERLAY_COLOR
    maskOverlayType.value = 'lesion'
    await loadVolumeById(maskVolumeId, 'filtered')
    filteredMeta.value = { ...(filteredMeta.value ?? {}), is_mask: true }
    statusText.value = '已加载 AI 病灶分割掩码'
  } catch (error) {
    statusText.value = `加载分割掩码失败：${error instanceof Error ? error.message : '未知错误'}`
  } finally {
    isLoading.value = false
  }
}

function applySegmentationLesions(lesions: CtLesionItem[]) {
  segmentationLesions.value = lesions ?? []
  // 病灶可视化默认以像素级红色掩码叠加为主（见 loadSegmentationMask），
  // 与参考 CT 系统的标注风格一致；矩形标注框会遮挡病灶本身，默认不启用。
  showLesionBoxAnnotations.value = false
  if (segmentationLesions.value.length) {
    statusText.value = `AI 病灶标注：${segmentationLesions.value.length} 处`
    void nextTick().then(() => refresh2DViews())
  }
}

function navigateToLesion(lesion: { sliceIndex?: number; centroidXyz?: number[]; bbox?: number[] }) {
  // 优先用 bbox 中心体素坐标定位三个平面（bbox: [z0,y0,x0,z1,y1,x1]，与后端 D/H/W 轴一致）
  if (lesion.bbox && lesion.bbox.length >= 6) {
    const [z0, y0, x0, z1, y1, x1] = lesion.bbox
    axialSlice.value = Math.max(0, Math.min(zCount.value - 1, Math.round((z0 + z1) / 2)))
    coronalSlice.value = Math.max(0, Math.min(yCount.value - 1, Math.round((y0 + y1) / 2)))
    sagittalSlice.value = Math.max(0, Math.min(xCount.value - 1, Math.round((x0 + x1) / 2)))
    return
  }
  // 降级：只有 sliceIndex（轴状切片 Z 体素索引）时仅跳轴状层面
  if (typeof lesion.sliceIndex === 'number') {
    axialSlice.value = Math.max(0, Math.min(zCount.value - 1, lesion.sliceIndex))
  }
}

function clearSegmentationOverlay() {
  filteredVolumeId.value = ''
  filteredVolume.value = null
  filteredMeta.value = null
  filteredIsMask.value = false
  maskOverlayColor.value = METAL_OVERLAY_COLOR
  maskOverlayType.value = 'metal'
  segmentationLesions.value = []
  showLesionBoxAnnotations.value = false
  if (!sourceVolumeId.value) {
    statusText.value = '已清除当前影像，请重新上传。'
  }
}

function resetSlicePositions() {
  axialSlice.value = Math.floor(Math.max(zCount.value - 1, 0) / 2)
  coronalSlice.value = Math.floor(Math.max(yCount.value - 1, 0) / 2)
  sagittalSlice.value = Math.floor(Math.max(xCount.value - 1, 0) / 2)
}

function drawSlice(
  canvas: HTMLCanvasElement | null,
  pixelData: Uint8ClampedArray,
  width: number,
  height: number,
  channels: number,
) {
  if (!canvas) return
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const imageData = ctx.createImageData(width, height)
  if (channels === 1) {
    for (let i = 0; i < pixelData.length; i += 1) {
      const offset = i * 4
      const g = pixelData[i]
      imageData.data[offset] = g
      imageData.data[offset + 1] = g
      imageData.data[offset + 2] = g
      imageData.data[offset + 3] = 255
    }
  } else {
    for (let i = 0; i < width * height; i += 1) {
      const src = i * 3
      const dst = i * 4
      imageData.data[dst] = pixelData[src]
      imageData.data[dst + 1] = pixelData[src + 1]
      imageData.data[dst + 2] = pixelData[src + 2]
      imageData.data[dst + 3] = 255
    }
  }
  ctx.putImageData(imageData, 0, 0)
}

function renderPlane(
  canvas: HTMLCanvasElement | null,
  extractor: (volume: NonNullable<typeof originalVolume.value>, index: number) => { data: Float32Array; width: number; height: number },
  index: number,
) {
  if (!originalVolume.value || !activeVolume.value) return

  if (displayIsMask.value && filteredVolume.value) {
    const baseSlice = extractor(originalVolume.value, index)
    const maskSlice = extractor(filteredVolume.value, index)
    const rgb = maskOverlayToRgb(
      baseSlice.data,
      maskSlice.data,
      windowCenter.value,
      windowWidth.value,
      0.65,
      maskOverlayColor.value,
    )
    drawSlice(canvas, rgb, baseSlice.width, baseSlice.height, 3)
    return
  }

  const slice = extractor(activeVolume.value, index)
  const gray = windowToUint8(slice.data, windowCenter.value, windowWidth.value)
  drawSlice(canvas, gray, slice.width, slice.height, 1)
}

/** 是否应显示 AI 病灶标注框（三个平面共用的开关条件） */
const lesionBoxesEnabled = computed(
  () => showLesionBoxAnnotations.value && maskOverlayVisible.value && segmentationLesions.value.length > 0,
)

/**
 * 病灶标注框改为在 CtSliceViewPanel 的叠加层（与显示分辨率一致）绘制，而不是直接画在
 * 体素分辨率的底图画布上——否则冠状/矢状图按物理比例非等比缩放后，标注框线宽/文字会被拉花。
 */
const axialLesionBoxes = computed(() =>
  lesionBoxesEnabled.value
    ? getLesionsForAxialSlice(segmentationLesions.value, axialSlice.value, xCount.value, yCount.value)
    : [],
)
const coronalLesionBoxes = computed(() =>
  lesionBoxesEnabled.value
    ? getLesionsForCoronalSlice(segmentationLesions.value, coronalSlice.value, xCount.value, zCount.value)
    : [],
)
const sagittalLesionBoxes = computed(() =>
  lesionBoxesEnabled.value
    ? getLesionsForSagittalSlice(segmentationLesions.value, sagittalSlice.value, yCount.value, zCount.value)
    : [],
)

function refresh2DViews() {
  if (!originalVolume.value) return
  renderPlane(axialCanvas.value, extractSliceZyx, axialSlice.value)
  renderPlane(coronalCanvas.value, extractCoronalSlice, coronalSlice.value)
  renderPlane(sagittalCanvas.value, extractSagittalSlice, sagittalSlice.value)
}

function clampIndex(value: number, count: number) {
  if (!count) return 0
  return Math.max(0, Math.min(count - 1, value))
}

function handleAxialWheel(delta: number) {
  axialSlice.value = clampIndex(axialSlice.value + delta, zCount.value)
}

function handleCoronalWheel(delta: number) {
  coronalSlice.value = clampIndex(coronalSlice.value + delta, yCount.value)
}

function handleSagittalWheel(delta: number) {
  sagittalSlice.value = clampIndex(sagittalSlice.value + delta, xCount.value)
}

function handleWindowDelta(dx: number, dy: number) {
  windowWidth.value = Math.max(1, Math.round(windowWidth.value + dx * 2))
  windowCenter.value = Math.round(windowCenter.value - dy * 2)
}

function handleAxialCrosshairSet(point: { x: number; y: number }) {
  sagittalSlice.value = Math.round(point.x * Math.max(xCount.value - 1, 0))
  coronalSlice.value = Math.round(point.y * Math.max(yCount.value - 1, 0))
}

function handleCoronalCrosshairSet(point: { x: number; y: number }) {
  sagittalSlice.value = Math.round(point.x * Math.max(xCount.value - 1, 0))
  axialSlice.value = Math.round((1 - point.y) * Math.max(zCount.value - 1, 0))
}

function handleSagittalCrosshairSet(point: { x: number; y: number }) {
  coronalSlice.value = Math.round(point.x * Math.max(yCount.value - 1, 0))
  axialSlice.value = Math.round((1 - point.y) * Math.max(zCount.value - 1, 0))
}

function handleRoiRequest(plane: 'axial' | 'coronal' | 'sagittal', rect: { x0: number; y0: number; x1: number; y1: number }) {
  if (!originalVolume.value) return
  let slice: { data: Float32Array; width: number; height: number }
  let colSpacing: number
  let rowSpacing: number

  if (plane === 'axial') {
    slice = extractSliceZyx(originalVolume.value, axialSlice.value)
    colSpacing = spacingX.value
    rowSpacing = spacingY.value
  } else if (plane === 'coronal') {
    slice = extractCoronalSlice(originalVolume.value, coronalSlice.value)
    colSpacing = spacingX.value
    rowSpacing = spacingZ.value
  } else {
    slice = extractSagittalSlice(originalVolume.value, sagittalSlice.value)
    colSpacing = spacingY.value
    rowSpacing = spacingZ.value
  }

  roiResults[plane] = computeRegionStats(slice.data, slice.width, slice.height, rect, colSpacing, rowSpacing)
}

function handleResetView() {
  axialPanelRef.value?.resetView()
  coronalPanelRef.value?.resetView()
  sagittalPanelRef.value?.resetView()
}

function handleToggleMaskOverlay() {
  maskOverlayVisible.value = !maskOverlayVisible.value
  void nextTick().then(() => refresh2DViews())
}

function handlePrint() {
  window.print()
}

function regenerateFilmstripThumbnails() {
  if (!originalVolume.value || !zCount.value) {
    filmstripThumbnails.value = []
    return
  }
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const thumbs: string[] = []
  for (let i = 0; i < zCount.value; i += 1) {
    const thumb = extractAxialThumbnail(originalVolume.value, i, windowCenter.value, windowWidth.value, 64)
    canvas.width = thumb.width
    canvas.height = thumb.height
    const imageData = ctx.createImageData(thumb.width, thumb.height)
    for (let p = 0; p < thumb.data.length; p += 1) {
      const g = thumb.data[p]
      const offset = p * 4
      imageData.data[offset] = g
      imageData.data[offset + 1] = g
      imageData.data[offset + 2] = g
      imageData.data[offset + 3] = 255
    }
    ctx.putImageData(imageData, 0, 0)
    thumbs.push(canvas.toDataURL('image/png'))
  }
  filmstripThumbnails.value = thumbs
}

let thumbnailDebounceTimer: number | undefined
function scheduleThumbnailRegen() {
  window.clearTimeout(thumbnailDebounceTimer)
  thumbnailDebounceTimer = window.setTimeout(regenerateFilmstripThumbnails, 300)
}

function toggleFilmstripPlay() {
  filmstripPlaying.value = !filmstripPlaying.value
  if (filmstripPlaying.value) {
    filmstripTimer = window.setInterval(() => {
      axialSlice.value = zCount.value ? (axialSlice.value + 1) % zCount.value : 0
    }, 200)
  } else if (filmstripTimer != null) {
    window.clearInterval(filmstripTimer)
    filmstripTimer = undefined
  }
}

function saveCurrentSlicePng() {
  const canvas = axialCanvas.value
  if (!canvas) return
  canvas.toBlob((blob) => {
    if (!blob) return
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `axial_slice_${axialSlice.value + 1}.png`
    anchor.click()
    URL.revokeObjectURL(url)
  })
}

async function saveFilteredVolume(format: string) {
  const volumeId = filteredVolumeId.value || sourceVolumeId.value
  if (!volumeId) return
  await downloadCtVolume(volumeId, format)
}

watch(
  () => props.initialVolumeId,
  (volumeId) => {
    if (volumeId) {
      void loadBoundVolume(volumeId)
    }
  },
)

watch([originalVolume, filteredVolume, axialSlice, coronalSlice, sagittalSlice, windowCenter, windowWidth, segmentationLesions, showLesionBoxAnnotations, maskOverlayVisible], async () => {
  await nextTick()
  refresh2DViews()
})

watch([originalVolume, windowCenter, windowWidth], () => scheduleThumbnailRegen())

watch(
  originalMeta,
  (meta) => {
    emit('meta-updated', meta)
  },
  { immediate: true },
)

onMounted(async () => {
  await checkBackend()
  if (props.initialVolumeId) {
    await loadBoundVolume(props.initialVolumeId)
  }
})

onBeforeUnmount(() => {
  if (filmstripTimer != null) window.clearInterval(filmstripTimer)
  window.clearTimeout(thumbnailDebounceTimer)
})

defineExpose({
  resetVolumeState,
  loadBoundVolume,
  loadSegmentationMask,
  applySegmentationLesions,
  navigateToLesion,
  clearSegmentationOverlay,
})
</script>

<template>
  <div
    class="ct-viewer-panel ct-imaging-theme"
    :class="{
      'ct-viewer-panel--embedded': embedded,
      'ct-viewer-panel--fullscreen': fullscreen,
    }"
  >
    <input
      ref="nrrdInput"
      class="hidden-input"
      type="file"
      accept=".nrrd,.nii,.gz,application/gzip,application/octet-stream"
      @change="handleNrrdUpload"
    />
    <input ref="dicomInput" class="hidden-input" type="file" multiple webkitdirectory directory @change="handleDicomFolderUpload" />

    <div v-if="showTechBar && !embedded && technicalMetaLine" class="ct-viewer-panel__tech-bar">
      <span class="ct-viewer-panel__tech-dot" />
      <span>{{ technicalMetaLine }}</span>
      <span v-if="originalMeta?.source_name" class="ct-viewer-panel__tech-source">
        来源：{{ originalMeta.source_name }}
      </span>
    </div>

    <div class="ct-viewer-layout">
      <aside class="ct-viewer-sidebar">
        <ElScrollbar>
          <CtPatientInfoPanel
            v-if="patientName || patientFields.length"
            :patient-name="patientName"
            :fields="patientFields"
          />

          <section v-if="effectiveAllowUpload" class="ct-sidebar-section">
            <h3 class="ct-sidebar-section__title">数据加载</h3>
            <div class="ct-btn-stack">
              <ElButton type="primary" class="ct-btn-primary" :loading="isLoading" :disabled="!backendReady" @click="nrrdInput?.click()">
                上传 NRRD / NIfTI
              </ElButton>
              <ElButton class="ct-btn-secondary" :loading="isLoading" :disabled="!backendReady || !algoReady" @click="dicomInput?.click()">
                上传 DICOM 文件夹
              </ElButton>
              <ElButton v-if="sourceVolumeId" class="ct-btn-ghost" @click="handleClearUpload">清除当前影像</ElButton>
            </div>
            <p class="ct-sidebar-status">{{ statusText }}</p>
            <p v-if="originalMeta" class="ct-sidebar-meta">{{ volumeDescription }}</p>
          </section>

          <section class="ct-sidebar-section">
            <h3 class="ct-sidebar-section__title">三视图定位</h3>
            <div class="ct-control-block">
              <div class="ct-control-row">
                <span class="ct-control-label">轴状 Axial</span>
                <span class="ct-control-value">{{ axialSlice + 1 }} / {{ zCount || '-' }}</span>
              </div>
              <ElSlider v-model="axialSlice" :min="0" :max="Math.max(zCount - 1, 0)" :disabled="!zCount" />
            </div>
            <div class="ct-control-block">
              <div class="ct-control-row">
                <span class="ct-control-label">冠状 Coronal</span>
                <span class="ct-control-value">{{ coronalSlice + 1 }} / {{ yCount || '-' }}</span>
              </div>
              <ElSlider v-model="coronalSlice" :min="0" :max="Math.max(yCount - 1, 0)" :disabled="!yCount" />
            </div>
            <div class="ct-control-block">
              <div class="ct-control-row">
                <span class="ct-control-label">矢状 Sagittal</span>
                <span class="ct-control-value">{{ sagittalSlice + 1 }} / {{ xCount || '-' }}</span>
              </div>
              <ElSlider v-model="sagittalSlice" :min="0" :max="Math.max(xCount - 1, 0)" :disabled="!xCount" />
            </div>
          </section>

          <section class="ct-sidebar-section">
            <h3 class="ct-sidebar-section__title">窗宽 / 窗位</h3>
            <div class="ct-wl-presets">
              <button type="button" class="ct-preset-chip" @click="applyWindowPreset('brain')">脑窗</button>
              <button type="button" class="ct-preset-chip" @click="applyWindowPreset('soft')">软组织</button>
              <button type="button" class="ct-preset-chip" @click="applyWindowPreset('bone')">骨窗</button>
            </div>
            <ElForm label-position="top" class="ct-wl-form">
              <ElFormItem label="窗位 Center">
                <ElInputNumber v-model="windowCenter" :min="-3000" :max="3000" controls-position="right" />
              </ElFormItem>
              <ElFormItem label="窗宽 Width">
                <ElInputNumber v-model="windowWidth" :min="1" :max="6000" controls-position="right" />
              </ElFormItem>
            </ElForm>
          </section>

          <section v-if="showFilterSection" class="ct-sidebar-section">
            <h3 class="ct-sidebar-section__title">滤波器</h3>
            <ElForm label-position="top">
              <ElFormItem label="选择滤波器">
                <ElSelect v-model="filterName" class="full-width">
                  <ElOption label="无滤波" value="无滤波" />
                  <ElOption label="高斯滤波 Gaussian" value="高斯滤波 Gaussian" />
                  <ElOption label="双边滤波 Bilateral" value="双边滤波 Bilateral" />
                  <ElOption label="中值滤波 Median" value="中值滤波 Median" />
                  <ElOption label="曲率流平滑 Curvature Flow" value="曲率流平滑 Curvature Flow" />
                  <ElOption label="各向异性扩散 Anisotropic Diffusion" value="各向异性扩散 Anisotropic Diffusion" />
                  <ElOption :label="METAL_MASK_FILTER_NAME" :value="METAL_MASK_FILTER_NAME" />
                  <ElOption :label="LESION_DEMO_FILTER_NAME" :value="LESION_DEMO_FILTER_NAME" />
                </ElSelect>
              </ElFormItem>

              <ElFormItem v-if="showLesionDemo">
                <p class="ct-sidebar-hint">演示用合成病灶掩码，结果可复现；正式分割请使用页头「AI 病灶分割」。</p>
              </ElFormItem>

              <ElFormItem v-if="showSpatial" label="空间 Sigma">
                <ElInputNumber v-model="spatialSigma" :min="0.1" :max="10" :step="0.1" controls-position="right" />
              </ElFormItem>
              <ElFormItem v-if="showRange" label="灰度 Sigma">
                <ElInputNumber v-model="rangeSigma" :min="1" :max="300" :step="0.5" controls-position="right" />
              </ElFormItem>
              <ElFormItem v-if="showMedian" label="中值半径">
                <ElInputNumber v-model="medianRadius" :min="1" :max="8" :step="1" controls-position="right" />
              </ElFormItem>
              <template v-if="showIter">
                <ElFormItem label="迭代次数">
                  <ElInputNumber v-model="iterations" :min="1" :max="30" :step="1" controls-position="right" />
                </ElFormItem>
                <ElFormItem label="Time Step">
                  <ElInputNumber v-model="timeStep" :min="0.001" :max="0.25" :step="0.001" controls-position="right" />
                </ElFormItem>
              </template>
              <ElFormItem v-if="showConductance" label="Conductance">
                <ElInputNumber v-model="conductance" :min="0.1" :max="20" :step="0.1" controls-position="right" />
              </ElFormItem>
              <template v-if="showMetal">
                <ElDivider content-position="left">金属伪影掩码参数</ElDivider>
                <ElFormItem label="阈值下限 HU">
                  <ElInputNumber v-model="metalThresholdLower" :min="-1000" :max="5000" controls-position="right" />
                </ElFormItem>
                <ElFormItem label="阈值上限 HU">
                  <ElInputNumber v-model="metalThresholdUpper" :min="-1000" :max="5000" controls-position="right" />
                </ElFormItem>
                <ElFormItem label="梯度阈值">
                  <ElInputNumber v-model="metalGradientThreshold" :min="0" :max="2000" controls-position="right" />
                </ElFormItem>
                <ElFormItem label="开运算半径">
                  <ElInputNumber v-model="metalOpeningRadius" :min="0" :max="5" controls-position="right" />
                </ElFormItem>
                <ElFormItem label="闭运算半径">
                  <ElInputNumber v-model="metalClosingRadius" :min="0" :max="10" controls-position="right" />
                </ElFormItem>
                <ElFormItem label="最小连通域体素数">
                  <ElInputNumber v-model="metalMinComponentSize" :min="0" :max="10000" controls-position="right" />
                </ElFormItem>
              </template>
            </ElForm>
            <ElButton class="full-width ct-btn-accent" type="primary" :disabled="!sourceVolumeId || !algoReady" :loading="isLoading" @click="applyFilter">
              应用滤波
            </ElButton>
          </section>

          <section v-if="effectiveShowSave" class="ct-sidebar-section">
            <h3 class="ct-sidebar-section__title">保存结果</h3>
            <div class="ct-btn-stack">
              <ElButton class="ct-btn-secondary" :disabled="!zCount" @click="saveCurrentSlicePng">保存轴状切片 PNG</ElButton>
              <ElButton class="ct-btn-secondary" :disabled="!sourceVolumeId" @click="saveFilteredVolume('nrrd')">保存体数据 NRRD</ElButton>
              <ElButton class="ct-btn-secondary" :disabled="!sourceVolumeId" @click="saveFilteredVolume('nii.gz')">保存体数据 NIfTI</ElButton>
            </div>
          </section>

          <div v-if="!embedded" class="ct-sidebar-footer">
            <ElTag :type="backendReady && algoReady ? 'success' : 'warning'" effect="dark" size="small">
              {{ backendReady && algoReady ? '服务已就绪' : backendReady ? '算法未就绪' : '服务未连接' }}
            </ElTag>
            <ElTag v-if="activeMeta" effect="plain" size="small">数据范围 {{ dataRange }}</ElTag>
          </div>
        </ElScrollbar>
      </aside>

      <section class="ct-viewer-workspace">
        <CtToolbar
          :tool="activeTool"
          :layout-mode="layoutMode"
          :crosshair-enabled="crosshairEnabled"
          :mask-overlay-enabled="maskOverlayVisible"
          :has-mask-overlay="hasMaskOverlay"
          @update:tool="(value) => (activeTool = value)"
          @update:layout-mode="(value) => (layoutMode = value)"
          @toggle-crosshair="crosshairEnabled = !crosshairEnabled"
          @toggle-mask-overlay="handleToggleMaskOverlay"
          @reset-view="handleResetView"
          @export="saveCurrentSlicePng"
          @print="handlePrint"
          @toggle-report="emit('toggle-report')"
        />

        <div class="ct-quad-grid" :class="`ct-quad-grid--${layoutMode}`">
          <CtSliceViewPanel
            ref="axialPanelRef"
            class="ct-quad-cell ct-quad-cell--axial"
            title="轴状图 Axial"
            plane="axial"
            :slice-index="axialSlice"
            :slice-total="zCount"
            :window-center="windowCenter"
            :window-width="windowWidth"
            :row-spacing-mm="spacingY"
            :col-spacing-mm="spacingX"
            :natural-width="xCount"
            :natural-height="yCount"
            :series-label="seriesLabel"
            :tool="activeTool"
            :crosshair="axialCrosshair"
            :roi-result="roiResults.axial"
            :lesion-boxes="axialLesionBoxes"
            @wheel-slice="handleAxialWheel"
            @window-delta="handleWindowDelta"
            @crosshair-set="handleAxialCrosshairSet"
            @roi-request="(rect) => handleRoiRequest('axial', rect)"
          >
            <template v-if="isLesionMaskActive || showLesionBoxAnnotations" #header-extra>
              <ElTag type="danger" size="small" effect="dark">AI 病灶标注</ElTag>
            </template>
            <canvas ref="axialCanvas" />
          </CtSliceViewPanel>

          <CtSliceViewPanel
            ref="coronalPanelRef"
            class="ct-quad-cell ct-quad-cell--coronal"
            title="冠状图 Coronal"
            plane="coronal"
            :slice-index="coronalSlice"
            :slice-total="yCount"
            :window-center="windowCenter"
            :window-width="windowWidth"
            :row-spacing-mm="spacingZ"
            :col-spacing-mm="spacingX"
            :natural-width="xCount"
            :natural-height="zCount"
            :series-label="seriesLabel"
            :tool="activeTool"
            :crosshair="coronalCrosshair"
            :roi-result="roiResults.coronal"
            :lesion-boxes="coronalLesionBoxes"
            @wheel-slice="handleCoronalWheel"
            @window-delta="handleWindowDelta"
            @crosshair-set="handleCoronalCrosshairSet"
            @roi-request="(rect) => handleRoiRequest('coronal', rect)"
          >
            <canvas ref="coronalCanvas" />
          </CtSliceViewPanel>

          <CtSliceViewPanel
            ref="sagittalPanelRef"
            class="ct-quad-cell ct-quad-cell--sagittal"
            title="矢状图 Sagittal"
            plane="sagittal"
            :slice-index="sagittalSlice"
            :slice-total="xCount"
            :window-center="windowCenter"
            :window-width="windowWidth"
            :row-spacing-mm="spacingZ"
            :col-spacing-mm="spacingY"
            :natural-width="yCount"
            :natural-height="zCount"
            :series-label="seriesLabel"
            :tool="activeTool"
            :crosshair="sagittalCrosshair"
            :roi-result="roiResults.sagittal"
            :lesion-boxes="sagittalLesionBoxes"
            @wheel-slice="handleSagittalWheel"
            @window-delta="handleWindowDelta"
            @crosshair-set="handleSagittalCrosshairSet"
            @roi-request="(rect) => handleRoiRequest('sagittal', rect)"
          >
            <canvas ref="sagittalCanvas" />
          </CtSliceViewPanel>

          <div class="ct-quad-cell ct-quad-cell--volume volume-panel">
            <header class="volume-panel__header">
              <div class="volume-panel__title-row">
                <span class="volume-panel__title">三维体渲染</span>
                <ElTag v-if="displayIsMask" type="warning" size="small">{{ maskOverlayLabel }}</ElTag>
              </div>
              <div class="volume-panel__mode">
                <span>体渲染 VR</span>
              </div>
            </header>

            <div class="volume-panel__body">
              <div class="volume-panel__orient-cube">
                <span class="cube-face cube-face--r">R</span>
                <span class="cube-face cube-face--a">A</span>
                <span class="cube-face cube-face--s">S</span>
              </div>

              <VtkVolumeViewer
                :volume-data="originalVolume"
                :window-center="windowCenter"
                :window-width="windowWidth"
                :data-min="originalMeta?.min ?? 0"
                :data-max="originalMeta?.max ?? 4096"
                :mask-volume-data="volume3dMaskOverlay"
                :mask-overlay-enabled="displayIsMask"
                :mask-color="normalizedMaskColor"
                :mask-data-max="filteredMeta?.max ?? 1"
                :mask-opacity="0.72"
              />
            </div>
          </div>
        </div>

        <CtFilmstrip
          v-if="zCount"
          :thumbnails="filmstripThumbnails"
          :current-index="axialSlice"
          :total="zCount"
          :playing="filmstripPlaying"
          @update:current-index="(value) => (axialSlice = value)"
          @toggle-play="toggleFilmstripPlay"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.ct-viewer-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: calc(100vh - var(--shell-gap) * 2 - 120px);
  background: var(--ct-bg);
  color: var(--ct-text);
}

.ct-viewer-panel--embedded {
  min-height: 640px;
  border-radius: var(--ct-radius-lg);
  overflow: hidden;
}

.ct-viewer-panel--fullscreen {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.ct-viewer-panel--fullscreen .ct-viewer-layout {
  flex: 1;
  min-height: 0;
}

.ct-viewer-panel--fullscreen .ct-viewer-workspace {
  min-height: 0;
}

.ct-viewer-panel__tech-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding: 6px 14px;
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-text-dim);
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-bg-soft);
}

.ct-viewer-panel__tech-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ct-accent);
  box-shadow: 0 0 8px var(--ct-accent-glow);
}

.ct-viewer-panel__tech-source {
  margin-inline-start: auto;
  color: var(--ct-text-muted);
}

.hidden-input {
  display: none;
}

.ct-viewer-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 0;
  min-height: 0;
  flex: 1;
}

.ct-viewer-sidebar {
  min-height: 0;
  border-inline-end: 1px solid var(--ct-border);
  background: var(--ct-surface);
  overflow: hidden;
}

.ct-sidebar-section {
  padding: 14px 14px 4px;
  border-block-end: 1px solid var(--ct-border);
}

.ct-sidebar-section__title {
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ct-text-muted);
}

.ct-btn-stack {
  display: grid;
  gap: 8px;
}

.ct-btn-stack :deep(.el-button) {
  margin-left: 0;
}

.ct-btn-primary {
  font-weight: 600;
}

.ct-btn-secondary {
  --el-button-bg-color: var(--ct-surface-elevated);
  --el-button-border-color: var(--ct-border-strong);
  --el-button-text-color: var(--ct-text);
  --el-button-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-button-hover-border-color: var(--ct-accent);
  --el-button-hover-text-color: var(--ct-accent);
}

.ct-btn-ghost {
  --el-button-bg-color: transparent;
  --el-button-border-color: transparent;
  --el-button-text-color: var(--ct-text-dim);
  --el-button-hover-text-color: var(--ct-danger);
}

.ct-btn-accent {
  margin-top: 8px;
  font-weight: 600;
}

.ct-sidebar-status {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ct-text-muted);
}

.ct-sidebar-meta {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ct-text-dim);
}

.ct-sidebar-hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--ct-text-dim);
}

.ct-control-block + .ct-control-block {
  margin-top: 14px;
}

.ct-control-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.ct-control-label {
  font-size: 12px;
  color: var(--ct-text-muted);
}

.ct-control-value {
  font-size: 11px;
  font-family: var(--ct-font-mono);
  color: var(--ct-accent);
}

.ct-wl-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.ct-preset-chip {
  padding: 4px 10px;
  border: 1px solid var(--ct-border-strong);
  border-radius: 999px;
  background: var(--ct-surface-elevated);
  color: var(--ct-text-muted);
  font-size: 11px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.ct-preset-chip:hover {
  border-color: var(--ct-accent);
  color: var(--ct-accent);
  background: var(--ct-accent-soft);
}

.ct-wl-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 10px;
}

.full-width {
  width: 100%;
}

.ct-viewer-sidebar :deep(.el-input-number) {
  width: 100%;
}

.ct-viewer-sidebar :deep(.el-slider) {
  margin-inline: 2px;
}

.ct-viewer-sidebar :deep(.el-divider__text) {
  background: var(--ct-surface);
  color: var(--ct-text-dim);
  font-size: 11px;
}

.ct-sidebar-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 14px 16px;
}

.ct-viewer-workspace {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--ct-bg);
}

.ct-quad-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  grid-template-areas:
    'axial coronal'
    'sagittal volume';
  gap: 8px;
  padding: 8px;
}

.ct-quad-cell--axial { grid-area: axial; }
.ct-quad-cell--coronal { grid-area: coronal; }
.ct-quad-cell--sagittal { grid-area: sagittal; }
.ct-quad-cell--volume { grid-area: volume; }

.ct-quad-cell {
  min-height: 0;
  min-width: 0;
}

.ct-quad-grid--axial {
  grid-template-columns: 1fr;
  grid-template-rows: 1fr;
  grid-template-areas: 'axial';
}

.ct-quad-grid--axial .ct-quad-cell:not(.ct-quad-cell--axial) {
  display: none;
}

.ct-quad-grid--3d {
  grid-template-columns: 1fr;
  grid-template-rows: 1fr;
  grid-template-areas: 'volume';
}

.ct-quad-grid--3d .ct-quad-cell:not(.ct-quad-cell--volume) {
  display: none;
}

.volume-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-panel);
  overflow: hidden;
}

.volume-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-surface);
}

.volume-panel__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.volume-panel__title {
  font-size: 13px;
  font-weight: 600;
}

.volume-panel__mode {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid var(--ct-border-strong);
  color: var(--ct-text-muted);
  background: var(--ct-surface-elevated);
}

.volume-panel__body {
  position: relative;
  flex: 1;
  min-height: 0;
}

.volume-panel__orient-cube {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 3;
  width: 52px;
  height: 52px;
  border: 1px solid var(--ct-border-strong);
  border-radius: 8px;
  background: rgba(11, 14, 20, 0.75);
  backdrop-filter: blur(6px);
}

.cube-face {
  position: absolute;
  font-size: 9px;
  font-weight: 700;
  color: var(--ct-accent);
}

.cube-face--r {
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
}

.cube-face--a {
  bottom: 4px;
  left: 50%;
  transform: translateX(-50%);
}

.cube-face--s {
  top: 4px;
  left: 4px;
}

@media (max-width: 1180px) {
  .ct-viewer-layout {
    grid-template-columns: 1fr;
  }

  .ct-viewer-workspace {
    min-height: 900px;
  }

  .ct-quad-grid,
  .ct-quad-grid--axial,
  .ct-quad-grid--3d {
    grid-template-columns: 1fr;
    grid-template-rows: repeat(4, minmax(240px, 1fr));
    grid-template-areas:
      'axial'
      'coronal'
      'sagittal'
      'volume';
  }

  .ct-wl-form {
    grid-template-columns: 1fr;
  }
}
</style>

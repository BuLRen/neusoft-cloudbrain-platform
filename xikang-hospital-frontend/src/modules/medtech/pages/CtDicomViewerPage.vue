<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
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
import PageHeader from '@/shared/components/PageHeader.vue'
import {
  checkCtViewerHealth,
  downloadCtVolume,
  fetchCtVolumeNrrd,
  runCtFilter,
  uploadCtDicomFiles,
  uploadCtNrrdFile,
  type CtVolumeMeta,
} from '@/shared/api/modules/ctViewer'
import VtkVolumeViewer from '@/modules/medtech/ct-viewer/components/VtkVolumeViewer.vue'
import { parseNrrdArrayBuffer } from '@/modules/medtech/ct-viewer/lib/nrrdToVtkImageData'
import {
  extractCoronalSlice,
  extractSagittalSlice,
  extractSliceZyx,
  maskOverlayToRgb,
  windowToUint8,
} from '@/modules/medtech/ct-viewer/lib/volumeUtils'

const METAL_MASK_FILTER_NAME = '金属伪影掩码 Metal Artifact Mask'

const statusText = ref('正在检查 CT 影像服务…')
const backendReady = ref(false)
const algoReady = ref(false)
const isLoading = ref(false)

const sourceVolumeId = ref('')
const filteredVolumeId = ref('')
const originalVolume = ref<ReturnType<typeof parseNrrdArrayBuffer> | null>(null)
const filteredVolume = ref<ReturnType<typeof parseNrrdArrayBuffer> | null>(null)
const originalMeta = ref<CtVolumeMeta | null>(null)
const filteredMeta = ref<CtVolumeMeta | null>(null)
const filteredIsMask = ref(false)

const axialSlice = ref(0)
const coronalSlice = ref(0)
const sagittalSlice = ref(0)
const windowCenter = ref(50)
const windowWidth = ref(350)
const filterName = ref('无滤波')

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
const displayIsMask = computed(() => filteredIsMask.value && !!filteredVolume.value)

const xCount = computed(() => originalVolume.value?.dimensions?.[0] ?? 0)
const yCount = computed(() => originalVolume.value?.dimensions?.[1] ?? 0)
const zCount = computed(() => originalVolume.value?.dimensions?.[2] ?? 0)

const volumeDescription = computed(() => {
  const meta = originalMeta.value
  if (!meta) return '尚未加载影像'
  return `Size ${meta.size_xyz?.join(' x ') ?? '-'} | Spacing ${meta.spacing_xyz?.map((v) => v.toFixed(3)).join(', ') ?? '-'}`
})

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

async function loadVolumeById(volumeId: string, target: 'original' | 'filtered') {
  const arrayBuffer = await fetchCtVolumeNrrd(volumeId)
  const volumeData = parseNrrdArrayBuffer(arrayBuffer)
  if (target === 'original') {
    originalVolume.value = volumeData
  } else {
    filteredVolume.value = volumeData
  }
}

function applyDefaultWindow(meta: CtVolumeMeta) {
  const min = meta.min
  const max = meta.max
  if (min == null || max == null) return
  // CT 软组织窗：覆盖常见 HU 范围，避免默认 50/350 在宽动态范围数据上全黑
  if (max - min > 800) {
    windowCenter.value = 40
    windowWidth.value = 400
  } else {
    windowCenter.value = Math.round((min + max) / 2)
    windowWidth.value = Math.max(Math.round(max - min), 1)
  }
}

async function handleNrrdUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const [file] = input.files ?? []
  if (!file) return

  isLoading.value = true
  try {
    const result = await uploadCtNrrdFile(file)
    sourceVolumeId.value = result.volume_id
    filteredVolumeId.value = ''
    originalMeta.value = result.meta
    filteredMeta.value = null
    filteredVolume.value = null
    filteredIsMask.value = false
    await loadVolumeById(sourceVolumeId.value, 'original')
    applyDefaultWindow(result.meta)
    resetSlicePositions()
    statusText.value = `加载成功：${file.name}`
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
    const result = await uploadCtDicomFiles(files)
    sourceVolumeId.value = result.volume_id
    filteredVolumeId.value = ''
    originalMeta.value = result.meta
    filteredMeta.value = null
    filteredVolume.value = null
    filteredIsMask.value = false
    await loadVolumeById(sourceVolumeId.value, 'original')
    applyDefaultWindow(result.meta)
    resetSlicePositions()
    statusText.value = `DICOM 加载成功（Series: ${result.meta.series_id || '-'}, 文件数: ${result.meta.file_count || files.length}）`
  } catch (error) {
    statusText.value = `DICOM 加载失败：${error instanceof Error ? error.message : '未知错误'}`
  } finally {
    isLoading.value = false
    input.value = ''
  }
}

async function applyFilter() {
  if (!sourceVolumeId.value) {
    statusText.value = '请先加载数据。'
    return
  }
  isLoading.value = true
  try {
    const result = await runCtFilter(sourceVolumeId.value, filterName.value, getFilterParams())
    filteredVolumeId.value = result.volume_id
    filteredIsMask.value = Boolean(result.is_mask)
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
    const rgb = maskOverlayToRgb(baseSlice.data, maskSlice.data, windowCenter.value, windowWidth.value)
    drawSlice(canvas, rgb, baseSlice.width, baseSlice.height, 3)
    return
  }

  const slice = extractor(activeVolume.value, index)
  const gray = windowToUint8(slice.data, windowCenter.value, windowWidth.value)
  drawSlice(canvas, gray, slice.width, slice.height, 1)
}

function refresh2DViews() {
  if (!originalVolume.value) return
  renderPlane(axialCanvas.value, extractSliceZyx, axialSlice.value)
  renderPlane(coronalCanvas.value, extractCoronalSlice, coronalSlice.value)
  renderPlane(sagittalCanvas.value, extractSagittalSlice, sagittalSlice.value)
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

watch([originalVolume, filteredVolume, axialSlice, coronalSlice, sagittalSlice, windowCenter, windowWidth], async () => {
  await nextTick()
  refresh2DViews()
})

onMounted(async () => {
  await checkBackend()
})
</script>

<template>
  <div class="ct-viewer-page">
    <input ref="nrrdInput" class="hidden-input" type="file" accept=".nrrd,.nii,.nii.gz" @change="handleNrrdUpload" />
    <input ref="dicomInput" class="hidden-input" type="file" multiple webkitdirectory directory @change="handleDicomFolderUpload" />

    <PageHeader
      title="CT 影像查看"
      description="上传 DICOM 文件夹或 NRRD/NIfTI，查看 2D 三视图与 3D 体渲染，并应用滤波。"
      :eyebrow="`医技管理 · ${volumeDescription}`"
    >
      <template #actions>
        <el-tag :type="backendReady && algoReady ? 'success' : 'warning'" effect="dark">
          {{ backendReady && algoReady ? '服务已就绪' : backendReady ? '算法未就绪' : '服务未连接' }}
        </el-tag>
        <el-tag v-if="activeMeta" effect="plain">数据范围 {{ dataRange }}</el-tag>
      </template>
    </PageHeader>

    <div class="ct-viewer-layout">
      <aside class="ct-viewer-sidebar">
        <el-scrollbar>
          <el-card class="panel-card" shadow="never">
            <template #header>加载数据</template>
            <div class="button-stack">
              <el-button type="primary" :loading="isLoading" :disabled="!backendReady" @click="nrrdInput?.click()">
                上传 NRRD / NIfTI
              </el-button>
              <el-button :loading="isLoading" :disabled="!backendReady || !algoReady" @click="dicomInput?.click()">
                上传 DICOM 文件夹
              </el-button>
            </div>
            <el-alert class="status-alert" :title="statusText" type="info" :closable="false" show-icon />
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>三视图定位</template>
            <div class="control-block">
              <div class="control-label">轴状 Axial：{{ axialSlice + 1 }} / {{ zCount || '-' }}</div>
              <el-slider v-model="axialSlice" :min="0" :max="Math.max(zCount - 1, 0)" :disabled="!zCount" />
            </div>
            <div class="control-block">
              <div class="control-label">冠状 Coronal：{{ coronalSlice + 1 }} / {{ yCount || '-' }}</div>
              <el-slider v-model="coronalSlice" :min="0" :max="Math.max(yCount - 1, 0)" :disabled="!yCount" />
            </div>
            <div class="control-block">
              <div class="control-label">矢状 Sagittal：{{ sagittalSlice + 1 }} / {{ xCount || '-' }}</div>
              <el-slider v-model="sagittalSlice" :min="0" :max="Math.max(xCount - 1, 0)" :disabled="!xCount" />
            </div>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>窗宽 / 窗位</template>
            <el-form label-position="top">
              <el-form-item label="窗位 Center">
                <el-input-number v-model="windowCenter" :min="-3000" :max="3000" controls-position="right" />
              </el-form-item>
              <el-form-item label="窗宽 Width">
                <el-input-number v-model="windowWidth" :min="1" :max="6000" controls-position="right" />
              </el-form-item>
            </el-form>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>滤波器</template>
            <el-form label-position="top">
              <el-form-item label="选择滤波器">
                <el-select v-model="filterName" class="full-width">
                  <el-option label="无滤波" value="无滤波" />
                  <el-option label="高斯滤波 Gaussian" value="高斯滤波 Gaussian" />
                  <el-option label="双边滤波 Bilateral" value="双边滤波 Bilateral" />
                  <el-option label="中值滤波 Median" value="中值滤波 Median" />
                  <el-option label="曲率流平滑 Curvature Flow" value="曲率流平滑 Curvature Flow" />
                  <el-option label="各向异性扩散 Anisotropic Diffusion" value="各向异性扩散 Anisotropic Diffusion" />
                  <el-option :label="METAL_MASK_FILTER_NAME" :value="METAL_MASK_FILTER_NAME" />
                </el-select>
              </el-form-item>

              <el-form-item v-if="showSpatial" label="空间 Sigma">
                <el-input-number v-model="spatialSigma" :min="0.1" :max="10" :step="0.1" controls-position="right" />
              </el-form-item>
              <el-form-item v-if="showRange" label="灰度 Sigma">
                <el-input-number v-model="rangeSigma" :min="1" :max="300" :step="0.5" controls-position="right" />
              </el-form-item>
              <el-form-item v-if="showMedian" label="中值半径">
                <el-input-number v-model="medianRadius" :min="1" :max="8" :step="1" controls-position="right" />
              </el-form-item>
              <template v-if="showIter">
                <el-form-item label="迭代次数">
                  <el-input-number v-model="iterations" :min="1" :max="30" :step="1" controls-position="right" />
                </el-form-item>
                <el-form-item label="Time Step">
                  <el-input-number v-model="timeStep" :min="0.001" :max="0.25" :step="0.001" controls-position="right" />
                </el-form-item>
              </template>
              <el-form-item v-if="showConductance" label="Conductance">
                <el-input-number v-model="conductance" :min="0.1" :max="20" :step="0.1" controls-position="right" />
              </el-form-item>
              <template v-if="showMetal">
                <el-divider content-position="left">金属伪影掩码参数</el-divider>
                <el-form-item label="阈值下限 HU">
                  <el-input-number v-model="metalThresholdLower" :min="-1000" :max="5000" controls-position="right" />
                </el-form-item>
                <el-form-item label="阈值上限 HU">
                  <el-input-number v-model="metalThresholdUpper" :min="-1000" :max="5000" controls-position="right" />
                </el-form-item>
                <el-form-item label="梯度阈值">
                  <el-input-number v-model="metalGradientThreshold" :min="0" :max="2000" controls-position="right" />
                </el-form-item>
                <el-form-item label="开运算半径">
                  <el-input-number v-model="metalOpeningRadius" :min="0" :max="5" controls-position="right" />
                </el-form-item>
                <el-form-item label="闭运算半径">
                  <el-input-number v-model="metalClosingRadius" :min="0" :max="10" controls-position="right" />
                </el-form-item>
                <el-form-item label="最小连通域体素数">
                  <el-input-number v-model="metalMinComponentSize" :min="0" :max="10000" controls-position="right" />
                </el-form-item>
              </template>
            </el-form>
            <el-button class="full-width" type="success" :disabled="!sourceVolumeId || !algoReady" :loading="isLoading" @click="applyFilter">
              应用滤波
            </el-button>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>保存结果</template>
            <div class="button-stack">
              <el-button :disabled="!zCount" @click="saveCurrentSlicePng">保存轴状切片 PNG</el-button>
              <el-button :disabled="!sourceVolumeId" @click="saveFilteredVolume('nrrd')">保存体数据 NRRD</el-button>
              <el-button :disabled="!sourceVolumeId" @click="saveFilteredVolume('nii.gz')">保存体数据 NIfTI</el-button>
            </div>
          </el-card>
        </el-scrollbar>
      </aside>

      <section class="ct-viewer-workspace">
        <div class="view-grid">
          <el-card class="view-card" shadow="never">
            <template #header>
              <div class="card-title"><span>轴状图 Axial</span><el-tag size="small">Z</el-tag></div>
            </template>
            <canvas ref="axialCanvas"></canvas>
          </el-card>
          <el-card class="view-card" shadow="never">
            <template #header>
              <div class="card-title"><span>冠状图 Coronal</span><el-tag size="small">Y</el-tag></div>
            </template>
            <canvas ref="coronalCanvas"></canvas>
          </el-card>
          <el-card class="view-card" shadow="never">
            <template #header>
              <div class="card-title"><span>矢状图 Sagittal</span><el-tag size="small">X</el-tag></div>
            </template>
            <canvas ref="sagittalCanvas"></canvas>
          </el-card>
        </div>

        <el-card class="volume-card" shadow="never">
          <template #header>
            <div class="card-title">
              <span>三维体渲染</span>
              <el-tag v-if="displayIsMask" type="warning" size="small">金属掩码</el-tag>
            </div>
          </template>
          <VtkVolumeViewer
            :volume-data="activeVolume"
            :window-center="windowCenter"
            :window-width="windowWidth"
            :is-mask="displayIsMask"
            :data-min="activeMeta?.min ?? 0"
            :data-max="activeMeta?.max ?? 1"
          />
        </el-card>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ct-viewer-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-height: calc(100vh - var(--shell-gap) * 2 - 120px);
}

.hidden-input {
  display: none;
}

.ct-viewer-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: var(--space-4);
  min-height: 0;
  flex: 1;
}

.ct-viewer-sidebar {
  min-height: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  background: var(--color-sidebar);
  overflow: hidden;
}

.panel-card {
  margin: 12px;
  border-radius: 14px;
}

.button-stack {
  display: grid;
  gap: 10px;
}

.button-stack :deep(.el-button) {
  margin-left: 0;
}

.status-alert {
  margin-top: 12px;
}

.control-block + .control-block {
  margin-top: 12px;
}

.control-label {
  font-size: 13px;
  color: var(--color-text-soft);
}

.full-width {
  width: 100%;
}

.ct-viewer-sidebar :deep(.el-input-number) {
  width: 100%;
}

.ct-viewer-sidebar :deep(.el-slider) {
  margin-inline: 4px;
}

.ct-viewer-workspace {
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(220px, 36%) minmax(360px, 1fr);
  gap: var(--space-4);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  background: var(--color-surface);
}

.view-grid {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.view-card,
.volume-card {
  min-height: 0;
  border-radius: 16px;
  overflow: hidden;
}

.view-card :deep(.el-card__body),
.volume-card :deep(.el-card__body) {
  height: calc(100% - 54px);
  padding: 10px;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-weight: 600;
}

.view-card canvas {
  width: 100%;
  height: 100%;
  background:
    linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px),
    #05070d;
  background-size: 20px 20px;
  border-radius: 12px;
  object-fit: contain;
}

@media (max-width: 1180px) {
  .ct-viewer-layout {
    grid-template-columns: 1fr;
  }

  .ct-viewer-workspace {
    min-height: 900px;
  }

  .view-grid {
    grid-template-columns: 1fr;
  }
}
</style>

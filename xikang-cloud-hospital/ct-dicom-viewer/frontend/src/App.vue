<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import VtkVolumeViewer from './components/VtkVolumeViewer.vue'
import { fetchVolumeNrrd, getSaveVolumeUrl, runFilter, uploadDicomFiles, uploadNrrdFile, checkBackendHealth } from './lib/apiClient'
import { parseNrrdArrayBuffer } from './lib/nrrdToVtkImageData'
import {
  extractCoronalSlice,
  extractSagittalSlice,
  extractSliceZyx,
  maskOverlayToRgb,
  windowToUint8,
} from './lib/volumeUtils'

const METAL_MASK_FILTER_NAME = '金属伪影掩码 Metal Artifact Mask'

const statusText = ref('请先启动后端服务，再加载 NRRD 或 DICOM。')
const backendReady = ref(false)
const isLoading = ref(false)

const sourceVolumeId = ref('')
const filteredVolumeId = ref('')
const originalVolume = ref(null)
const filteredVolume = ref(null)
const originalMeta = ref(null)
const filteredMeta = ref(null)
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

const nrrdInput = ref(null)
const dicomInput = ref(null)
const axialCanvas = ref(null)
const coronalCanvas = ref(null)
const sagittalCanvas = ref(null)

const activeVolume = computed(() => filteredVolume.value ?? originalVolume.value)
const activeMeta = computed(() => filteredMeta.value ?? originalMeta.value)
const displayIsMask = computed(() => filteredIsMask.value && !!filteredVolume.value)

const xCount = computed(() => originalVolume.value?.dimensions?.[0] ?? 0)
const yCount = computed(() => originalVolume.value?.dimensions?.[1] ?? 0)
const zCount = computed(() => {
  if (!originalVolume.value) return 0
  return originalVolume.value.dimensions[2]
})

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

function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

async function checkBackend() {
  try {
    await checkBackendHealth()
    backendReady.value = true
    statusText.value = '后端连接成功，可加载 DICOM / NRRD。'
  } catch {
    backendReady.value = false
    statusText.value =
      '后端未连接。请在 backend 目录启动服务：python server.py'
  }
}

async function loadVolumeById(volumeId, target) {
  const arrayBuffer = await fetchVolumeNrrd(volumeId)
  const volumeData = parseNrrdArrayBuffer(arrayBuffer)
  if (target === 'original') {
    originalVolume.value = volumeData
  } else {
    filteredVolume.value = volumeData
  }
}

async function handleNrrdUpload(event) {
  const [file] = event.target.files ?? []
  if (!file) {
    return
  }

  isLoading.value = true
  try {
    const result = await uploadNrrdFile(file)
    sourceVolumeId.value = result.volume_id
    filteredVolumeId.value = ''
    originalMeta.value = result.meta
    filteredMeta.value = null
    filteredVolume.value = null
    filteredIsMask.value = false
    await loadVolumeById(sourceVolumeId.value, 'original')
    resetSlicePositions()
    statusText.value = `加载成功：${file.name}`
  } catch (error) {
    statusText.value = `加载失败：${error.message}`
  } finally {
    isLoading.value = false
    event.target.value = ''
  }
}

async function handleDicomFolderUpload(event) {
  const files = Array.from(event.target.files ?? [])
  if (!files.length) {
    return
  }

  isLoading.value = true
  try {
    const result = await uploadDicomFiles(files)
    sourceVolumeId.value = result.volume_id
    filteredVolumeId.value = ''
    originalMeta.value = result.meta
    filteredMeta.value = null
    filteredVolume.value = null
    filteredIsMask.value = false
    await loadVolumeById(sourceVolumeId.value, 'original')
    resetSlicePositions()
    statusText.value = `DICOM 加载成功（Series: ${result.meta.series_id || '-'}, 文件数: ${result.meta.file_count || files.length}）`
  } catch (error) {
    statusText.value = `DICOM 加载失败：${error.message}`
  } finally {
    isLoading.value = false
    event.target.value = ''
  }
}

async function applyFilter() {
  if (!sourceVolumeId.value) {
    statusText.value = '请先加载数据。'
    return
  }
  isLoading.value = true
  try {
    const result = await runFilter(sourceVolumeId.value, filterName.value, getFilterParams())
    filteredVolumeId.value = result.volume_id
    filteredIsMask.value = Boolean(result.is_mask)
    filteredMeta.value = result.meta
    await loadVolumeById(filteredVolumeId.value, 'filtered')
    statusText.value = result.message
  } catch (error) {
    statusText.value = `滤波失败：${error.message}`
  } finally {
    isLoading.value = false
  }
}

function resetSlicePositions() {
  axialSlice.value = Math.floor(Math.max(zCount.value - 1, 0) / 2)
  coronalSlice.value = Math.floor(Math.max(yCount.value - 1, 0) / 2)
  sagittalSlice.value = Math.floor(Math.max(xCount.value - 1, 0) / 2)
}

function drawSlice(canvas, pixelData, width, height, channels) {
  if (!canvas) return
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
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

function renderPlane(canvas, extractor, index) {
  if (!originalVolume.value || !activeVolume.value) return

  if (displayIsMask.value) {
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
    downloadBlob(blob, `axial_slice_${axialSlice.value + 1}.png`)
  })
}

function saveFilteredVolume(format) {
  const volumeId = filteredVolumeId.value || sourceVolumeId.value
  if (!volumeId) return
  const anchor = document.createElement('a')
  anchor.href = getSaveVolumeUrl(volumeId, format)
  anchor.download = format === 'nrrd' ? 'volume.nrrd' : 'volume.nii.gz'
  anchor.click()
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
  <main class="app-shell">
    <input ref="nrrdInput" class="hidden-input" type="file" accept=".nrrd,.nii,.nii.gz" @change="handleNrrdUpload" />
    <input ref="dicomInput" class="hidden-input" type="file" multiple webkitdirectory directory @change="handleDicomFolderUpload" />

    <el-container class="layout">
      <el-header class="topbar">
        <div>
          <h1>CT 影像工作台</h1>
          <p>{{ volumeDescription }}</p>
        </div>
        <div class="topbar-actions">
          <el-tag :type="backendReady ? 'success' : 'danger'" effect="dark">
            {{ backendReady ? '后端已连接' : '后端未连接' }}
          </el-tag>
          <el-tag v-if="activeMeta" effect="plain">数据范围 {{ dataRange }}</el-tag>
        </div>
      </el-header>

      <el-container class="main-container">
        <el-aside class="sidebar" width="360px">
          <el-scrollbar>
            <el-card class="panel-card" shadow="never">
              <template #header>加载数据</template>
              <div class="button-stack">
                <el-button type="primary" :loading="isLoading" @click="nrrdInput?.click()">
                  上传 NRRD / NIfTI
                </el-button>
                <el-button :loading="isLoading" @click="dicomInput?.click()">
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
              <el-button class="full-width" type="success" :disabled="!sourceVolumeId" :loading="isLoading" @click="applyFilter">
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
        </el-aside>

        <el-main class="workspace">
          <section class="view-grid">
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
          </section>

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
        </el-main>
      </el-container>
    </el-container>
  </main>
</template>

<style scoped>
.app-shell,
.layout,
.main-container {
  height: 100%;
}

.hidden-input {
  display: none;
}

.topbar {
  height: 76px;
  border-bottom: 1px solid var(--el-border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.18), transparent 32%),
    rgba(20, 23, 31, 0.92);
}

.topbar h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: 0.2px;
}

.topbar p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.topbar-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.sidebar {
  border-right: 1px solid var(--el-border-color);
  background: #111827;
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
  color: var(--el-text-color-secondary);
}

.full-width {
  width: 100%;
}

.workspace {
  min-height: 0;
  padding: 14px;
  display: grid;
  grid-template-rows: minmax(260px, 38%) minmax(360px, 1fr);
  gap: 14px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.12), transparent 38%),
    #0b1020;
}

.view-grid {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
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

.volume-card {
  min-height: 0;
}

@media (max-width: 1180px) {
  .main-container {
    display: block;
    overflow: auto;
  }

  .sidebar {
    width: 100% !important;
    height: auto;
  }

  .workspace {
    min-height: 900px;
  }

  .view-grid {
    grid-template-columns: 1fr;
  }
}
</style>

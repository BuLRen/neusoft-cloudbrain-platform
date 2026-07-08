<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import '@kitware/vtk.js/Rendering/Profiles/Volume'
import vtkColorTransferFunction from '@kitware/vtk.js/Rendering/Core/ColorTransferFunction'
import vtkGenericRenderWindow from '@kitware/vtk.js/Rendering/Misc/GenericRenderWindow'
import vtkPiecewiseFunction from '@kitware/vtk.js/Common/DataModel/PiecewiseFunction'
import vtkVolume from '@kitware/vtk.js/Rendering/Core/Volume'
import vtkVolumeMapper from '@kitware/vtk.js/Rendering/Core/VolumeMapper'

interface VolumeData {
  vtkImageData?: unknown
}

const props = withDefaults(
  defineProps<{
    volumeData?: VolumeData | null
    windowCenter?: number
    windowWidth?: number
    /** 仅渲染掩码体（无 CT 底图），用于 AI 面板局部 3D 预览 */
    isMask?: boolean
    dataMin?: number
    dataMax?: number
    /** isMask=true 或 maskOverlayEnabled=true 时的高亮颜色（0~1 归一化 RGB） */
    maskColor?: [number, number, number]
    /** 与 CT 同网格的掩码体，叠加在底图之上 */
    maskVolumeData?: VolumeData | null
    maskOverlayEnabled?: boolean
    /** 掩码标量最大值：AI 分割为 1，金属伪影演示为 255 */
    maskDataMax?: number
    /** 掩码体渲染不透明度（0~1） */
    maskOpacity?: number
  }>(),
  {
    volumeData: null,
    windowCenter: 50,
    windowWidth: 350,
    isMask: false,
    dataMin: 0,
    dataMax: 1,
    maskColor: () => [1.0, 0.35, 0.15],
    maskVolumeData: null,
    maskOverlayEnabled: false,
    maskDataMax: 1,
    maskOpacity: 0.72,
  },
)

const containerRef = ref<HTMLElement | null>(null)

let genericRenderWindow: ReturnType<typeof vtkGenericRenderWindow.newInstance> | null = null
let mapper: ReturnType<typeof vtkVolumeMapper.newInstance> | null = null
let volume: ReturnType<typeof vtkVolume.newInstance> | null = null
let maskMapper: ReturnType<typeof vtkVolumeMapper.newInstance> | null = null
let maskVolume: ReturnType<typeof vtkVolume.newInstance> | null = null
let renderer: ReturnType<ReturnType<typeof vtkGenericRenderWindow.newInstance>['getRenderer']> | null = null
let renderWindow: ReturnType<ReturnType<typeof vtkGenericRenderWindow.newInstance>['getRenderWindow']> | null = null
let lastBaseVolumeRef: unknown = null

function isOverlayMode() {
  return Boolean(props.maskOverlayEnabled && props.maskVolumeData?.vtkImageData && !props.isMask)
}

function applyBaseTransfer() {
  if (!volume || !renderWindow) {
    return
  }

  const color = vtkColorTransferFunction.newInstance()
  const opacity = vtkPiecewiseFunction.newInstance()
  const renderAsMaskOnly = props.isMask && !isOverlayMode()

  if (renderAsMaskOnly) {
    const [r, g, b] = props.maskColor
    const maskMax = Math.max(Number(props.maskDataMax), 1)
    const threshold = maskMax <= 1 ? 0.5 : maskMax - 1

    color.addRGBPoint(0, 0.0, 0.0, 0.0)
    color.addRGBPoint(maskMax, r, g, b)
    opacity.addPoint(0, 0.0)
    opacity.addPoint(threshold, 0.0)
    opacity.addPoint(maskMax, props.maskOpacity)
  } else {
    const ww = Math.max(Number(props.windowWidth), 1.0)
    const wc = Number(props.windowCenter)
    const lower = wc - ww / 2
    const upper = wc + ww / 2
    const min = Number(props.dataMin)
    const max = Number(props.dataMax)

    color.addRGBPoint(min, 0.0, 0.0, 0.0)
    color.addRGBPoint(lower, 0.0, 0.0, 0.0)
    color.addRGBPoint(wc, 0.85, 0.85, 0.85)
    color.addRGBPoint(upper, 1.0, 1.0, 1.0)
    color.addRGBPoint(max, 1.0, 1.0, 1.0)

    opacity.addPoint(min, 0.0)
    opacity.addPoint(lower, 0.0)
    opacity.addPoint(wc, 0.08)
    opacity.addPoint(upper, 0.32)
    opacity.addPoint(max, 0.52)
  }

  const volumeProperty = volume.getProperty()
  volumeProperty.setRGBTransferFunction(0, color)
  volumeProperty.setScalarOpacity(0, opacity)
}

function applyMaskOverlayTransfer() {
  if (!maskVolume || !renderWindow) {
    return
  }

  const color = vtkColorTransferFunction.newInstance()
  const opacity = vtkPiecewiseFunction.newInstance()
  const [r, g, b] = props.maskColor
  const maskMax = Math.max(Number(props.maskDataMax), 1)
  const threshold = maskMax <= 1 ? 0.5 : maskMax - 1

  color.addRGBPoint(0, 0.0, 0.0, 0.0)
  color.addRGBPoint(maskMax, r, g, b)
  opacity.addPoint(0, 0.0)
  opacity.addPoint(threshold, 0.0)
  opacity.addPoint(maskMax, props.maskOpacity)

  const volumeProperty = maskVolume.getProperty()
  volumeProperty.setRGBTransferFunction(0, color)
  volumeProperty.setScalarOpacity(0, opacity)
}

function syncMaskOverlayVolume() {
  if (!renderer || !renderWindow || !maskMapper || !maskVolume) {
    return
  }

  if (!isOverlayMode()) {
    if (renderer.getVolumes().includes(maskVolume)) {
      renderer.removeVolume(maskVolume)
    }
    renderWindow.render()
    return
  }

  maskMapper.setInputData(props.maskVolumeData!.vtkImageData as never)
  if (!renderer.getVolumes().includes(maskVolume)) {
    renderer.addVolume(maskVolume)
  }
  applyMaskOverlayTransfer()
}

function renderVolume() {
  if (!props.volumeData?.vtkImageData || !mapper || !volume || !renderer || !renderWindow) {
    return
  }

  const baseChanged = lastBaseVolumeRef !== props.volumeData.vtkImageData
  lastBaseVolumeRef = props.volumeData.vtkImageData

  mapper.setInputData(props.volumeData.vtkImageData as never)
  if (!renderer.getVolumes().includes(volume)) {
    renderer.addVolume(volume)
  }

  applyBaseTransfer()
  syncMaskOverlayVolume()

  if (baseChanged) {
    renderer.resetCamera()
  }
  renderWindow.render()
}

function handleResize() {
  if (!genericRenderWindow || !renderer || !renderWindow) return
  genericRenderWindow.resize()
  renderer.resetCamera()
  renderWindow.render()
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (!containerRef.value) return

  genericRenderWindow = vtkGenericRenderWindow.newInstance({
    background: [0.02, 0.027, 0.05],
  })
  genericRenderWindow.setContainer(containerRef.value)
  genericRenderWindow.resize()

  renderer = genericRenderWindow.getRenderer()
  renderWindow = genericRenderWindow.getRenderWindow()

  mapper = vtkVolumeMapper.newInstance()
  mapper.setSampleDistance(1.0)

  volume = vtkVolume.newInstance()
  volume.setMapper(mapper)

  maskMapper = vtkVolumeMapper.newInstance()
  maskMapper.setSampleDistance(0.8)

  maskVolume = vtkVolume.newInstance()
  maskVolume.setMapper(maskMapper)

  const configureVolumeProperty = (volumeProperty: ReturnType<ReturnType<typeof vtkVolume.newInstance>['getProperty']>) => {
    volumeProperty.setScalarOpacityUnitDistance(0, 1.0)
    volumeProperty.setInterpolationTypeToLinear()
    volumeProperty.setShade(true)
  }

  configureVolumeProperty(volume.getProperty())
  configureVolumeProperty(maskVolume.getProperty())

  window.addEventListener('resize', handleResize)
  resizeObserver = new ResizeObserver(() => handleResize())
  resizeObserver.observe(containerRef.value)

  renderVolume()
})

watch(
  () => props.volumeData,
  () => {
    renderVolume()
  },
)

watch(
  () => props.maskVolumeData,
  () => {
    syncMaskOverlayVolume()
    renderWindow?.render()
  },
)

watch(
  () => [
    props.windowCenter,
    props.windowWidth,
    props.isMask,
    props.dataMin,
    props.dataMax,
    props.maskColor,
    props.maskOverlayEnabled,
    props.maskDataMax,
    props.maskOpacity,
  ],
  () => {
    applyBaseTransfer()
    syncMaskOverlayVolume()
    renderWindow?.render()
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  if (genericRenderWindow) {
    genericRenderWindow.delete()
    genericRenderWindow = null
  }
})
</script>

<template>
  <div ref="containerRef" class="vtk-container"></div>
</template>

<style scoped>
.vtk-container {
  width: 100%;
  height: 100%;
  min-height: 0;
  border: none;
  border-radius: 0;
  overflow: hidden;
}
</style>

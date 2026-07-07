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
    isMask?: boolean
    dataMin?: number
    dataMax?: number
    /** isMask=true 时的高亮颜色（0~1 归一化 RGB），默认橙红 */
    maskColor?: [number, number, number]
  }>(),
  {
    volumeData: null,
    windowCenter: 50,
    windowWidth: 350,
    isMask: false,
    dataMin: 0,
    dataMax: 1,
    maskColor: () => [1.0, 0.35, 0.15],
  },
)

const containerRef = ref<HTMLElement | null>(null)

let genericRenderWindow: ReturnType<typeof vtkGenericRenderWindow.newInstance> | null = null
let mapper: ReturnType<typeof vtkVolumeMapper.newInstance> | null = null
let volume: ReturnType<typeof vtkVolume.newInstance> | null = null
let renderer: ReturnType<ReturnType<typeof vtkGenericRenderWindow.newInstance>['getRenderer']> | null = null
let renderWindow: ReturnType<ReturnType<typeof vtkGenericRenderWindow.newInstance>['getRenderWindow']> | null = null

function applyTransfer() {
  if (!volume || !renderWindow) {
    return
  }

  const color = vtkColorTransferFunction.newInstance()
  const opacity = vtkPiecewiseFunction.newInstance()

  if (props.isMask) {
    const [r, g, b] = props.maskColor
    color.addRGBPoint(0, 0.0, 0.0, 0.0)
    color.addRGBPoint(255, r, g, b)
    opacity.addPoint(0, 0.0)
    opacity.addPoint(254, 0.0)
    opacity.addPoint(255, 0.85)
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
  renderWindow.render()
}

function renderVolume() {
  if (!props.volumeData?.vtkImageData || !mapper || !volume || !renderer || !renderWindow) {
    return
  }

  mapper.setInputData(props.volumeData.vtkImageData as never)
  if (!renderer.getVolumes().includes(volume)) {
    renderer.addVolume(volume)
  }

  applyTransfer()
  renderer.resetCamera()
  renderWindow.render()
}

function handleResize() {
  if (!genericRenderWindow || !renderer || !renderWindow) return
  genericRenderWindow.resize()
  // 容器尺寸变化（如网格布局/侧栏切换）后重新居中相机，避免画面偏移或被裁切
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

  const volumeProperty = volume.getProperty()
  volumeProperty.setScalarOpacityUnitDistance(0, 1.0)
  volumeProperty.setInterpolationTypeToLinear()
  volumeProperty.setShade(true)

  window.addEventListener('resize', handleResize)
  // 容器由 flex/grid 分配尺寸，其变化（如布局模式切换）不一定触发 window resize，
  // 必须用 ResizeObserver 监听容器本身，否则 vtk 内部画布尺寸与可视区域不一致，
  // 导致渲染内容被裁切、看起来不居中。
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
  () => [props.windowCenter, props.windowWidth, props.isMask, props.dataMin, props.dataMax, props.maskColor],
  () => {
    applyTransfer()
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
  /* 不要设固定 min-height：容器由父级 flex/grid 精确分配尺寸，
     若最小高度大于实际分配高度，会把 vtk 画布撑大后被父级裁切，
     导致三维渲染看起来偏移/不居中。 */
  min-height: 0;
  border: none;
  border-radius: 0;
  overflow: hidden;
}
</style>

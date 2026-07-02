<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import '@kitware/vtk.js/Rendering/Profiles/Volume'
import vtkColorTransferFunction from '@kitware/vtk.js/Rendering/Core/ColorTransferFunction'
import vtkGenericRenderWindow from '@kitware/vtk.js/Rendering/Misc/GenericRenderWindow'
import vtkPiecewiseFunction from '@kitware/vtk.js/Common/DataModel/PiecewiseFunction'
import vtkVolume from '@kitware/vtk.js/Rendering/Core/Volume'
import vtkVolumeMapper from '@kitware/vtk.js/Rendering/Core/VolumeMapper'

const props = defineProps({
  volumeData: {
    type: Object,
    default: null,
  },
  windowCenter: {
    type: Number,
    default: 50,
  },
  windowWidth: {
    type: Number,
    default: 350,
  },
  isMask: {
    type: Boolean,
    default: false,
  },
  dataMin: {
    type: Number,
    default: 0,
  },
  dataMax: {
    type: Number,
    default: 1,
  },
})

const containerRef = ref(null)

let genericRenderWindow = null
let mapper = null
let volume = null
let renderer = null
let renderWindow = null

function applyTransfer() {
  if (!volume || !renderWindow) {
    return
  }

  const color = vtkColorTransferFunction.newInstance()
  const opacity = vtkPiecewiseFunction.newInstance()

  if (props.isMask) {
    color.addRGBPoint(0, 0.0, 0.0, 0.0)
    color.addRGBPoint(255, 1.0, 0.35, 0.15)
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

  mapper.setInputData(props.volumeData.vtkImageData)
  if (!renderer.getVolumes().includes(volume)) {
    renderer.addVolume(volume)
  }

  applyTransfer()
  renderer.resetCamera()
  renderWindow.render()
}

function handleResize() {
  if (genericRenderWindow) {
    genericRenderWindow.resize()
  }
}

onMounted(() => {
  genericRenderWindow = vtkGenericRenderWindow.newInstance({
    background: [0.05, 0.05, 0.07],
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
  renderVolume()
})

watch(
  () => props.volumeData,
  () => {
    renderVolume()
  },
)

watch(
  () => [props.windowCenter, props.windowWidth, props.isMask, props.dataMin, props.dataMax],
  () => {
    applyTransfer()
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
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
  min-height: 620px;
  border: 1px solid #2b2d31;
  border-radius: 8px;
  overflow: hidden;
}
</style>

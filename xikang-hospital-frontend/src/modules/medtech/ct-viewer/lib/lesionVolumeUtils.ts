import vtkDataArray from '@kitware/vtk.js/Common/Core/DataArray'
import vtkImageData from '@kitware/vtk.js/Common/DataModel/ImageData'

interface CroppableVolume {
  scalars: ArrayLike<number>
  dimensions: [number, number, number]
  spacing: number[]
  origin: number[]
}

/**
 * 以世界坐标（mm）为中心，裁出一个立方体子体积，用于病灶 3D 预览小窗口。
 *
 * 不依赖病灶 bbox（体素坐标顺序在新旧后端间可能不一致），只用更明确的
 * centroidXyz（世界坐标）+ 期望物理边长反推体素范围，规避顺序歧义。
 */
export function cropVolumeAroundWorldPoint(
  volume: CroppableVolume,
  centerXyz: [number, number, number],
  halfExtentMm: number,
) {
  const [xDim, yDim, zDim] = volume.dimensions
  const spacing = volume.spacing?.length === 3 ? volume.spacing : [1, 1, 1]
  const origin = volume.origin?.length === 3 ? volume.origin : [0, 0, 0]

  const centerVoxel = [0, 1, 2].map((axis) => (centerXyz[axis] - origin[axis]) / (spacing[axis] || 1))
  const halfVoxel = [0, 1, 2].map((axis) => Math.max(2, Math.ceil(halfExtentMm / (spacing[axis] || 1))))

  const dims = [xDim, yDim, zDim]
  const lo = [0, 1, 2].map((axis) =>
    Math.max(0, Math.min(dims[axis] - 1, Math.floor(centerVoxel[axis] - halfVoxel[axis]))),
  )
  const hi = [0, 1, 2].map((axis) =>
    Math.max(0, Math.min(dims[axis] - 1, Math.ceil(centerVoxel[axis] + halfVoxel[axis]))),
  )

  const [x0, y0, z0] = lo
  const [x1, y1, z1] = hi
  const w = Math.max(1, x1 - x0 + 1)
  const h = Math.max(1, y1 - y0 + 1)
  const d = Math.max(1, z1 - z0 + 1)

  const cropped = new Float32Array(w * h * d)
  const { scalars } = volume
  for (let z = 0; z < d; z += 1) {
    const srcZ = z + z0
    for (let y = 0; y < h; y += 1) {
      const srcY = y + y0
      const srcRowOffset = srcZ * xDim * yDim + srcY * xDim
      const dstRowOffset = z * w * h + y * w
      for (let x = 0; x < w; x += 1) {
        cropped[dstRowOffset + x] = Number(scalars[srcRowOffset + x + x0])
      }
    }
  }

  const imageData = vtkImageData.newInstance()
  imageData.setDimensions(w, h, d)
  imageData.setSpacing(spacing as [number, number, number])
  imageData.setOrigin([0, 0, 0])
  const dataArray = vtkDataArray.newInstance({
    name: 'LesionCropScalars',
    values: cropped,
    numberOfComponents: 1,
  })
  imageData.getPointData().setScalars(dataArray)

  return {
    vtkImageData: imageData,
    scalars: cropped,
    dimensions: [w, h, d] as [number, number, number],
  }
}

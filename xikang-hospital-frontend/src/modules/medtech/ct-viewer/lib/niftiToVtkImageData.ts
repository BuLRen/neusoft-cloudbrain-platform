import vtkDataArray from '@kitware/vtk.js/Common/Core/DataArray'
import vtkImageData from '@kitware/vtk.js/Common/DataModel/ImageData'
import * as nifti from 'nifti-reader-js'

import type { ParsedVolumeData } from './nrrdToVtkImageData'

const NIFTI_DATATYPE = {
  UINT8: 2,
  INT16: 4,
  INT32: 8,
  FLOAT32: 16,
  FLOAT64: 64,
} as const

function readNiftiScalars(header: nifti.NIFTI1 | nifti.NIFTI2, image: ArrayBuffer): ArrayLike<number> {
  const voxelCount = header.dims[1] * header.dims[2] * header.dims[3]
  switch (header.datatypeCode) {
    case NIFTI_DATATYPE.UINT8:
      return new Uint8Array(image, 0, voxelCount)
    case NIFTI_DATATYPE.INT16:
      return new Int16Array(image, 0, voxelCount)
    case NIFTI_DATATYPE.INT32:
      return new Int32Array(image, 0, voxelCount)
    case NIFTI_DATATYPE.FLOAT32:
      return new Float32Array(image, 0, voxelCount)
    case NIFTI_DATATYPE.FLOAT64:
      return new Float64Array(image, 0, voxelCount)
    default:
      throw new Error(`当前前端暂不支持该 NIfTI 数据类型：${header.datatypeCode}`)
  }
}

function buildVtkVolume(
  dimensions: [number, number, number],
  spacing: [number, number, number],
  scalars: ArrayLike<number>,
): ParsedVolumeData {
  const [x, y, z] = dimensions
  const imageData = vtkImageData.newInstance()

  imageData.setDimensions(x, y, z)
  imageData.setSpacing(spacing)
  imageData.setOrigin([0, 0, 0])
  imageData.setDirection([1, 0, 0, 0, 1, 0, 0, 0, 1])

  const dataArray = vtkDataArray.newInstance({
    name: 'CTScalars',
    values: scalars,
    numberOfComponents: 1,
  })
  imageData.getPointData().setScalars(dataArray)

  return {
    vtkImageData: imageData,
    scalars,
    dimensions,
    spacing,
    origin: [0, 0, 0],
    direction: [1, 0, 0, 0, 1, 0, 0, 0, 1],
    isMask: false,
  }
}

export function parseNiftiArrayBuffer(arrayBuffer: ArrayBuffer): ParsedVolumeData {
  let buffer: ArrayBuffer = arrayBuffer
  if (nifti.isCompressed(arrayBuffer)) {
    buffer = nifti.decompress(arrayBuffer) as ArrayBuffer
  }
  if (!nifti.isNIFTI(buffer)) {
    throw new Error('不是有效的 NIfTI 文件')
  }

  const header = nifti.readHeader(buffer)
  if (header.dims[0] < 3) {
    throw new Error('仅支持 3D NIfTI 体数据')
  }

  const image = nifti.readImage(header, buffer)
  const nx = header.dims[1]
  const ny = header.dims[2]
  const nz = header.dims[3]
  const scalars = readNiftiScalars(header, image)
  const spacing: [number, number, number] = [
    Math.abs(header.pixDims[1]) || 1,
    Math.abs(header.pixDims[2]) || 1,
    Math.abs(header.pixDims[3]) || 1,
  ]

  return buildVtkVolume([nx, ny, nz], spacing, scalars)
}

export async function parseNiftiFile(file: File): Promise<ParsedVolumeData> {
  const buffer = await file.arrayBuffer()
  return parseNiftiArrayBuffer(buffer)
}

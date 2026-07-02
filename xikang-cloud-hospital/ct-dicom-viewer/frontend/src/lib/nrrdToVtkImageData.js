import nrrd from 'nrrd-js'
import { ungzip } from 'pako'
import vtkDataArray from '@kitware/vtk.js/Common/Core/DataArray'
import vtkImageData from '@kitware/vtk.js/Common/DataModel/ImageData'

const IS_LITTLE_ENDIAN = new Uint8Array(new Uint16Array([1]).buffer)[0] === 1

const TYPE_INFO = {
  int8: { bytes: 1, ctor: Int8Array, getter: 'getInt8' },
  uint8: { bytes: 1, ctor: Uint8Array, getter: 'getUint8' },
  int16: { bytes: 2, ctor: Int16Array, getter: 'getInt16' },
  uint16: { bytes: 2, ctor: Uint16Array, getter: 'getUint16' },
  int32: { bytes: 4, ctor: Int32Array, getter: 'getInt32' },
  uint32: { bytes: 4, ctor: Uint32Array, getter: 'getUint32' },
  float: { bytes: 4, ctor: Float32Array, getter: 'getFloat32' },
  double: { bytes: 8, ctor: Float64Array, getter: 'getFloat64' },
}

function vecLength(vector) {
  return Math.sqrt(vector.reduce((sum, value) => sum + value * value, 0))
}

function normalize(vector) {
  const len = vecLength(vector)
  if (!len) {
    return [0, 0, 0]
  }
  return vector.map((value) => value / len)
}

function getSpacing(parsed) {
  if (Array.isArray(parsed.spacings) && parsed.spacings.length >= 3) {
    return parsed.spacings.slice(0, 3).map((value) => Math.abs(value) || 1)
  }

  if (Array.isArray(parsed.spaceDirections) && parsed.spaceDirections.length >= 3) {
    return parsed.spaceDirections.slice(0, 3).map((direction) => {
      if (!Array.isArray(direction)) {
        return 1
      }
      return vecLength(direction.slice(0, 3)) || 1
    })
  }

  return [1, 1, 1]
}

function getDirection(parsed) {
  if (Array.isArray(parsed.spaceDirections) && parsed.spaceDirections.length >= 3) {
    const [xDirection, yDirection, zDirection] = parsed.spaceDirections.slice(0, 3).map((value) =>
      Array.isArray(value) ? normalize(value.slice(0, 3)) : [0, 0, 0],
    )

    return [
      xDirection[0],
      xDirection[1],
      xDirection[2],
      yDirection[0],
      yDirection[1],
      yDirection[2],
      zDirection[0],
      zDirection[1],
      zDirection[2],
    ]
  }

  return [1, 0, 0, 0, 1, 0, 0, 0, 1]
}

function getVoxelCount(sizes) {
  return sizes.reduce((acc, value) => acc * value, 1)
}

function decodeRawData(buffer, type, endian, expectedCount) {
  const info = TYPE_INFO[type]
  if (!info) {
    throw new Error(`暂不支持的 NRRD 数据类型：${type}`)
  }

  const expectedBytes = expectedCount * info.bytes
  if (buffer.byteLength < expectedBytes) {
    throw new Error('NRRD 二进制数据长度不足。')
  }

  if (info.bytes === 1) {
    return new info.ctor(buffer.slice(0, expectedBytes))
  }

  const dataIsLittleEndian = endian === 'little'
  if (dataIsLittleEndian === IS_LITTLE_ENDIAN) {
    return new info.ctor(buffer.slice(0, expectedBytes))
  }

  const output = new info.ctor(expectedCount)
  const view = new DataView(buffer, 0, expectedBytes)
  for (let index = 0; index < expectedCount; index += 1) {
    output[index] = view[info.getter](index * info.bytes, dataIsLittleEndian)
  }

  return output
}

function ensureDataPayload(parsed) {
  if (parsed?.data?.length) {
    return parsed
  }

  if (parsed.dataFile) {
    throw new Error('该 NRRD 使用外部 data file，网页端单文件上传无法直接读取。请先转换为内嵌数据的 .nrrd。')
  }

  if (!parsed.buffer) {
    throw new Error('NRRD 数据为空，无法渲染。')
  }

  const sizes = parsed.sizes ?? []
  const totalCount = getVoxelCount(sizes)
  const encoding = parsed.encoding?.toLowerCase()

  if (encoding === 'gzip' || encoding === 'gz') {
    const compressed = new Uint8Array(parsed.buffer)
    const inflated = ungzip(compressed)
    parsed.data = decodeRawData(
      inflated.buffer.slice(inflated.byteOffset, inflated.byteOffset + inflated.byteLength),
      parsed.type,
      parsed.endian ?? 'little',
      totalCount,
    )
    return parsed
  }

  throw new Error(`当前前端暂不支持该 NRRD 编码：${parsed.encoding}`)
}

function toVtkImageData(parsed) {
  const normalized = ensureDataPayload(parsed)

  const dimensions = normalized.sizes?.slice(0, 3)
  if (!Array.isArray(dimensions) || dimensions.length < 3) {
    throw new Error('仅支持 3D NRRD 体数据。')
  }

  const [x, y, z] = dimensions
  const voxelCount = x * y * z
  const scalarValues = normalized.data
  const numberOfComponents = Math.max(1, Math.floor(scalarValues.length / voxelCount))

  if (voxelCount <= 0 || scalarValues.length < voxelCount) {
    throw new Error('NRRD 体素数据长度与尺寸不匹配。')
  }

  const imageData = vtkImageData.newInstance()
  imageData.setDimensions(x, y, z)
  imageData.setSpacing(getSpacing(normalized))
  imageData.setOrigin(normalized.spaceOrigin?.slice(0, 3) ?? [0, 0, 0])
  imageData.setDirection(...getDirection(normalized))

  const dataArray = vtkDataArray.newInstance({
    name: 'CTScalars',
    values: scalarValues,
    numberOfComponents,
  })
  imageData.getPointData().setScalars(dataArray)

  return {
    vtkImageData: imageData,
    scalars: scalarValues,
    dimensions: [x, y, z],
    spacing: getSpacing(normalized),
    origin: normalized.spaceOrigin?.slice(0, 3) ?? [0, 0, 0],
    direction: getDirection(normalized),
    isMask: false,
  }
}

export function parseNrrdArrayBuffer(arrayBuffer) {
  const parsed = nrrd.parse(arrayBuffer)
  return toVtkImageData(parsed)
}

export async function parseNrrdFile(file) {
  const buffer = await file.arrayBuffer()
  return parseNrrdArrayBuffer(buffer)
}

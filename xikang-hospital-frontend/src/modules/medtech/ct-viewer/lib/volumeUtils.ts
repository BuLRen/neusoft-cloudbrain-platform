export function windowToUint8(sliceArray: ArrayLike<number>, windowCenter: number, windowWidth: number) {
  const width = Math.max(windowWidth, 1)
  const lower = windowCenter - width / 2
  const upper = windowCenter + width / 2

  const output = new Uint8ClampedArray(sliceArray.length)
  for (let i = 0; i < sliceArray.length; i += 1) {
    const value = Number.isFinite(sliceArray[i]) ? Number(sliceArray[i]) : 0
    const mapped = ((value - lower) / (upper - lower)) * 255
    output[i] = Math.max(0, Math.min(255, mapped))
  }
  return output
}

export function maskOverlayToRgb(
  baseSlice: ArrayLike<number>,
  maskSlice: ArrayLike<number>,
  windowCenter: number,
  windowWidth: number,
  alpha = 0.65,
  overlayColor: [number, number, number] = [255, 80, 40],
) {
  const gray = windowToUint8(baseSlice, windowCenter, windowWidth)
  const rgb = new Uint8ClampedArray(gray.length * 3)

  for (let i = 0; i < gray.length; i += 1) {
    const g = gray[i]
    const offset = i * 3
    if (maskSlice[i] > 0) {
      rgb[offset] = Math.round(g * (1 - alpha) + overlayColor[0] * alpha)
      rgb[offset + 1] = Math.round(g * (1 - alpha) + overlayColor[1] * alpha)
      rgb[offset + 2] = Math.round(g * (1 - alpha) + overlayColor[2] * alpha)
    } else {
      rgb[offset] = g
      rgb[offset + 1] = g
      rgb[offset + 2] = g
    }
  }
  return rgb
}

interface VolumeSliceData {
  scalars: ArrayLike<number>
  dimensions: [number, number, number]
}

export function extractSliceZyx(volume: VolumeSliceData, sliceIndex: number) {
  const { scalars, dimensions } = volume
  const [xDim, yDim, zDim] = dimensions
  const z = Math.max(0, Math.min(zDim - 1, sliceIndex))

  const sliceLength = xDim * yDim
  const output = new Float32Array(sliceLength)
  const zOffset = z * sliceLength

  for (let i = 0; i < sliceLength; i += 1) {
    output[i] = Number(scalars[zOffset + i])
  }
  return { data: output, width: xDim, height: yDim }
}

export function extractCoronalSlice(volume: VolumeSliceData, yIndex: number) {
  const { scalars, dimensions } = volume
  const [xDim, yDim, zDim] = dimensions
  const y = Math.max(0, Math.min(yDim - 1, yIndex))

  const output = new Float32Array(xDim * zDim)
  for (let z = 0; z < zDim; z += 1) {
    for (let x = 0; x < xDim; x += 1) {
      output[(zDim - 1 - z) * xDim + x] = Number(scalars[z * xDim * yDim + y * xDim + x])
    }
  }

  return { data: output, width: xDim, height: zDim }
}

export function extractSagittalSlice(volume: VolumeSliceData, xIndex: number) {
  const { scalars, dimensions } = volume
  const [xDim, yDim, zDim] = dimensions
  const x = Math.max(0, Math.min(xDim - 1, xIndex))

  const output = new Float32Array(yDim * zDim)
  for (let z = 0; z < zDim; z += 1) {
    for (let y = 0; y < yDim; y += 1) {
      output[(zDim - 1 - z) * yDim + y] = Number(scalars[z * xDim * yDim + y * xDim + x])
    }
  }

  return { data: output, width: yDim, height: zDim }
}

/** 生成轴状切片缩略图（最近邻降采样），供底部胶片条使用，避免逐张渲染整幅原图 */
export function extractAxialThumbnail(
  volume: VolumeSliceData,
  sliceIndex: number,
  windowCenter: number,
  windowWidth: number,
  targetSize = 64,
): { data: Uint8ClampedArray; width: number; height: number } {
  const { scalars, dimensions } = volume
  const [xDim, yDim, zDim] = dimensions
  const z = Math.max(0, Math.min(zDim - 1, sliceIndex))
  const sliceLength = xDim * yDim
  const zOffset = z * sliceLength

  const aspect = xDim / yDim
  const width = aspect >= 1 ? targetSize : Math.max(1, Math.round(targetSize * aspect))
  const height = aspect >= 1 ? Math.max(1, Math.round(targetSize / aspect)) : targetSize

  const width2 = Math.max(windowWidth, 1)
  const lower = windowCenter - width2 / 2
  const upper = windowCenter + width2 / 2

  const output = new Uint8ClampedArray(width * height)
  for (let ty = 0; ty < height; ty += 1) {
    const srcY = Math.min(yDim - 1, Math.floor((ty / height) * yDim))
    for (let tx = 0; tx < width; tx += 1) {
      const srcX = Math.min(xDim - 1, Math.floor((tx / width) * xDim))
      const value = Number(scalars[zOffset + srcY * xDim + srcX])
      const mapped = ((value - lower) / (upper - lower)) * 255
      output[ty * width + tx] = Math.max(0, Math.min(255, mapped))
    }
  }

  return { data: output, width, height }
}

export function normalizeFilterNameForMask(filterName: string) {
  return filterName === '金属伪影掩码 Metal Artifact Mask'
}

/** CT 显示用窗宽窗位：避免把空气/FOV 外像素纳入后对比度失真 */
export function computeCtDisplayWindow(options?: {
  min?: number | null
  max?: number | null
  scalars?: ArrayLike<number>
}): { windowCenter: number; windowWidth: number } {
  const min = options?.min
  const max = options?.max
  if (min != null && max != null && Number.isFinite(min) && Number.isFinite(max)) {
    const range = max - min
    if (range > 800) {
      return { windowCenter: 40, windowWidth: 400 }
    }
    return {
      windowCenter: Math.round((min + max) / 2),
      windowWidth: Math.max(Math.round(range), 1),
    }
  }

  const scalars = options?.scalars
  if (scalars?.length) {
    let tissueMin = Number.POSITIVE_INFINITY
    let tissueMax = Number.NEGATIVE_INFINITY
    const step = Math.max(1, Math.floor(scalars.length / 8000))
    for (let i = 0; i < scalars.length; i += step) {
      const value = Number(scalars[i])
      if (!Number.isFinite(value) || value < -200) continue
      tissueMin = Math.min(tissueMin, value)
      tissueMax = Math.max(tissueMax, value)
    }
    if (Number.isFinite(tissueMin) && Number.isFinite(tissueMax) && tissueMax > tissueMin) {
      return { windowCenter: 40, windowWidth: 400 }
    }
  }

  return { windowCenter: 40, windowWidth: 400 }
}

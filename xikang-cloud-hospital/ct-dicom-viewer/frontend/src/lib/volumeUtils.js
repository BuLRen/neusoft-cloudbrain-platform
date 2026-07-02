export function windowToUint8(sliceArray, windowCenter, windowWidth) {
  const width = Math.max(windowWidth, 1)
  const lower = windowCenter - width / 2
  const upper = windowCenter + width / 2

  const output = new Uint8ClampedArray(sliceArray.length)
  for (let i = 0; i < sliceArray.length; i += 1) {
    const value = Number.isFinite(sliceArray[i]) ? sliceArray[i] : 0
    const mapped = ((value - lower) / (upper - lower)) * 255
    output[i] = Math.max(0, Math.min(255, mapped))
  }
  return output
}

export function maskOverlayToRgb(
  baseSlice,
  maskSlice,
  windowCenter,
  windowWidth,
  alpha = 0.65,
) {
  const gray = windowToUint8(baseSlice, windowCenter, windowWidth)
  const rgb = new Uint8ClampedArray(gray.length * 3)
  const overlayColor = [255, 80, 40]

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

export function extractSliceZyx(volume, sliceIndex) {
  const { scalars, dimensions } = volume
  const [xDim, yDim, zDim] = dimensions
  const z = Math.max(0, Math.min(zDim - 1, sliceIndex))

  const sliceLength = xDim * yDim
  const output = new Float32Array(sliceLength)
  const zOffset = z * sliceLength

  for (let i = 0; i < sliceLength; i += 1) {
    output[i] = scalars[zOffset + i]
  }
  return { data: output, width: xDim, height: yDim }
}

export function extractCoronalSlice(volume, yIndex) {
  const { scalars, dimensions } = volume
  const [xDim, yDim, zDim] = dimensions
  const y = Math.max(0, Math.min(yDim - 1, yIndex))

  const output = new Float32Array(xDim * zDim)
  for (let z = 0; z < zDim; z += 1) {
    for (let x = 0; x < xDim; x += 1) {
      output[(zDim - 1 - z) * xDim + x] = scalars[z * xDim * yDim + y * xDim + x]
    }
  }

  return { data: output, width: xDim, height: zDim }
}

export function extractSagittalSlice(volume, xIndex) {
  const { scalars, dimensions } = volume
  const [xDim, yDim, zDim] = dimensions
  const x = Math.max(0, Math.min(xDim - 1, xIndex))

  const output = new Float32Array(yDim * zDim)
  for (let z = 0; z < zDim; z += 1) {
    for (let y = 0; y < yDim; y += 1) {
      output[(zDim - 1 - z) * yDim + y] = scalars[z * xDim * yDim + y * xDim + x]
    }
  }

  return { data: output, width: yDim, height: zDim }
}

export function normalizeFilterNameForMask(filterName) {
  return filterName === '金属伪影掩码 Metal Artifact Mask'
}

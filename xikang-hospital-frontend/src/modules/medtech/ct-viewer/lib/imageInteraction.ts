/** 2D 切片视口的缩放/平移/测量/ROI 交互用到的纯函数工具（不依赖 Vue） */

export function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

export interface ContainFit {
  width: number
  height: number
  offsetX: number
  offsetY: number
}

/** 计算“图像以 object-fit: contain 方式居中显示在容器内”时的尺寸与偏移 */
export function computeContainFit(
  containerWidth: number,
  containerHeight: number,
  naturalWidth: number,
  naturalHeight: number,
): ContainFit {
  if (!containerWidth || !containerHeight || !naturalWidth || !naturalHeight) {
    return { width: containerWidth, height: containerHeight, offsetX: 0, offsetY: 0 }
  }
  const containerAspect = containerWidth / containerHeight
  const imageAspect = naturalWidth / naturalHeight
  let width: number
  let height: number
  if (imageAspect > containerAspect) {
    width = containerWidth
    height = containerWidth / imageAspect
  } else {
    height = containerHeight
    width = containerHeight * imageAspect
  }
  return {
    width,
    height,
    offsetX: (containerWidth - width) / 2,
    offsetY: (containerHeight - height) / 2,
  }
}

/** 两点间真实物理距离（mm），rowSpacingMm/colSpacingMm 为该切片行/列方向的体素间距 */
export function computeDistanceMm(
  p0: { x: number; y: number },
  p1: { x: number; y: number },
  naturalWidth: number,
  naturalHeight: number,
  colSpacingMm: number,
  rowSpacingMm: number,
): number {
  const dxPixels = (p1.x - p0.x) * naturalWidth
  const dyPixels = (p1.y - p0.y) * naturalHeight
  const dxMm = dxPixels * colSpacingMm
  const dyMm = dyPixels * rowSpacingMm
  return Math.sqrt(dxMm * dxMm + dyMm * dyMm)
}

export interface RegionStats {
  mean: number
  min: number
  max: number
  areaMm2: number
  pixelCount: number
}

/** 统计矩形 ROI（归一化坐标）内的 HU 均值/最大/最小值，以及物理面积 */
export function computeRegionStats(
  sliceData: ArrayLike<number>,
  sliceWidth: number,
  sliceHeight: number,
  rect: { x0: number; y0: number; x1: number; y1: number },
  colSpacingMm: number,
  rowSpacingMm: number,
): RegionStats | null {
  const px0 = clamp(Math.round(Math.min(rect.x0, rect.x1) * sliceWidth), 0, sliceWidth - 1)
  const px1 = clamp(Math.round(Math.max(rect.x0, rect.x1) * sliceWidth), 0, sliceWidth - 1)
  const py0 = clamp(Math.round(Math.min(rect.y0, rect.y1) * sliceHeight), 0, sliceHeight - 1)
  const py1 = clamp(Math.round(Math.max(rect.y0, rect.y1) * sliceHeight), 0, sliceHeight - 1)

  let sum = 0
  let count = 0
  let min = Number.POSITIVE_INFINITY
  let max = Number.NEGATIVE_INFINITY

  for (let y = py0; y <= py1; y += 1) {
    const rowOffset = y * sliceWidth
    for (let x = px0; x <= px1; x += 1) {
      const value = Number(sliceData[rowOffset + x])
      if (!Number.isFinite(value)) continue
      sum += value
      count += 1
      if (value < min) min = value
      if (value > max) max = value
    }
  }

  if (count === 0) return null

  const widthVoxels = px1 - px0 + 1
  const heightVoxels = py1 - py0 + 1
  const areaMm2 = widthVoxels * colSpacingMm * heightVoxels * rowSpacingMm

  return { mean: sum / count, min, max, areaMm2, pixelCount: count }
}

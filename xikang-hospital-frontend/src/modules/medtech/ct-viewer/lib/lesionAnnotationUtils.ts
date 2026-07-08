import type { CtLesionItem } from '@/shared/api/modules/ctViewer'

/** AI 病灶标注框，坐标为该平面内的归一化值（0~1），供 CtSliceViewPanel 叠加层绘制 */
export interface LesionDrawRect {
  left: number
  top: number
  width: number
  height: number
  label: string
  confidence: number
}

/**
 * bbox: [z0, y0, x0, z1, y1, x1]（体素坐标，与后端 lung-nodule-seg-service 一致：
 * numpy 数组轴顺序为 D/H/W，即 Z（层深/轴状切片）/ Y（高度）/ X（宽度））。
 * 注意：不是 [x0,y0,z0,x1,y1,z1]，此前误按该顺序解析导致标注框位置/尺寸错误。
 *
 * 以下三个函数均返回"该平面内归一化 0~1 坐标"的矩形，由 xDim/yDim/zDim（体素总数）
 * 换算，避免直接使用体素像素值——体素分辨率与画布实际显示分辨率不一定一致
 * （尤其冠状/矢状图按物理毫米比例显示时会与体素分辨率产生非等比缩放）。
 */
export function getLesionsForAxialSlice(
  lesions: CtLesionItem[],
  sliceIndex: number,
  xDim: number,
  yDim: number,
): LesionDrawRect[] {
  if (!xDim || !yDim) return []
  return lesions
    .filter((lesion) => {
      const bbox = lesion.bbox
      if (!bbox || bbox.length < 6) return false
      const [z0, , , z1] = bbox
      return sliceIndex >= z0 && sliceIndex <= z1
    })
    .map((lesion) => {
      const [, y0, x0, , y1, x1] = lesion.bbox
      return {
        left: x0 / xDim,
        top: y0 / yDim,
        width: (x1 - x0 + 1) / xDim,
        height: (y1 - y0 + 1) / yDim,
        label: lesion.label || '疑似结节',
        confidence: lesion.confidence ?? 0,
      }
    })
}

export function getLesionsForCoronalSlice(
  lesions: CtLesionItem[],
  sliceIndex: number,
  xDim: number,
  zDim: number,
): LesionDrawRect[] {
  if (!xDim || !zDim) return []
  return lesions
    .filter((lesion) => {
      const bbox = lesion.bbox
      if (!bbox || bbox.length < 6) return false
      const [, y0, , , y1] = bbox
      return sliceIndex >= y0 && sliceIndex <= y1
    })
    .map((lesion) => {
      const [z0, , x0, z1, , x1] = lesion.bbox
      return {
        left: x0 / xDim,
        top: (zDim - 1 - z1) / zDim,
        width: (x1 - x0 + 1) / xDim,
        height: (z1 - z0 + 1) / zDim,
        label: lesion.label || '疑似结节',
        confidence: lesion.confidence ?? 0,
      }
    })
}

export function getLesionsForSagittalSlice(
  lesions: CtLesionItem[],
  sliceIndex: number,
  yDim: number,
  zDim: number,
): LesionDrawRect[] {
  if (!yDim || !zDim) return []
  return lesions
    .filter((lesion) => {
      const bbox = lesion.bbox
      if (!bbox || bbox.length < 6) return false
      const [, , x0, , , x1] = bbox
      return sliceIndex >= x0 && sliceIndex <= x1
    })
    .map((lesion) => {
      const [z0, y0, , z1, y1] = lesion.bbox
      return {
        left: y0 / yDim,
        top: (zDim - 1 - z1) / zDim,
        width: (y1 - y0 + 1) / yDim,
        height: (z1 - z0 + 1) / zDim,
        label: lesion.label || '疑似结节',
        confidence: lesion.confidence ?? 0,
      }
    })
}

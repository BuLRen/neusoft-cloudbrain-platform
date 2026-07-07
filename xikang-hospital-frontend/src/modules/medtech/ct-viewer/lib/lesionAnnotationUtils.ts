import type { CtLesionItem } from '@/shared/api/modules/ctViewer'

export interface LesionDrawRect {
  left: number
  top: number
  width: number
  height: number
  label: string
  confidence: number
}

const MIN_BOX_PX = 18

function clampBox(rect: LesionDrawRect): LesionDrawRect {
  let { left, top, width, height, label, confidence } = rect
  if (width < MIN_BOX_PX) {
    const cx = left + width / 2
    left = cx - MIN_BOX_PX / 2
    width = MIN_BOX_PX
  }
  if (height < MIN_BOX_PX) {
    const cy = top + height / 2
    top = cy - MIN_BOX_PX / 2
    height = MIN_BOX_PX
  }
  return { left, top, width, height, label, confidence }
}

/** bbox: [x0, y0, z0, x1, y1, z1]（体素坐标，与后端一致） */
export function getLesionsForAxialSlice(lesions: CtLesionItem[], sliceIndex: number): LesionDrawRect[] {
  return lesions
    .filter((lesion) => {
      const bbox = lesion.bbox
      if (!bbox || bbox.length < 6) return false
      const [, , z0, , , z1] = bbox
      return sliceIndex >= z0 && sliceIndex <= z1
    })
    .map((lesion) => {
      const [x0, y0, , x1, y1] = lesion.bbox
      return clampBox({
        left: x0,
        top: y0,
        width: x1 - x0 + 1,
        height: y1 - y0 + 1,
        label: lesion.label || '疑似结节',
        confidence: lesion.confidence ?? 0,
      })
    })
}

export function getLesionsForCoronalSlice(
  lesions: CtLesionItem[],
  sliceIndex: number,
  zDim: number,
): LesionDrawRect[] {
  return lesions
    .filter((lesion) => {
      const bbox = lesion.bbox
      if (!bbox || bbox.length < 6) return false
      const [, y0, , , y1] = bbox
      return sliceIndex >= y0 && sliceIndex <= y1
    })
    .map((lesion) => {
      const [x0, , z0, x1, , z1] = lesion.bbox
      return clampBox({
        left: x0,
        top: zDim - 1 - z1,
        width: x1 - x0 + 1,
        height: z1 - z0 + 1,
        label: lesion.label || '疑似结节',
        confidence: lesion.confidence ?? 0,
      })
    })
}

export function getLesionsForSagittalSlice(
  lesions: CtLesionItem[],
  sliceIndex: number,
  zDim: number,
): LesionDrawRect[] {
  return lesions
    .filter((lesion) => {
      const bbox = lesion.bbox
      if (!bbox || bbox.length < 6) return false
      const [x0, , , x1] = bbox
      return sliceIndex >= x0 && sliceIndex <= x1
    })
    .map((lesion) => {
      const [, y0, z0, , y1, z1] = lesion.bbox
      return clampBox({
        left: y0,
        top: zDim - 1 - z1,
        width: y1 - y0 + 1,
        height: z1 - z0 + 1,
        label: lesion.label || '疑似结节',
        confidence: lesion.confidence ?? 0,
      })
    })
}

export function drawLesionAnnotations(ctx: CanvasRenderingContext2D, rects: LesionDrawRect[]) {
  if (!rects.length) return

  const canvasW = ctx.canvas.width
  const canvasH = ctx.canvas.height

  rects.forEach((item, index) => {
    const { left, top, width, height, label, confidence } = item
    const percent = `${Math.round(confidence * 100)}%`

    ctx.save()
    ctx.strokeStyle = '#ff4d4f'
    ctx.fillStyle = '#ff4d4f'
    ctx.lineWidth = 2
    ctx.strokeRect(left + 0.5, top + 0.5, width, height)

    const anchorX = left + width
    const anchorY = top + height * 0.35
    const labelX = Math.min(anchorX + 14, canvasW - 72)
    const labelY = Math.max(22, top - 4 + index * 38)

    ctx.beginPath()
    ctx.lineWidth = 1.5
    ctx.moveTo(anchorX, anchorY)
    ctx.lineTo(labelX - 4, labelY - 2)
    ctx.stroke()

    const headAngle = Math.atan2(labelY - anchorY, labelX - anchorX)
    const headLen = 7
    ctx.beginPath()
    ctx.moveTo(labelX - 4, labelY - 2)
    ctx.lineTo(
      labelX - 4 - headLen * Math.cos(headAngle - 0.45),
      labelY - 2 - headLen * Math.sin(headAngle - 0.45),
    )
    ctx.moveTo(labelX - 4, labelY - 2)
    ctx.lineTo(
      labelX - 4 - headLen * Math.cos(headAngle + 0.45),
      labelY - 2 - headLen * Math.sin(headAngle + 0.45),
    )
    ctx.stroke()

    ctx.font = '600 13px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    ctx.shadowColor = 'rgba(0,0,0,0.85)'
    ctx.shadowBlur = 4
    ctx.fillText(label, labelX, labelY)
    ctx.font = '12px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    ctx.fillText(percent, labelX, labelY + 15)
    ctx.restore()
  })
}

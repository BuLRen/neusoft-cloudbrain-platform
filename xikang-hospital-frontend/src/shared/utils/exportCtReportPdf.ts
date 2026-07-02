import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'
import html2pdf from 'html2pdf.js'
import {
  buildCtDiagnosisPdfFilename,
  buildCtFilmPdfFilename,
  type CtDiagnosisReportPdfContext,
  type CtFilmPdfContext,
} from '@/shared/types/ctReportPdf'

/**
 * 胶片 PDF：直接用 html2canvas 截图再贴满单页，
 * 绕过 html2pdf 的自动分页逻辑，保证 25 格始终在同一页内。
 */
export async function exportCtFilmPdfFromElement(
  element: HTMLElement,
  context: CtFilmPdfContext,
): Promise<void> {
  const filename = buildCtFilmPdfFilename(context.techName, context.checkRequestId)

  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    logging: false,
    backgroundColor: '#ffffff',
    width: element.offsetWidth,
    height: element.offsetHeight,
  })

  const imgData = canvas.toDataURL('image/jpeg', 0.96)

  // A4 横向：297 × 210 mm，零边距，图片完整铺满
  const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'landscape' })
  const pageW = pdf.internal.pageSize.getWidth()
  const pageH = pdf.internal.pageSize.getHeight()

  pdf.addImage(imgData, 'JPEG', 0, 0, pageW, pageH)
  pdf.save(filename)
}

/**
 * 诊断报告 PDF：内容较少，走 html2pdf 正常分页即可。
 */
export async function exportCtDiagnosisReportPdfFromElement(
  element: HTMLElement,
  context: CtDiagnosisReportPdfContext,
): Promise<void> {
  const filename = buildCtDiagnosisPdfFilename(context.techName, context.checkRequestId)
  await html2pdf()
    .set({
      filename,
      margin: [8, 8, 8, 8],
      image: { type: 'jpeg', quality: 0.98 },
      html2canvas: {
        scale: 2,
        useCORS: true,
        logging: false,
        backgroundColor: '#ffffff',
      },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
    })
    .from(element)
    .save()
}

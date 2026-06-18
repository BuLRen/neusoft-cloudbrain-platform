import html2pdf from 'html2pdf.js'
import { buildLabReportFilename, type LabReportPdfContext } from '@/shared/types/labReportPdf'

const PDF_OPTIONS = {
  margin: [10, 10, 10, 10] as [number, number, number, number],
  filename: '检验报告.pdf',
  image: { type: 'jpeg' as const, quality: 0.98 },
  html2canvas: {
    scale: 2,
    useCORS: true,
    logging: false,
  },
  jsPDF: {
    unit: 'mm' as const,
    format: 'a4' as const,
    orientation: 'portrait' as const,
  },
}

export async function exportLabReportPdfFromElement(
  element: HTMLElement,
  context: LabReportPdfContext,
): Promise<void> {
  const filename = buildLabReportFilename(context.techName === '-' ? context.result.checkName : context.techName)
  await html2pdf().set({ ...PDF_OPTIONS, filename }).from(element).save()
}

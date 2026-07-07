import html2pdf from 'html2pdf.js'
import {
  buildClinicalNotebookPdfFilename,
  type ClinicalNotebookPdfContext,
} from '@/shared/types/clinicalNotebookPdf'

const PDF_OPTIONS = {
  margin: [10, 10, 10, 10] as [number, number, number, number],
  filename: '门诊病历本.pdf',
  image: { type: 'jpeg' as const, quality: 0.98 },
  html2canvas: {
    scale: 2,
    useCORS: true,
    logging: false,
    backgroundColor: '#ffffff',
  },
  jsPDF: {
    unit: 'mm' as const,
    format: 'a4' as const,
    orientation: 'portrait' as const,
  },
}

export async function exportClinicalNotebookPdfFromElement(
  element: HTMLElement,
  context: ClinicalNotebookPdfContext,
): Promise<void> {
  const filename = buildClinicalNotebookPdfFilename(context.notebook)
  await html2pdf().set({ ...PDF_OPTIONS, filename }).from(element).save()
}

export function delayMs(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

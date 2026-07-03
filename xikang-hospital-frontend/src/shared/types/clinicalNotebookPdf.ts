import type { ClinicalNotebook } from '@/shared/api/modules/clinicalRecord'
import { HOSPITAL_NAME } from '@/shared/types/labReportPdf'
import { displayText, examCategoryLabel, formatVisitDate } from '@/shared/utils/clinicalNotebook'

export const CLINICAL_NOTEBOOK_DISCLAIMER =
  '本病历本由系统自动生成，仅供临床参考，最终以医疗机构签章纸质病历为准。'

export interface ClinicalNotebookPdfContext {
  hospitalName: string
  notebook: ClinicalNotebook
}

export function buildClinicalNotebookPdfContext(notebook: ClinicalNotebook): ClinicalNotebookPdfContext {
  return {
    hospitalName: HOSPITAL_NAME,
    notebook,
  }
}

export function buildClinicalNotebookPdfFilename(notebook: ClinicalNotebook): string {
  const caseNumber = (notebook.header.caseNumber || '病历')
    .replace(/[\\/:*?"<>|]/g, '_')
    .trim()
  const visit = formatVisitDate(notebook.header.visitDate).replace(/[:\s]/g, '')
  return `门诊病历本_${caseNumber || '未编号'}_${visit || '导出'}.pdf`
}

export function formatNotebookField(value?: string | null, fallback = '—'): string {
  return displayText(value, fallback)
}

export function formatExamCategoryLabel(category: 'check' | 'inspection'): string {
  return examCategoryLabel(category)
}

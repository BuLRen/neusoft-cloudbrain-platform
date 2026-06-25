import type { InspectionReport } from '@/shared/api/modules/medtech'
import type { InspectionResult } from '@/shared/api/modules/physician'
import type { EncounterPatientSummary } from '@/app/stores/encounter'
import type { ClinicalExamItem, ClinicalNotebookHeader } from '@/shared/api/modules/clinicalRecord'
import type { SimulatedCheckStructuredOutput } from '@/shared/types/simulatedCheckResult'

export type LabReportPatientSummary = Pick<EncounterPatientSummary, 'realName' | 'caseNumber' | 'gender' | 'age'>

export const HOSPITAL_NAME = '熙康云医院'
export const LAB_REPORT_DISCLAIMER = '本报告为系统模拟/演示结果，仅供教学使用。'

export interface LabReportPdfContext {
  hospitalName: string
  reportTitle: string
  patientName: string
  caseNumber: string
  gender: string
  age: string
  techName: string
  techCode: string
  position: string
  purpose: string
  reportTime: string
  result: SimulatedCheckStructuredOutput
}

export function statusIndicator(status: string): string {
  if (status === 'high' || status === 'abnormal' || status === 'positive') return '↑'
  if (status === 'low') return '↓'
  return ''
}

export function canExportLabReport(structuredOutput: SimulatedCheckStructuredOutput | null | undefined): boolean {
  return Boolean(structuredOutput && (structuredOutput.resultItems?.length ?? 0) > 0)
}

export function formatReportTime(value?: string | null): string {
  if (!value) {
    return new Date().toLocaleString('zh-CN', { hour12: false })
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return parsed.toLocaleString('zh-CN', { hour12: false })
}

function displayValue(value?: string | number | null): string {
  if (value == null || String(value).trim() === '') return '-'
  return String(value)
}

function buildReportTitle(structuredOutput: SimulatedCheckStructuredOutput, techName?: string): string {
  const name = structuredOutput.checkName?.trim() || techName?.trim() || '检验'
  return name.endsWith('报告单') ? name : `${name}检验报告单`
}

export function buildLabReportContextFromMedtech(
  report: InspectionReport,
  structuredOutput: SimulatedCheckStructuredOutput,
): LabReportPdfContext {
  return {
    hospitalName: HOSPITAL_NAME,
    reportTitle: buildReportTitle(structuredOutput, report.techName),
    patientName: displayValue(report.patientName),
    caseNumber: displayValue(report.caseNumber),
    gender: '-',
    age: '-',
    techName: displayValue(report.techName),
    techCode: displayValue(report.techCode),
    position: displayValue(report.position),
    purpose: displayValue(report.info),
    reportTime: formatReportTime(report.inspectionTime),
    result: structuredOutput,
  }
}

export function buildLabReportContextFromPhysician(
  row: InspectionResult,
  structuredOutput: SimulatedCheckStructuredOutput,
  patientSummary?: LabReportPatientSummary | null,
): LabReportPdfContext {
  return {
    hospitalName: HOSPITAL_NAME,
    reportTitle: buildReportTitle(structuredOutput, row.techName),
    patientName: displayValue(patientSummary?.realName),
    caseNumber: displayValue(patientSummary?.caseNumber),
    gender: displayValue(patientSummary?.gender),
    age: patientSummary?.age != null ? String(patientSummary.age) : '-',
    techName: displayValue(row.techName),
    techCode: '-',
    position: displayValue(row.inspectionPosition),
    purpose: '-',
    reportTime: formatReportTime(row.inspectionTime),
    result: structuredOutput,
  }
}

export function buildLabReportContextFromClinicalNotebook(
  item: ClinicalExamItem,
  structuredOutput: SimulatedCheckStructuredOutput,
  header?: ClinicalNotebookHeader | null,
): LabReportPdfContext {
  return {
    hospitalName: HOSPITAL_NAME,
    reportTitle: buildReportTitle(structuredOutput, item.techName),
    patientName: displayValue(header?.realName),
    caseNumber: displayValue(header?.caseNumber),
    gender: displayValue(header?.gender),
    age: header?.age != null ? String(header.age) : '-',
    techName: displayValue(item.techName),
    techCode: '-',
    position: '-',
    purpose: '-',
    reportTime: formatReportTime(item.completedAt),
    result: structuredOutput,
  }
}

export function buildLabReportFilename(techName: string): string {
  const date = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  const safe = techName.replace(/[/\\?%*:|"<>]/g, '_').trim() || '检验'
  return `${safe}-检验报告-${date}.pdf`
}

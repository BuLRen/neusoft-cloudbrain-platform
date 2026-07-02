import type { CheckResult } from '@/shared/api/modules/physician'
import type { EncounterPatientSummary } from '@/app/stores/encounter'
import type { ResultFormSchema } from '@/shared/types/resultForm'
import type { CtAnalyzeResult } from '@/shared/api/modules/ctViewer'
import { HOSPITAL_NAME, formatReportTime } from '@/shared/types/labReportPdf'

export const CT_REPORT_DISCLAIMER = '本报告仅供临床参考，最终诊断以医师签章报告为准。'

export interface CtReportPdfPatient {
  hospitalName: string
  patientName: string
  caseNumber: string
  gender: string
  age: string
}

export interface CtFilmPdfContext extends CtReportPdfPatient {
  reportTitle: string
  techName: string
  checkRequestId: number
  reportTime: string
  sourceName?: string
}

export interface CtDiagnosisReportField {
  label: string
  value: string
}

export interface CtDiagnosisReportPdfContext extends CtReportPdfPatient {
  reportTitle: string
  techName: string
  checkRequestId: number
  reportTime: string
  fields: CtDiagnosisReportField[]
  qcSummary?: string
}

function displayValue(value?: string | number | null): string {
  if (value == null || String(value).trim() === '') return '-'
  return String(value)
}

function buildPatientSummary(patient?: EncounterPatientSummary | null): CtReportPdfPatient {
  return {
    hospitalName: HOSPITAL_NAME,
    patientName: displayValue(patient?.realName),
    caseNumber: displayValue(patient?.caseNumber),
    gender: displayValue(patient?.gender),
    age: patient?.age != null ? `${patient.age}` : '-',
  }
}

export function buildCtFilmPdfFilename(techName: string, checkRequestId: number): string {
  const safe = techName.replace(/[\\/:*?"<>|]/g, '_').trim() || 'CT'
  return `CT胶片_${safe}_${checkRequestId}.pdf`
}

export function buildCtDiagnosisPdfFilename(techName: string, checkRequestId: number): string {
  const safe = techName.replace(/[\\/:*?"<>|]/g, '_').trim() || 'CT'
  return `CT诊断报告_${safe}_${checkRequestId}.pdf`
}

export function buildCtFilmPdfContext(
  row: CheckResult,
  patient?: EncounterPatientSummary | null,
): CtFilmPdfContext {
  const techName = row.techName || 'CT 检查'
  return {
    ...buildPatientSummary(patient),
    reportTitle: `${techName} 影像胶片`,
    techName,
    checkRequestId: row.id,
    reportTime: formatReportTime(row.checkTime),
    sourceName: row.imagingSourceName || undefined,
  }
}

const QC_SEVERITY_LABEL: Record<string, string> = {
  clean: '无伪影',
  mild: '轻微',
  moderate: '中等',
  severe: '严重',
}

function buildQcSummaryText(result?: CtAnalyzeResult | null): string | undefined {
  if (!result) return undefined
  const severity = QC_SEVERITY_LABEL[result.severity] ?? result.severity
  const artifact = result.has_artifact ? `检测到伪影（${severity}）` : `未见明显伪影（${severity}）`
  if (!result.has_artifact) return artifact
  const metal = Math.round((result.artifact_types?.metal ?? 0) * 1000) / 10
  return `${artifact}；金属伪影概率 ${metal}%`
}

export function buildCtDiagnosisReportPdfContext(
  row: CheckResult,
  schema: ResultFormSchema,
  patient?: EncounterPatientSummary | null,
): CtDiagnosisReportPdfContext {
  const techName = row.techName || schema.techName || 'CT 检查'
  const values = schema.existingValues ?? {}
  const fields = schema.fields
    .map((field) => ({
      label: field.fieldLabel,
      value: displayValue(values[field.fieldKey] as string | number | null | undefined),
    }))
    .filter((field) => field.value !== '-')

  return {
    ...buildPatientSummary(patient),
    reportTitle: `${techName} 诊断报告`,
    techName,
    checkRequestId: row.id,
    reportTime: formatReportTime(row.checkTime),
    fields,
    qcSummary: buildQcSummaryText(row.imagingAnalysisResult),
  }
}

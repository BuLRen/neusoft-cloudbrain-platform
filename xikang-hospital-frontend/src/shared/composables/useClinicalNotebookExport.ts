import { nextTick, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import ClinicalNotebookPrintSheet from '@/shared/components/ClinicalNotebookPrintSheet.vue'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import CtFilmPrintSheet from '@/shared/components/CtFilmPrintSheet.vue'
import CtDiagnosisReportPrintSheet from '@/shared/components/CtDiagnosisReportPrintSheet.vue'
import { physicianApi, type CheckResult } from '@/shared/api/modules/physician'
import type { CtVolumeMeta } from '@/shared/api/modules/ctViewer'
import type { ClinicalExamItem, ClinicalNotebook } from '@/shared/api/modules/clinicalRecord'
import type { EncounterPatientSummary } from '@/app/stores/encounter'
import {
  buildClinicalNotebookPdfContext,
  type ClinicalNotebookPdfContext,
} from '@/shared/types/clinicalNotebookPdf'
import { buildLabReportContextFromClinicalNotebook, type LabReportPdfContext } from '@/shared/types/labReportPdf'
import { buildCtDiagnosisReportPdfContext, buildCtFilmPdfContext, type CtDiagnosisReportPdfContext, type CtFilmPdfContext } from '@/shared/types/ctReportPdf'
import { parseResultPayload, type ResultFormField, type ResultFormSchema } from '@/shared/types/resultForm'
import {
  delayMs,
  exportClinicalNotebookPdfFromElement,
} from '@/shared/utils/exportClinicalNotebookPdf'
import { exportLabReportPdfFromElement } from '@/shared/utils/exportLabReportPdf'
import { exportCtDiagnosisReportPdfFromElement, exportCtFilmPdfFromElement } from '@/shared/utils/exportCtReportPdf'
import {
  describeExamItemExportCapability,
  isCtExamItem,
  resolveExamStructuredOutput,
} from '@/shared/utils/clinicalNotebook'

const DOWNLOAD_GAP_MS = 450

function buildCtSchemaFromExamItem(item: ClinicalExamItem): ResultFormSchema | null {
  const payload = parseResultPayload(item.resultRaw)
  if (!payload?.values || !Object.keys(payload.values).length) return null
  const fields: ResultFormField[] = [
    { fieldKey: 'findings', fieldLabel: '所见', fieldType: 'textarea', required: true, sortOrder: 1 },
    { fieldKey: 'impression', fieldLabel: '印象', fieldType: 'textarea', required: true, sortOrder: 2 },
    { fieldKey: 'conclusion', fieldLabel: '结论', fieldType: 'textarea', required: true, sortOrder: 3 },
  ]
  return {
    checkRequestId: item.id,
    categoryCode: payload.categoryCode ?? 'imaging_ct',
    techName: item.techName,
    fields,
    baseFieldCount: 3,
    extensionFieldCount: 0,
    existingValues: payload.values,
  }
}

function buildCheckResultStub(item: ClinicalExamItem): CheckResult {
  return {
    id: item.id,
    techName: item.techName,
    checkTime: item.completedAt,
  }
}

function toPatientSummary(header: ClinicalNotebook['header']): EncounterPatientSummary {
  return {
    realName: header.realName,
    caseNumber: header.caseNumber,
    gender: header.gender,
    age: header.age,
  }
}

export interface ClinicalNotebookExportRefs {
  notebookPrintRef: Ref<InstanceType<typeof ClinicalNotebookPrintSheet> | null>
  labPrintRef: Ref<InstanceType<typeof LabReportPrintSheet> | null>
  ctFilmPrintRef: Ref<InstanceType<typeof CtFilmPrintSheet> | null>
  ctDiagnosisPrintRef: Ref<InstanceType<typeof CtDiagnosisReportPrintSheet> | null>
}

export function useClinicalNotebookExport() {
  const exporting = ref(false)
  const notebookExportContext = ref<ClinicalNotebookPdfContext | null>(null)
  const labExportContext = ref<LabReportPdfContext | null>(null)
  const filmExportContext = ref<CtFilmPdfContext | null>(null)
  const volumeMeta = ref<CtVolumeMeta | null>(null)
  const ctDiagnosisExportContext = ref<CtDiagnosisReportPdfContext | null>(null)

  async function renderElement<T>(
    assignContext: (value: T) => void,
    context: T,
    printRef: Ref<{ $el?: HTMLElement } | null>,
  ): Promise<HTMLElement | null> {
    assignContext(context)
    await nextTick()
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => resolve())
    })
    return (printRef.value?.$el as HTMLElement | undefined) ?? null
  }

  async function exportNotebookPdf(
    notebook: ClinicalNotebook,
    refs: ClinicalNotebookExportRefs,
  ): Promise<boolean> {
    const context = buildClinicalNotebookPdfContext(notebook)
    const root = await renderElement(
      (value) => { notebookExportContext.value = value },
      context,
      refs.notebookPrintRef,
    )
    if (!root) {
      ElMessage.error('病历本模板未就绪，请稍后重试')
      return false
    }
    await exportClinicalNotebookPdfFromElement(root, context)
    return true
  }

  async function exportLabItemPdf(
    context: LabReportPdfContext,
    refs: ClinicalNotebookExportRefs,
  ): Promise<boolean> {
    const root = await renderElement(
      (value) => { labExportContext.value = value },
      context,
      refs.labPrintRef,
    )
    if (!root) return false
    await exportLabReportPdfFromElement(root, context)
    return true
  }

  async function exportCtDiagnosisItemPdf(
    context: CtDiagnosisReportPdfContext,
    refs: ClinicalNotebookExportRefs,
  ): Promise<boolean> {
    const root = await renderElement(
      (value) => { ctDiagnosisExportContext.value = value },
      context,
      refs.ctDiagnosisPrintRef,
    )
    if (!root) return false
    await exportCtDiagnosisReportPdfFromElement(root, context)
    return true
  }

  async function exportCtFilmItemPdf(
    context: CtFilmPdfContext,
    checkRequestId: number,
    refs: ClinicalNotebookExportRefs,
  ): Promise<boolean> {
    filmExportContext.value = context
    await nextTick()
    try {
      await refs.ctFilmPrintRef.value?.ensureRendered(() => physicianApi.fetchCheckImagingNrrd(checkRequestId))
    } catch {
      return false
    }
    await nextTick()
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => resolve())
    })
    const root = refs.ctFilmPrintRef.value?.$el as HTMLElement | undefined
    if (!root) return false
    await exportCtFilmPdfFromElement(root, context)
    return true
  }

  async function exportCtExamItem(
    item: ClinicalExamItem,
    notebook: ClinicalNotebook,
    mode: 'physician' | 'patient',
    refs: ClinicalNotebookExportRefs,
  ): Promise<number> {
    const schema = buildCtSchemaFromExamItem(item)
    let row: CheckResult = buildCheckResultStub(item)

    if (mode === 'physician') {
      try {
        const results = await physicianApi.checkResults(notebook.registerId)
        row = results.find((result) => result.id === item.id) ?? row
      } catch {
        // 使用 notebook 中的基础信息
      }
    }

    const patient = toPatientSummary(notebook.header)
    let pdfCount = 0

    if (schema) {
      const diagnosisOk = await exportCtDiagnosisItemPdf(
        buildCtDiagnosisReportPdfContext(row, schema, patient),
        refs,
      )
      if (diagnosisOk) pdfCount += 1
      await delayMs(DOWNLOAD_GAP_MS)
    }

    const canExportFilm = mode === 'physician' && Boolean(row.hasImaging && row.imagingVolumeId)
    if (canExportFilm) {
      try {
        volumeMeta.value = await physicianApi.fetchCheckImagingMeta(item.id)
      } catch {
        volumeMeta.value = null
      }
      const filmOk = await exportCtFilmItemPdf(
        buildCtFilmPdfContext(row, patient),
        item.id,
        refs,
      )
      if (filmOk) pdfCount += 1
    }

    if (pdfCount === 0) {
      ElMessage.warning(`${item.techName || 'CT 检查'}：暂无可导出的报告`)
    }

    return pdfCount
  }

  async function exportExamItem(
    item: ClinicalExamItem,
    notebook: ClinicalNotebook,
    mode: 'physician' | 'patient',
    refs: ClinicalNotebookExportRefs,
  ): Promise<number> {
    const capability = describeExamItemExportCapability(item, mode)
    if (!capability.exportable) {
      ElMessage.warning(`${item.techName || '项目'}：${capability.reason || '无法导出'}`)
      return 0
    }

    if (item.category === 'inspection') {
      const structured = resolveExamStructuredOutput(item)
      if (!structured) return 0
      const ok = await exportLabItemPdf(
        buildLabReportContextFromClinicalNotebook(item, structured, notebook.header),
        refs,
      )
      return ok ? 1 : 0
    }

    if (isCtExamItem(item)) {
      return exportCtExamItem(item, notebook, mode, refs)
    }

    const structured = resolveExamStructuredOutput(item)
    if (!structured) return 0
    const ok = await exportLabItemPdf(
      buildLabReportContextFromClinicalNotebook(item, structured, notebook.header),
      refs,
    )
    return ok ? 1 : 0
  }

  async function exportNotebookBundle(
    notebook: ClinicalNotebook,
    selectedItems: ClinicalExamItem[],
    mode: 'physician' | 'patient',
    refs: ClinicalNotebookExportRefs,
  ): Promise<void> {
    exporting.value = true
    try {
      const notebookOk = await exportNotebookPdf(notebook, refs)
      if (!notebookOk) {
        ElMessage.error('病历本 PDF 导出失败')
        return
      }

      if (!selectedItems.length) {
        ElMessage.success('病历本 PDF 已生成')
        return
      }

      await delayMs(DOWNLOAD_GAP_MS)

      let reportPdfCount = 0
      let successItemCount = 0
      for (const item of selectedItems) {
        const count = await exportExamItem(item, notebook, mode, refs)
        if (count > 0) {
          successItemCount += 1
          reportPdfCount += count
        }
        await delayMs(DOWNLOAD_GAP_MS)
      }

      if (successItemCount === selectedItems.length) {
        ElMessage.success(`已生成病历本及 ${reportPdfCount} 份检查检验报告 PDF`)
      } else {
        ElMessage.warning(
          `病历本已导出，${successItemCount}/${selectedItems.length} 个项目导出成功（共 ${reportPdfCount} 份报告 PDF）`,
        )
      }
    } catch {
      ElMessage.error('导出失败，请稍后重试')
    } finally {
      exporting.value = false
    }
  }

  return {
    exporting,
    notebookExportContext,
    labExportContext,
    filmExportContext,
    volumeMeta,
    ctDiagnosisExportContext,
    exportNotebookBundle,
  }
}

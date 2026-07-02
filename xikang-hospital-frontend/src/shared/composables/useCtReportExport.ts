import { nextTick, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import CtFilmPrintSheet from '@/shared/components/CtFilmPrintSheet.vue'
import CtDiagnosisReportPrintSheet from '@/shared/components/CtDiagnosisReportPrintSheet.vue'
import {
  exportCtDiagnosisReportPdfFromElement,
  exportCtFilmPdfFromElement,
} from '@/shared/utils/exportCtReportPdf'
import type { CtDiagnosisReportPdfContext, CtFilmPdfContext } from '@/shared/types/ctReportPdf'

export function useCtReportExport() {
  const filmExportContext = ref<CtFilmPdfContext | null>(null)
  const diagnosisExportContext = ref<CtDiagnosisReportPdfContext | null>(null)
  const exportingFilm = ref(false)
  const exportingReport = ref(false)

  async function exportFilmPdf(
    context: CtFilmPdfContext,
    printSheetRef: Ref<InstanceType<typeof CtFilmPrintSheet> | null>,
    nrrdFetcher: () => Promise<ArrayBuffer>,
  ): Promise<boolean> {
    filmExportContext.value = context
    exportingFilm.value = true
    try {
      await nextTick()
      await printSheetRef.value?.ensureRendered(nrrdFetcher)
      await nextTick()
      await new Promise<void>((resolve) => {
        requestAnimationFrame(() => resolve())
      })
      const root = printSheetRef.value?.$el as HTMLElement | undefined
      if (!root) {
        ElMessage.error('胶片模板未就绪，请稍后重试')
        return false
      }
      await exportCtFilmPdfFromElement(root, context)
      ElMessage.success('CT 胶片 PDF 已生成')
      return true
    } catch {
      ElMessage.error('胶片 PDF 导出失败，请稍后重试')
      return false
    } finally {
      exportingFilm.value = false
    }
  }

  async function exportDiagnosisPdf(
    context: CtDiagnosisReportPdfContext,
    printSheetRef: Ref<InstanceType<typeof CtDiagnosisReportPrintSheet> | null>,
  ): Promise<boolean> {
    diagnosisExportContext.value = context
    exportingReport.value = true
    try {
      await nextTick()
      const root = printSheetRef.value?.$el as HTMLElement | undefined
      if (!root) {
        ElMessage.error('报告模板未就绪，请稍后重试')
        return false
      }
      await exportCtDiagnosisReportPdfFromElement(root, context)
      ElMessage.success('CT 诊断报告 PDF 已生成')
      return true
    } catch {
      ElMessage.error('报告 PDF 导出失败，请稍后重试')
      return false
    } finally {
      exportingReport.value = false
    }
  }

  return {
    filmExportContext,
    diagnosisExportContext,
    exportingFilm,
    exportingReport,
    exportFilmPdf,
    exportDiagnosisPdf,
    CtFilmPrintSheet,
    CtDiagnosisReportPrintSheet,
  }
}

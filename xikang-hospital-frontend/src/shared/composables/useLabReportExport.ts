import { nextTick, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import { exportLabReportPdfFromElement } from '@/shared/utils/exportLabReportPdf'
import type { LabReportPdfContext } from '@/shared/types/labReportPdf'

export function useLabReportExport() {
  const exportContext = ref<LabReportPdfContext | null>(null)
  const exporting = ref(false)

  async function exportPdf(
    context: LabReportPdfContext,
    printSheetRef: Ref<InstanceType<typeof LabReportPrintSheet> | null>,
  ): Promise<boolean> {
    exportContext.value = context
    exporting.value = true
    try {
      await nextTick()
      const root = printSheetRef.value?.$el as HTMLElement | undefined
      if (!root) {
        ElMessage.error('报告模板未就绪，请稍后重试')
        return false
      }
      await exportLabReportPdfFromElement(root, context)
      ElMessage.success('检验报告 PDF 已生成')
      return true
    } catch {
      ElMessage.error('PDF 导出失败，请稍后重试')
      return false
    } finally {
      exporting.value = false
    }
  }

  return {
    exportContext,
    exporting,
    exportPdf,
    LabReportPrintSheet,
  }
}

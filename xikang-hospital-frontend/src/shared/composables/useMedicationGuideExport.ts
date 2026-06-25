import { nextTick, ref, type Ref } from 'vue'
import html2pdf from 'html2pdf.js'
import { ElMessage } from 'element-plus'
import MedicationGuidePrintSheet from '@/shared/components/MedicationGuidePrintSheet.vue'
import type { MedicationGuideRecord } from '@/shared/types/pharmacy'

const PDF_OPTIONS = {
  margin: [10, 10, 10, 10] as [number, number, number, number],
  filename: '用药指导单.pdf',
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

/**
 * 用药指导单前端 PDF 导出。
 * 思路与 useLabReportExport 一致：先把 Vue 组件渲染到隐藏 DOM，
 * 再用 html2pdf.js（html2canvas + jsPDF）截图成 PDF。
 * 浏览器系统字体直接栅格化成像素，不存在"字体缺失乱码"问题。
 */
export function useMedicationGuideExport() {
  const record = ref<MedicationGuideRecord | null>(null)
  const exporting = ref(false)

  async function exportPdf(
    guide: MedicationGuideRecord,
    printSheetRef: Ref<InstanceType<typeof MedicationGuidePrintSheet> | null>,
  ): Promise<boolean> {
    record.value = guide
    exporting.value = true
    try {
      await nextTick()
      const root = printSheetRef.value?.$el as HTMLElement | undefined
      if (!root) {
        ElMessage.error('指导单模板未就绪，请稍后重试')
        return false
      }
      const filename = `medication-guide-${guide.registerId ?? 'unknown'}.pdf`
      await html2pdf().set({ ...PDF_OPTIONS, filename }).from(root).save()
      ElMessage.success('用药指导单 PDF 已生成')
      return true
    } catch {
      ElMessage.error('PDF 导出失败，请稍后重试')
      return false
    } finally {
      exporting.value = false
    }
  }

  return {
    record,
    exporting,
    exportPdf,
    MedicationGuidePrintSheet,
  }
}

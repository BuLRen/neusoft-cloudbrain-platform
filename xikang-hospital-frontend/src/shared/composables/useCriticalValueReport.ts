import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  criticalValueApi,
  type CriticalDetectResult,
  type CriticalItemHit,
} from '@/shared/api/modules/criticalValue'

export interface CriticalValueReportContext {
  registerId: number
  sourceType: 'inspection' | 'check' | 'disposal'
  sourceId: number
  techName?: string
}

export function useCriticalValueReport() {
  const dialogVisible = ref(false)
  const reporting = ref(false)
  const detectResult = ref<CriticalDetectResult | null>(null)
  const reportContext = ref<CriticalValueReportContext | null>(null)
  let onFinished: (() => void) | null = null

  function openIfSuspected(
    response: Record<string, unknown> | null | undefined,
    context: CriticalValueReportContext,
    finished?: () => void,
  ): boolean {
    const detect = response?.criticalDetect as CriticalDetectResult | undefined
    if (!detect?.suspected || !(detect.items?.length)) {
      return false
    }
    detectResult.value = detect
    reportContext.value = context
    onFinished = finished ?? null
    dialogVisible.value = true
    return true
  }

  function finishFlow() {
    dialogVisible.value = false
    detectResult.value = null
    reportContext.value = null
    const cb = onFinished
    onFinished = null
    cb?.()
  }

  async function confirmReport() {
    const ctx = reportContext.value
    const detect = detectResult.value
    if (!ctx || !detect?.items?.length) return
    reporting.value = true
    try {
      await criticalValueApi.report({
        registerId: ctx.registerId,
        sourceType: ctx.sourceType,
        sourceId: ctx.sourceId,
        techName: ctx.techName,
        severity: detect.severity,
        items: detect.items as CriticalItemHit[],
      })
      ElMessage.success('危急值已上报，已通知开单医生')
      finishFlow()
    } catch (err) {
      const msg = err instanceof Error ? err.message : '危急值上报失败'
      ElMessage.error(msg)
    } finally {
      reporting.value = false
    }
  }

  function skipReport() {
    ElMessage.info('已跳过危急值上报')
    finishFlow()
  }

  return {
    dialogVisible,
    reporting,
    detectResult,
    reportContext,
    openIfSuspected,
    confirmReport,
    skipReport,
  }
}

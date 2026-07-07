import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { medtechApi, type CheckReport } from '@/shared/api/modules/medtech'
import { isCtCategoryCode } from '@/modules/medtech/constants/medtechCategory'
import {
  resolveSimulationDisplayOutput,
  type SimulatedCheckStructuredOutput,
} from '@/shared/types/simulatedCheckResult'

export interface CtCheckDraft {
  simulatedValues?: Record<string, unknown>
  savedAt?: number
}

export function ctCheckDraftKey(id: number) {
  return `ct-check-draft:${id}`
}

export interface CtCheckIdentity {
  aiCategoryCode?: string
}

export function isCtCheck(identity: CtCheckIdentity): boolean {
  return isCtCategoryCode(identity.aiCategoryCode)
}

export function isCtCategory(code?: string) {
  return isCtCategoryCode(code)
}

export function saveCtDraft(id: number, draft: CtCheckDraft) {
  sessionStorage.setItem(ctCheckDraftKey(id), JSON.stringify(draft))
}

export function loadCtDraft(id: number): CtCheckDraft | null {
  const raw = sessionStorage.getItem(ctCheckDraftKey(id))
  if (!raw) return null
  try {
    return JSON.parse(raw) as CtCheckDraft
  } catch {
    return null
  }
}

export function clearCtDraft(id: number) {
  sessionStorage.removeItem(ctCheckDraftKey(id))
}

export function useCtCheckContext() {
  const loading = ref(false)
  const imagingBinding = ref(false)
  const simulating = ref(false)
  const errorMessage = ref('')
  const simulateError = ref('')
  const report = ref<CheckReport | null>(null)
  const started = ref(false)
  const imagingVolumeId = ref('')
  const imagingSourceName = ref('')
  const structuredOutput = ref<SimulatedCheckStructuredOutput | null>(null)

  const hasImaging = computed(() => Boolean(imagingVolumeId.value))
  const isCt = computed(() => isCtCheck(report.value ?? {}))
  const canInfer = computed(
    () =>
      started.value &&
      !loading.value &&
      !simulating.value &&
      report.value?.paid !== false &&
      hasImaging.value,
  )

  function syncImagingFromReport(data: CheckReport) {
    imagingVolumeId.value = data.imagingVolumeId ?? ''
    imagingSourceName.value = data.imagingSourceName ?? ''
  }

  async function loadCheckContext(id: number, options?: { autoStart?: boolean }) {
    const autoStart = options?.autoStart ?? true
    loading.value = true
    errorMessage.value = ''
    try {
      report.value = await medtechApi.checkReport(id)
      syncImagingFromReport(report.value)

      if (!isCtCheck(report.value)) {
        errorMessage.value = '当前检查单不是 CT 影像项目'
        started.value = false
        return
      }

      if (report.value.paid === false) {
        errorMessage.value = '患者尚未支付检查费，请提醒患者先完成缴费后再执行'
        started.value = false
        return
      }

      if (autoStart && report.value.checkState === '待检查') {
        await medtechApi.startCheck(id)
        report.value = { ...report.value, checkState: '检查中', statusText: '检查中' }
      }

      started.value = report.value.checkState === '检查中'
      if (!started.value && report.value.checkState !== '待检查') {
        errorMessage.value = `当前状态为「${report.value.checkState ?? report.value.statusText ?? '未知'}」，无法继续 CT 检查`
      }
    } catch (err) {
      report.value = null
      const msg = err instanceof Error ? err.message : ''
      errorMessage.value = msg || '检查记录加载失败，请返回列表重试'
      started.value = false
    } finally {
      loading.value = false
    }
  }

  async function bindImaging(
    id: number,
    payload: { volumeId: string; sourceName: string },
    onBindFail?: () => void,
  ) {
    imagingBinding.value = true
    try {
      const bound = await medtechApi.bindCheckImaging(id, {
        volumeId: payload.volumeId,
        sourceName: payload.sourceName,
      })
      imagingVolumeId.value = bound.volumeId ?? payload.volumeId
      imagingSourceName.value = bound.sourceName ?? payload.sourceName
      if (report.value) {
        report.value = {
          ...report.value,
          imagingVolumeId: imagingVolumeId.value,
          imagingSourceName: imagingSourceName.value,
          hasImaging: true,
          hasImagingAnalysis: false,
          imagingAnalyzedAt: undefined,
          imagingAnalysisResult: undefined,
          hasImagingSegmentation: false,
          imagingSegmentedAt: undefined,
          imagingSegmentationResult: undefined,
          imagingSegmentationMaskVolumeId: undefined,
        }
      }
      ElMessage.success('CT 影像已绑定到当前检查单')
    } catch {
      ElMessage.error('影像绑定失败，请重试')
      onBindFail?.()
    } finally {
      imagingBinding.value = false
    }
  }

  async function clearImaging(id: number) {
    if (!hasImaging.value) return
    imagingBinding.value = true
    try {
      await medtechApi.clearCheckImaging(id)
      imagingVolumeId.value = ''
      imagingSourceName.value = ''
      if (report.value) {
        report.value = {
          ...report.value,
          imagingVolumeId: undefined,
          imagingSourceName: undefined,
          hasImaging: false,
          hasImagingAnalysis: false,
          imagingAnalyzedAt: undefined,
          imagingAnalysisResult: undefined,
          hasImagingSegmentation: false,
          imagingSegmentedAt: undefined,
          imagingSegmentationResult: undefined,
          imagingSegmentationMaskVolumeId: undefined,
        }
      }
      ElMessage.info('已清除影像绑定，可重新上传')
    } catch {
      ElMessage.error('清除影像绑定失败')
    } finally {
      imagingBinding.value = false
    }
  }

  async function runCtInfer(id: number) {
    if (!canInfer.value) return null
    simulating.value = true
    simulateError.value = ''
    structuredOutput.value = null
    try {
      const result = await medtechApi.ctInferCheck(id)
      structuredOutput.value = resolveSimulationDisplayOutput(result, {
        defaultCheckName: report.value?.techName,
      })
      return result
    } catch {
      simulateError.value = 'CT 影像分析失败，请稍后重试或手动录入'
      return null
    } finally {
      simulating.value = false
    }
  }

  return {
    loading,
    imagingBinding,
    simulating,
    errorMessage,
    simulateError,
    report,
    started,
    imagingVolumeId,
    imagingSourceName,
    structuredOutput,
    hasImaging,
    isCt,
    canInfer,
    syncImagingFromReport,
    loadCheckContext,
    bindImaging,
    clearImaging,
    runCtInfer,
  }
}

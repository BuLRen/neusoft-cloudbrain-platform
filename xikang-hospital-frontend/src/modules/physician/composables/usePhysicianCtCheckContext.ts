import { computed, ref } from 'vue'
import { physicianApi, type CheckResult } from '@/shared/api/modules/physician'
import type { ResultFormField, ResultFormSchema } from '@/shared/types/resultForm'
import { parseResultPayload } from '@/shared/types/resultForm'
import type { CtVolumeMeta } from '@/shared/api/modules/ctViewer'

function buildCtReportSchemaFallback(row: CheckResult): ResultFormSchema | null {
  const payload = parseResultPayload(row.checkResult)
  if (!payload?.values || !Object.keys(payload.values).length) return null
  const fields: ResultFormField[] = [
    { fieldKey: 'findings', fieldLabel: '所见', fieldType: 'textarea', required: true, sortOrder: 1 },
    { fieldKey: 'impression', fieldLabel: '印象', fieldType: 'textarea', required: true, sortOrder: 2 },
    { fieldKey: 'conclusion', fieldLabel: '结论', fieldType: 'textarea', required: true, sortOrder: 3 },
  ]
  return {
    checkRequestId: row.id,
    categoryCode: payload.categoryCode ?? 'imaging_ct',
    techName: row.techName,
    fields,
    baseFieldCount: 3,
    extensionFieldCount: 0,
    existingValues: payload.values,
  }
}

export function isPhysicianCtResult(row: CheckResult | null | undefined): boolean {
  if (!row) return false
  return Boolean(row.aiCategoryCode?.startsWith('imaging_ct') || /CT/i.test(row.techName || ''))
}

export function usePhysicianCtCheckContext() {
  const loading = ref(false)
  const errorMessage = ref('')
  const checkResult = ref<CheckResult | null>(null)
  const resultFormSchema = ref<ResultFormSchema | null>(null)
  const volumeMeta = ref<CtVolumeMeta | null>(null)

  const hasImaging = computed(() => Boolean(checkResult.value?.hasImaging && checkResult.value?.imagingVolumeId))
  const canViewImaging = computed(() => hasImaging.value && isPhysicianCtResult(checkResult.value))

  async function loadCheckContext(registerId: number, checkRequestId: number) {
    loading.value = true
    errorMessage.value = ''
    checkResult.value = null
    resultFormSchema.value = null
    volumeMeta.value = null
    try {
      const results = await physicianApi.checkResults(registerId)
      const row = results.find((item) => item.id === checkRequestId) ?? null
      if (!row) {
        errorMessage.value = '未找到该检查单或无权查看'
        return null
      }
      if (!isPhysicianCtResult(row)) {
        errorMessage.value = '该检查单不是 CT 影像检查'
        return null
      }
      checkResult.value = row

      if (row.checkState === '已完成' && row.checkResult) {
        resultFormSchema.value = buildCtReportSchemaFallback(row)
        void physicianApi.resolveCheckResultForm(checkRequestId)
          .then((resolved) => {
            if (checkResult.value?.id === checkRequestId) {
              resultFormSchema.value = resolved
            }
          })
          .catch(() => {
            // 保留本地 fallback schema，避免阻塞导出
          })
      }

      if (row.hasImaging && row.imagingVolumeId) {
        try {
          volumeMeta.value = await physicianApi.fetchCheckImagingMeta(checkRequestId)
        } catch {
          // meta 非必须
        }
      }

      return row
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : '加载 CT 检查信息失败'
      return null
    } finally {
      loading.value = false
    }
  }

  function fetchNrrd() {
    const id = checkResult.value?.id
    if (!id) {
      return Promise.reject(new Error('缺少检查单信息'))
    }
    return physicianApi.fetchCheckImagingNrrd(id)
  }

  return {
    loading,
    errorMessage,
    checkResult,
    resultFormSchema,
    volumeMeta,
    hasImaging,
    canViewImaging,
    loadCheckContext,
    fetchNrrd,
  }
}

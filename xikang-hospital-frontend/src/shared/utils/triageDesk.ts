import { registrationApi } from '@/shared/api/modules/registration'
import type { PatientInfo } from '@/app/stores/auth'

/**
 * 患者 AI 导诊成功后，向分诊台写入待确认记录（不阻断导诊主流程）。
 */
export async function submitTriageDeskRecord(params: {
  symptoms: string
  aiTriageResult: object
  patientId?: number | null
  patient?: Pick<PatientInfo, 'patientId' | 'realName' | 'phone'> | null
}): Promise<void> {
  const { symptoms, aiTriageResult } = params
  if ('isOutOfScope' in aiTriageResult && (aiTriageResult as { isOutOfScope?: boolean }).isOutOfScope) return

  const patientId = params.patientId ?? params.patient?.patientId
  if (!patientId) {
    console.warn('[triageDesk] 跳过创建分诊记录：缺少 patientId')
    return
  }

  try {
    await registrationApi.createTriage({
      patientId,
      patientName: params.patient?.realName ?? '匿名患者',
      patientPhone: params.patient?.phone,
      symptoms,
      aiTriageResult,
    })
  } catch (err) {
    console.warn('[triageDesk] 创建分诊台记录失败（不影响导诊）', err)
  }
}

import type { FollowUpHistoryEvent, FollowUpHistoryEventType } from '@/shared/types/medtechFollowUp'

const EVENT_LABELS: Record<FollowUpHistoryEventType, string> = {
  patient_feedback: '患者反馈',
  glucose_entry: '血糖录入',
  observation_confirmed: '观察确认',
  interview_scheduled: '访谈安排',
  interview_completed: '访谈完成',
  communication_message: '医患沟通',
  drug_card: '荐药卡片',
  diagnosis_card: '病况卡片',
  case_summary: '病例总结',
  revisit_reminder: '复诊提醒',
  forecast_alert: '血糖预警',
}

export function parseHistoryPayload(payload: FollowUpHistoryEvent['payload']): Record<string, unknown> {
  if (!payload) return {}
  if (typeof payload === 'string') {
    try {
      return JSON.parse(payload) as Record<string, unknown>
    } catch {
      return {}
    }
  }
  return payload
}

function excerpt(text: string, max = 320): string {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (normalized.length <= max) return normalized
  return `${normalized.slice(0, max)}…`
}

function asText(value: unknown): string | null {
  if (value == null) return null
  const text = String(value).trim()
  if (!text || text === 'null') return null
  return text
}

/** 将历史事件整理为面向护士/医生阅读的中文描述 */
export function formatHistoryEventSummary(event: FollowUpHistoryEvent): string {
  const direct = asText(event.summary)
  if (direct) return direct

  const payload = parseHistoryPayload(event.payload)
  const content = asText(payload.content) ?? asText(payload.summary) ?? asText(payload.patientFeedback)
  if (content) return excerpt(content)

  if (event.eventType === 'glucose_entry' && payload.metricValue != null) {
    const note = asText(payload.note)
    const base = `录入血糖 ${payload.metricValue} mmol/L`
    return note ? `${base}（${note}）` : base
  }

  if (event.eventType === 'patient_feedback') {
    const rating = payload.rating
    const relief = asText(payload.symptomRelief)
    if (typeof rating === 'number' && relief) {
      return `患者反馈：整体感受 ${rating}/5，症状变化 ${relief}`
    }
  }

  if (event.eventType === 'drug_card') {
    const drugName = asText(payload.drugName) ?? asText(payload.drug_name)
    const usage = asText(payload.drugUsage) ?? asText(payload.drug_usage)
    if (drugName && usage) return `推荐 ${drugName}，${usage}`
    if (drugName) return `推荐药品：${drugName}`
  }

  if (event.eventType === 'diagnosis_card') {
    const name = asText(payload.diseaseName) ?? asText(payload.diagnosisText)
    const treatment = asText(payload.treatmentDirection)
    if (name && treatment) return `可能病况：${name}，建议 ${treatment}`
    if (name) return `可能病况：${name}`
  }

  if (event.eventType === 'case_summary' || payload.messageType === 'case_summary') {
    return '已向患者分享本次看诊病例总结，请患者在随访沟通中查看详情。'
  }

  if (event.eventType === 'communication_message') {
    return '随访沟通消息已发送，请提醒患者按时查看与回复。'
  }

  return asText(event.title) ?? EVENT_LABELS[event.eventType] ?? '随访记录'
}

export function formatHistoryEventTitle(event: FollowUpHistoryEvent): string {
  const title = asText(event.title)
  if (title && title !== 'null' && title !== '医患沟通消息') return title
  return EVENT_LABELS[event.eventType] ?? '随访记录'
}

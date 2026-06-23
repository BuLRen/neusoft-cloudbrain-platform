import { http } from '../request'
import type {
  ExamAnalyzePayload,
  ExamAnalyzeResult,
  FollowUpCreatePayload,
  FollowUpCreateResult,
  FollowUpFeedbackPayload,
  FollowUpPlan,
  FollowUpQuestion,
  FollowUpRecord,
  MedicationGuidePayload,
  MedicationGuideResult,
  PrevisitChatFinishPayload,
  PrevisitChatMeta,
  PrevisitChatPayload,
  PrevisitChatReplyPayload,
  PrevisitChatResult,
  PrevisitChatStartPayload,
  PrevisitPayload,
  PrevisitRecord,
  PrevisitResult,
  PrevisitSession,
  PrevisitSummary,
  PrevisitSummaryPayload,
  PrevisitSummaryResult,
  TriageAnalyzePayload,
  TriageAnalysisDetail,
  TriageAnalysisResult,
  TriageChatPayload,
  TriageChatResult,
  TriageHistoryRecord,
} from '@/shared/types/ai'

function parseJsonField<T>(value: unknown): T | null {
  if (value == null || value === '') {
    return null
  }
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as T
    } catch {
      return null
    }
  }
  return value as T
}

function normalizeTriageHistory(record: TriageHistoryRecord): TriageHistoryRecord {
  return {
    ...record,
    aiAnalysis: parseJsonField<TriageAnalysisDetail>(record.aiAnalysis) || record.aiAnalysis || null,
  }
}

function normalizeFollowUpStatus(status: FollowUpPlan['status']): FollowUpPlan['status'] {
  if (typeof status === 'string' && /^\d+$/.test(status)) {
    return Number(status) as FollowUpPlan['status']
  }
  return status
}

function normalizeFollowUpPlan(plan: FollowUpPlan): FollowUpPlan {
  return {
    ...plan,
    followUpItems: parseJsonField<FollowUpQuestion[]>(plan.followUpItems) || [],
    status: normalizeFollowUpStatus(plan.status),
  }
}

export const aiApi = {
  get<T>(url: string, params?: Record<string, unknown>) {
    return http<T>({ url, method: 'GET', params })
  },
  post<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'POST', data })
  },
  put<T>(url: string, data?: unknown) {
    return http<T>({ url, method: 'PUT', data })
  },
  delete<T>(url: string) {
    return http<T>({ url, method: 'DELETE' })
  },
  triageAnalyze(data: TriageAnalyzePayload) {
    return http<TriageAnalysisResult>({ url: '/ai/triage/analyze', method: 'POST', data, skipErrorMessage: true })
  },
  triageChat(data: TriageChatPayload) {
    return http<TriageChatResult>({ url: '/ai/triage/chat', method: 'POST', data })
  },
  async triageRecord(id: number) {
    const result = await http<TriageHistoryRecord>({ url: `/ai/triage/record/${id}`, method: 'GET' })
    return normalizeTriageHistory(result)
  },
  async triageRecordsByPatient(patientId: number) {
    const result = await http<TriageHistoryRecord[]>({ url: `/ai/triage/records/patient/${patientId}`, method: 'GET', skipErrorMessage: true })
    return result.map(normalizeTriageHistory)
  },
  previsit(data: PrevisitPayload) {
    return http<PrevisitResult>({ url: '/ai/consult/previsit', method: 'POST', data })
  },
  previsitSummary(data: PrevisitSummaryPayload) {
    return http<PrevisitSummaryResult>({ url: '/ai/consult/summary', method: 'POST', data })
  },
  previsitChat(data: PrevisitChatPayload) {
    return http<PrevisitChatResult>({ url: '/ai/consult/chat', method: 'POST', data })
  },
  previsitRecord(id: number) {
    return http<PrevisitRecord>({ url: `/ai/consult/record/${id}`, method: 'GET', skipErrorMessage: true })
  },
  previsitRecordByRegister(registerId: number) {
    return http<PrevisitRecord>({ url: `/ai/consult/record/register/${registerId}`, method: 'GET', skipErrorMessage: true })
  },
  examAnalyze(data: ExamAnalyzePayload) {
    return http<ExamAnalyzeResult>({ url: '/ai/diagnosis/exam-analyze', method: 'POST', data, skipErrorMessage: true })
  },
  followupCreate(data: FollowUpCreatePayload) {
    return http<FollowUpCreateResult>({ url: '/ai/pharmacy/followup', method: 'POST', data })
  },
  async followupDetail(id: number) {
    const result = await http<FollowUpPlan>({ url: `/ai/pharmacy/followup/${id}`, method: 'GET' })
    return normalizeFollowUpPlan(result)
  },
  async followupPlansByPatient(patientId: number) {
    const result = await http<FollowUpPlan[]>({ url: `/ai/pharmacy/followup/patient/${patientId}`, method: 'GET' })
    return result.map(normalizeFollowUpPlan)
  },
  submitFollowupFeedback(planId: number, data: FollowUpFeedbackPayload) {
    return http<void>({ url: `/ai/pharmacy/followup/${planId}/feedback`, method: 'POST', data })
  },
  followupRecords(planId: number) {
    return http<FollowUpRecord[]>({ url: `/ai/pharmacy/followup/${planId}/records`, method: 'GET' })
  },
  medicationGuide(data: MedicationGuidePayload) {
    return http<MedicationGuideResult>({ url: '/ai/pharmacy/guide', method: 'POST', data, skipErrorMessage: true })
  },

  // ========== 对话式预问诊（SSE 流式） ==========

  /**
   * 调用 SSE 接口的统一方法
   * 解析 event: meta / event: token / event: error 三类事件
   * @param url 路径
   * @param body POST body
   * @param onToken 每个 token 片段的回调
   * @param signal 用于中断请求
   */
  async callPrevisitSSE(
    url: string,
    body: unknown,
    onToken: (chunk: string) => void,
    signal?: AbortSignal,
  ): Promise<PrevisitChatMeta> {
    const token = localStorage.getItem('access_token') || ''
    const response = await fetch(`/api${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      credentials: 'include',
      signal,
    })

    if (!response.ok || !response.body) {
      throw new Error(`SSE 请求失败: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let meta: PrevisitChatMeta = {}

    const flushEvent = (rawEvent: string) => {
      // rawEvent 形如 "event: meta\ndata: {...}\n\n" 或 "event: token\ndata: ...\n\n"
      const lines = rawEvent.split('\n')
      let eventName = ''
      const dataLines: string[] = []
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim())
        }
      }
      const data = dataLines.join('\n')
      if (eventName === 'token' && data) {
        onToken(data)
      } else if (eventName === 'meta' && data) {
        try {
          meta = JSON.parse(data)
        } catch {
          /* ignore */
        }
      } else if (eventName === 'error' && data) {
        throw new Error(data)
      }
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 用 \n\n 分隔 SSE 事件
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const rawEvent = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        if (rawEvent.trim()) flushEvent(rawEvent)
      }
    }
    return meta
  },

  previsitStart(data: PrevisitChatStartPayload, onToken: (chunk: string) => void, signal?: AbortSignal) {
    return this.callPrevisitSSE('/ai/consult/preconsult/start', data, onToken, signal)
  },

  previsitReply(data: PrevisitChatReplyPayload, onToken: (chunk: string) => void, signal?: AbortSignal) {
    return this.callPrevisitSSE('/ai/consult/preconsult/reply', data, onToken, signal)
  },

  previsitFinish(data: PrevisitChatFinishPayload) {
    return http<PrevisitSummary>({ url: '/ai/consult/preconsult/finish', method: 'POST', data })
  },

  previsitSession(registerId: number) {
    return http<PrevisitSession>({
      url: `/ai/consult/preconsult/session/${registerId}`,
      method: 'GET',
      skipErrorMessage: true,
    })
  },
}

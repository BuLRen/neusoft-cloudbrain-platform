import type {
  CopilotAgentThought,
  CopilotConfirmActionResponse,
  CopilotConfirmCompletion,
  CopilotMessage,
  CopilotPrepareActionResponse,
  CopilotRunActionResponse,
  CopilotSession,
} from '@/shared/types/copilot'
import { getAccessToken } from '@/shared/auth/tokenStorage'
import { apiUrl } from '@/config/api'

async function callCopilotSSE(
  url: string,
  body: unknown,
  onToken: (chunk: string) => void,
  onThought?: (thought: CopilotAgentThought) => void,
  signal?: AbortSignal,
): Promise<Record<string, unknown>> {
  const token = getAccessToken()
  const response = await fetch(apiUrl(url), {
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
  let meta: Record<string, unknown> = {}

  const flushEvent = (rawEvent: string) => {
    const normalized = rawEvent.replace(/\r\n/g, '\n').trim()
    if (!normalized) return
    const lines = normalized.split('\n')
    let eventName = ''
    const dataLines: string[] = []
    for (const line of lines) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5))
    }
    const data = dataLines.join('\n')
    if (eventName === 'token') {
      if (data) onToken(data)
    }
    else if (eventName === 'thought' && data && onThought) {
      try { onThought(JSON.parse(data) as CopilotAgentThought) } catch { /* ignore */ }
    }
    else if (eventName === 'meta' && data) {
      try { meta = JSON.parse(data) } catch { /* ignore */ }
    } else if (eventName === 'error' && data) throw new Error(data)
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      flushEvent(buffer.slice(0, idx))
      buffer = buffer.slice(idx + 2)
    }
  }
  if (buffer.trim()) flushEvent(buffer)
  return meta
}

function authHeaders(): Record<string, string> {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function parseJsonResponse<T>(res: Response): Promise<T> {
  const json = await res.json()
  if (!res.ok || json.code !== 200) {
    throw new Error(json.message || '请求失败')
  }
  return json.data as T
}

export const copilotApi = {
  listSessions(registerId: number) {
    return fetch(apiUrl(`/physician/ai/copilot/sessions?registerId=${registerId}`), {
      headers: authHeaders(),
      credentials: 'include',
    }).then((res) => parseJsonResponse<CopilotSession[]>(res))
  },

  createSession(registerId: number, title?: string) {
    return fetch(apiUrl('/physician/ai/copilot/sessions'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ registerId, title }),
      credentials: 'include',
    }).then((res) => parseJsonResponse<CopilotSession>(res))
  },

  deleteSession(registerId: number, sessionId: number) {
    return fetch(apiUrl(`/physician/ai/copilot/sessions/${sessionId}?registerId=${registerId}`), {
      method: 'DELETE',
      headers: authHeaders(),
      credentials: 'include',
    }).then(async (res) => {
      if (!res.ok) {
        const json = await res.json().catch(() => ({}))
        throw new Error(json.message || '删除对话失败')
      }
    })
  },

  renameSession(registerId: number, sessionId: number, title: string) {
    return fetch(apiUrl(`/physician/ai/copilot/sessions/${sessionId}/title`), {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ registerId, title }),
      credentials: 'include',
    }).then(async (res) => {
      if (!res.ok) {
        const json = await res.json().catch(() => ({}))
        throw new Error(json.message || '重命名失败')
      }
    })
  },

  history(registerId: number, sessionId: number) {
    return fetch(
      apiUrl(`/physician/ai/copilot/history?registerId=${registerId}&sessionId=${sessionId}`),
      { headers: authHeaders(), credentials: 'include' },
    ).then((res) => parseJsonResponse<CopilotMessage[]>(res))
  },

  confirmCompletions(registerId: number, sessionId: number) {
    return fetch(
      apiUrl(`/physician/ai/copilot/confirm-completions?registerId=${registerId}&sessionId=${sessionId}`),
      { headers: authHeaders(), credentials: 'include' },
    ).then((res) => parseJsonResponse<CopilotConfirmCompletion[]>(res))
  },

  clearHistory(registerId: number, sessionId: number) {
    return fetch(
      apiUrl(`/physician/ai/copilot/history?registerId=${registerId}&sessionId=${sessionId}`),
      { method: 'DELETE', headers: authHeaders(), credentials: 'include' },
    ).then(async (res) => {
      if (!res.ok) {
        const json = await res.json().catch(() => ({}))
        throw new Error(json.message || '清空对话失败')
      }
    })
  },

  chat(
    registerId: number,
    sessionId: number,
    message: string,
    onToken: (chunk: string) => void,
    onThought?: (thought: CopilotAgentThought) => void,
    signal?: AbortSignal,
  ) {
    return callCopilotSSE(
      '/physician/ai/copilot/chat',
      { registerId, sessionId, message },
      onToken,
      onThought,
      signal,
    )
  },

  async runAction(registerId: number, actionType: string): Promise<CopilotRunActionResponse> {
    const response = await fetch(apiUrl('/physician/ai/copilot/run-action'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ registerId, actionType }),
      credentials: 'include',
    })
    return parseJsonResponse<CopilotRunActionResponse>(response)
  },

  async prepareAction(
    registerId: number,
    sessionId: number,
    actionType: string,
    payload: Record<string, unknown>,
  ): Promise<CopilotPrepareActionResponse> {
    const response = await fetch(apiUrl('/physician/ai/copilot/prepare-action'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ registerId, sessionId, actionType, payload }),
      credentials: 'include',
    })
    return parseJsonResponse<CopilotPrepareActionResponse>(response)
  },

  async confirmAction(
    registerId: number,
    sessionId: number,
    confirmationToken: string,
    payloadOverride?: Record<string, unknown>,
  ): Promise<CopilotConfirmActionResponse> {
    const response = await fetch(apiUrl('/physician/ai/copilot/confirm-action'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ registerId, sessionId, confirmationToken, payloadOverride }),
      credentials: 'include',
    })
    return parseJsonResponse<CopilotConfirmActionResponse>(response)
  },
}

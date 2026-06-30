import type { CopilotMessage } from '@/shared/types/copilot'

async function callCopilotSSE(
  url: string,
  body: unknown,
  onToken: (chunk: string) => void,
  signal?: AbortSignal,
): Promise<Record<string, unknown>> {
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
    if (eventName === 'token') onToken(data)
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

export const copilotApi = {
  history(registerId: number) {
    const token = localStorage.getItem('access_token') || ''
    return fetch(`/api/physician/ai/copilot/history?registerId=${registerId}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      credentials: 'include',
    }).then(async (res) => {
      if (!res.ok) throw new Error('加载对话历史失败')
      const json = await res.json()
      return (json.data ?? []) as CopilotMessage[]
    })
  },

  clearHistory(registerId: number) {
    const token = localStorage.getItem('access_token') || ''
    return fetch(`/api/physician/ai/copilot/history?registerId=${registerId}`, {
      method: 'DELETE',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      credentials: 'include',
    }).then(async (res) => {
      if (!res.ok) throw new Error('清空对话失败')
    })
  },

  chat(registerId: number, message: string, onToken: (chunk: string) => void, signal?: AbortSignal) {
    return callCopilotSSE(
      '/physician/ai/copilot/chat',
      { registerId, message },
      onToken,
      signal,
    )
  },
}

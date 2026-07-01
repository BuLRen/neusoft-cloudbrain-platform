import type {
  AgentAction,
  AgentActionStatus,
  AgentActionType,
  AgentConfirmAction,
  CommitActionType,
  CopilotAgentThought,
  CopilotConfirmCompletion,
  CopilotMessage,
} from '@/shared/types/copilot'

const VALID_COMMIT_TYPES = new Set<CommitActionType>([
  'commit_medical_record',
  'commit_preliminary_diagnosis',
  'commit_check_requests',
  'commit_inspection_requests',
  'commit_disposal_requests',
  'commit_diagnosis',
  'commit_prescription',
  'commit_archive_visit',
])

export const COMMIT_LABELS: Record<CommitActionType, string> = {
  commit_medical_record: '保存病历',
  commit_preliminary_diagnosis: '保存初步诊断',
  commit_check_requests: '提交检查申请',
  commit_inspection_requests: '提交检验申请',
  commit_disposal_requests: '提交处置申请',
  commit_diagnosis: '提交确诊',
  commit_prescription: '提交处方',
  commit_archive_visit: '归档病历',
}

export function commitLabel(type: CommitActionType): string {
  return COMMIT_LABELS[type] ?? '确认提交'
}

const VALID_ACTION_TYPES = new Set<AgentActionType>([
  'trigger_preliminary_diagnosis',
  'trigger_w2',
  'trigger_w3',
  'trigger_w4',
  'trigger_w5',
])

/** 每次调用使用新 RegExp，避免模块级 /g 正则的 lastIndex 污染后续匹配 */
function replaceAll(text: string, pattern: RegExp, replacer: string | ((substring: string, ...args: string[]) => string)) {
  const flags = pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`
  return text.replace(new RegExp(pattern.source, flags), replacer as never)
}

function parseBlockJson(block: string): Record<string, unknown> {
  const trimmed = block.trim()
  try {
    return JSON.parse(trimmed) as Record<string, unknown>
  } catch {
    const start = trimmed.indexOf('{')
    const end = trimmed.lastIndexOf('}')
    if (start >= 0 && end > start) {
      return JSON.parse(trimmed.slice(start, end + 1)) as Record<string, unknown>
    }
    throw new Error('Invalid action block JSON')
  }
}

export function stripCompletedBlocks(content: string) {
  let text = replaceAll(content, /```action\s*[\s\S]*?\r?\n?```/, '')
  text = replaceAll(text, /```confirm\s*[\s\S]*?\r?\n?```/, '')
  return text.trim()
}

export function stripBlocksForDisplay(content: string) {
  let text = stripCompletedBlocks(content)
  const incompleteAction = text.match(/```action\b[\s\S]*$/)
  if (incompleteAction?.index !== undefined) {
    text = text.slice(0, incompleteAction.index).trim()
  }
  const incompleteConfirm = text.match(/```confirm\b[\s\S]*$/)
  if (incompleteConfirm?.index !== undefined) {
    text = text.slice(0, incompleteConfirm.index).trim()
  }
  return text
}

/** 从助手回复正文中解析 ```action``` / ```confirm``` 块 */
export function parseActionBlocks(content: string) {
  const actions: AgentAction[] = []
  const confirms: AgentConfirmAction[] = []
  const normalized = content.replace(/\r\n/g, '\n')

  const withoutActions = replaceAll(normalized, /```action\s*([\s\S]*?)\r?\n?```/, (_, json: string) => {
    try {
      const parsed = parseBlockJson(json) as Partial<AgentAction>
      if (parsed.type && VALID_ACTION_TYPES.has(parsed.type) && parsed.label) {
        actions.push({
          type: parsed.type,
          label: parsed.label,
          description: parsed.description,
          reason: parsed.reason,
        })
      }
    } catch {
      /* ignore malformed action block */
    }
    return ''
  })

  const text = replaceAll(withoutActions, /```confirm\s*([\s\S]*?)\r?\n?```/, (_, json: string) => {
    try {
      const parsed = parseBlockJson(json) as Partial<AgentConfirmAction>
      if (parsed.type && VALID_COMMIT_TYPES.has(parsed.type)) {
        const payload = parsed.payload && typeof parsed.payload === 'object' ? parsed.payload : {}
        confirms.push({
          type: parsed.type,
          label: parsed.label || commitLabel(parsed.type),
          description: parsed.description,
          reason: parsed.reason,
          payload,
        })
      }
    } catch {
      /* ignore malformed confirm block */
    }
    return ''
  })

  return { text: text.trim(), actions, confirms }
}

/** 解析历史消息里存储的 agent thoughts（兼容 string / 已解析数组） */
export function parseStoredThoughts(msg: CopilotMessage): CopilotAgentThought[] {
  if (msg.role !== 'assistant') return []
  if (msg.agentThoughts?.length) return msg.agentThoughts
  const raw = msg.toolCallsJson
  if (!raw) return []
  if (Array.isArray(raw)) return raw as CopilotAgentThought[]
  if (typeof raw !== 'string') return []
  try {
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? (parsed as CopilotAgentThought[]) : []
  } catch {
    return []
  }
}

/** 从每条 thought 文本里提取 ```confirm``` 块 */
export function extractThoughtBlockConfirms(thoughts?: CopilotAgentThought[]): AgentConfirmAction[] {
  if (!thoughts?.length) return []
  const out: AgentConfirmAction[] = []
  for (const thought of thoughts) {
    if (!thought.thought) continue
    const { confirms } = parseActionBlocks(sanitizeAgentContent(thought.thought))
    out.push(...confirms)
  }
  return out
}

function resolveThoughtConfirms(thoughts: CopilotAgentThought[]): AgentConfirmAction[] {
  const fromObservation = extractConfirmsFromThoughts(thoughts)
  const fromBlocks = extractThoughtBlockConfirms(thoughts)
  return mergeConfirms(fromBlocks, fromObservation)
}

/**
 * 统一解析助手消息：正文块 + thought 文本块 + draft observation → content / actions / confirms。
 * 返回新对象，便于 Vue 侦测变更并触发确认卡片渲染。
 */
export function enrichAssistantMessage(
  msg: CopilotMessage,
  rawContent?: string,
  options?: { display?: boolean },
): CopilotMessage {
  if (msg.role !== 'assistant') return msg

  const thoughts = parseStoredThoughts(msg)
  const raw = rawContent ?? msg.content ?? ''
  const sanitized = sanitizeAgentContent(raw)
  const { text, actions, confirms } = parseActionBlocks(sanitized)
  const mergedConfirms = mergeConfirms(confirms, resolveThoughtConfirms(thoughts))

  const content = options?.display
    ? (stripBlocksForDisplay(sanitized) || text || sanitized || msg.content)
    : (text || sanitized || msg.content)

  return {
    ...msg,
    agentThoughts: thoughts.length ? thoughts : msg.agentThoughts,
    content,
    actions: actions.length ? actions : undefined,
    confirms: mergedConfirms.length ? mergedConfirms : undefined,
  }
}

/**
 * 根据审计日志恢复「已确认提交」状态，避免离开页面后 confirmStates 丢失导致可重复点击。
 * 按消息时间与 actionType 顺序贪心匹配。
 */
export function applyConfirmCompletions(
  messages: CopilotMessage[],
  completions: CopilotConfirmCompletion[],
): Record<string, AgentActionStatus> {
  const states: Record<string, AgentActionStatus> = {}
  if (!completions.length) return states

  const pool = [...completions].sort(
    (a, b) => new Date(a.completedAt).getTime() - new Date(b.completedAt).getTime(),
  )

  messages.forEach((msg, messageIndex) => {
    if (msg.role !== 'assistant' || !msg.confirms?.length) return
    const msgTime = msg.createdAt ? new Date(msg.createdAt).getTime() : 0

    msg.confirms.forEach((confirm, confirmIndex) => {
      const matchIndex = pool.findIndex((item) => {
        if (item.actionType !== confirm.type) return false
        return new Date(item.completedAt).getTime() >= msgTime
      })
      if (matchIndex >= 0) {
        states[`confirm-${messageIndex}-${confirmIndex}`] = 'done'
        pool.splice(matchIndex, 1)
      }
    })
  })

  return states
}

/**
 * 合并「回复内 ```confirm``` 块」与「draft observation 自动生成」的确认卡：
 * 以 confirm 块为主（医生可见意图），用 observation 补全 payload / diff。
 * 同类型只保留一张卡，避免重复。
 */
export function mergeConfirms(
  blockConfirms: AgentConfirmAction[],
  autoConfirms: AgentConfirmAction[],
): AgentConfirmAction[] {
  const byType = new Map<CommitActionType, AgentConfirmAction>()
  for (const auto of autoConfirms) {
    byType.set(auto.type, auto)
  }
  for (const block of blockConfirms) {
    const auto = byType.get(block.type)
    if (!auto) {
      byType.set(block.type, block)
      continue
    }
    const blockPayload = block.payload && Object.keys(block.payload).length ? block.payload : undefined
    byType.set(block.type, {
      type: block.type,
      label: block.label || auto.label,
      description: block.description || auto.description,
      reason: block.reason || auto.reason,
      payload: blockPayload ?? auto.payload,
      diff: block.diff ?? auto.diff,
    })
  }
  return Array.from(byType.values())
}

/** 过滤模型内部思考标签，避免泄露到聊天气泡 */
export function sanitizeAgentContent(text: string) {
  return text
    .replace(/<think>[\s\S]*?<\/redacted_thinking>/gi, '')
    .replace(/\u003cthink\u003e[\s\S]*?\u003c\/think\u003e/gi, '')
    .replace(/(?:^|\s)tool_[a-z0-9_]+(?=\s|<|$)/gi, ' ')
    .replace(/[ \t]{2,}/g, ' ')
    .trim()
}

/** 尽力把字符串整体解析成 JSON */
function tryParseJson(text: string): unknown {
  const trimmed = text.trim()
  if (!trimmed) return null
  try {
    return JSON.parse(trimmed)
  } catch {
    return null
  }
}

/**
 * 从字符串中扫描出所有顶层「平衡」的 JSON 对象并解析。
 * 兼容 Dify observation 里把同一响应重复拼接的情况：{...紧凑...}{...美化...}
 * （直接 JSON.parse 整段会失败）。
 */
function extractJsonObjects(text: string): unknown[] {
  const results: unknown[] = []
  let depth = 0
  let start = -1
  let inString = false
  let escape = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (inString) {
      if (escape) escape = false
      else if (ch === '\\') escape = true
      else if (ch === '"') inString = false
      continue
    }
    if (ch === '"') {
      inString = true
    } else if (ch === '{') {
      if (depth === 0) start = i
      depth++
    } else if (ch === '}' && depth > 0) {
      depth--
      if (depth === 0 && start >= 0) {
        try {
          results.push(JSON.parse(text.slice(start, i + 1)))
        } catch {
          /* skip malformed segment */
        }
        start = -1
      }
    }
  }
  return results
}

/**
 * 递归在任意结构中寻找包含 confirmActionType 的确认节点。
 * 兼容 Dify observation 的多种包裹：
 *  - 裸 JSON：{ confirmActionType, confirmPayload, ... }
 *  - 统一响应：{ success, data: { confirmActionType, ... } }
 *  - 工具名包裹：{ "tool_draft_medical_record": "<json 字符串>" }
 *  - 数组 / 嵌套 JSON 字符串 / 多层 data
 */
function findConfirmNode(value: unknown, depth = 0): Record<string, unknown> | null {
  if (value == null || depth > 6) return null

  if (typeof value === 'string') {
    const whole = tryParseJson(value)
    if (whole !== null && whole !== value) {
      const found = findConfirmNode(whole, depth + 1)
      if (found) return found
    }
    // 整体解析失败时，逐个提取平衡 JSON 对象（兼容拼接的多段 JSON）
    for (const candidate of extractJsonObjects(value)) {
      const found = findConfirmNode(candidate, depth + 1)
      if (found) return found
    }
    return null
  }

  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findConfirmNode(item, depth + 1)
      if (found) return found
    }
    return null
  }

  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    if (isCommitType(record.confirmActionType)) {
      return record
    }
    for (const child of Object.values(record)) {
      const found = findConfirmNode(child, depth + 1)
      if (found) return found
    }
  }

  return null
}

function parseObservation(observation: string): Record<string, unknown> | null {
  return findConfirmNode(observation)
}

function isCommitType(value: unknown): value is CommitActionType {
  return typeof value === 'string' && VALID_COMMIT_TYPES.has(value as CommitActionType)
}

/** 当 Agent 未输出 ```confirm``` 块时，从 draft 工具 observation 自动生成确认卡 */
export function extractConfirmsFromThoughts(
  thoughts?: CopilotAgentThought[],
): AgentConfirmAction[] {
  if (!thoughts?.length) return []

  const confirms: AgentConfirmAction[] = []
  const seen = new Set<string>()

  for (const thought of thoughts) {
    if (!thought.observation) continue

    const data = parseObservation(thought.observation)
    if (!data) continue

    const actionType = data.confirmActionType
    if (!isCommitType(actionType)) continue

    const payload = (data.confirmPayload ?? data.proposed) as Record<string, unknown> | undefined
    const payloadObj = payload && typeof payload === 'object' ? payload : {}
    if (actionType !== 'commit_archive_visit' && !Object.keys(payloadObj).length) continue

    const diff = data.diff as Record<string, { before?: string; after?: string }> | undefined
    const key = `${actionType}:${JSON.stringify(payloadObj)}`
    if (seen.has(key)) continue
    seen.add(key)

    confirms.push({
      type: actionType,
      label: COMMIT_LABELS[actionType],
      description: typeof data.message === 'string' ? data.message : '请确认后提交',
      reason: diff && Object.keys(diff).length
        ? `共 ${Object.keys(diff).length} 处变更待确认`
        : 'Agent 已生成草案',
      payload: { ...payloadObj },
      diff,
    })
  }

  return confirms
}

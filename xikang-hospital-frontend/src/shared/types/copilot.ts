export type AgentActionType =
  | 'trigger_preliminary_diagnosis'
  | 'trigger_w2'
  | 'trigger_w3'
  | 'trigger_w4'
  | 'trigger_w5'

export interface AgentAction {
  type: AgentActionType
  label: string
  description?: string
  reason?: string
}

export type AgentActionStatus = 'pending' | 'loading' | 'done' | 'dismissed'

export interface AgentActionResult {
  actionType: string
  label: string
  success: boolean
  summary: string
  rawData?: unknown
}

export interface CopilotSession {
  id: number
  registerId: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface CopilotAgentThought {
  event?: string
  thought?: string
  tool?: string
  toolInput?: string
  observation?: string
}

export interface CopilotMessage {
  id?: number
  registerId?: number
  sessionId?: number
  role: 'user' | 'assistant' | 'tool' | 'action_result'
  content: string
  toolCallsJson?: string | null
  createdAt?: string
  actions?: AgentAction[]
  actionResult?: AgentActionResult
  /** Dify Agent 思考/工具调用状态（流式阶段） */
  agentStatus?: string
  agentThoughts?: CopilotAgentThought[]
}

export interface CopilotRunActionResponse {
  actionType: string
  registerId: number
  success: boolean
  summary: string
  data?: unknown
}

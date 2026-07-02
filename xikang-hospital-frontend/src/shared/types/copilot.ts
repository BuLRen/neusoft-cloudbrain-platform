export type AgentActionType =
  | 'trigger_preliminary_diagnosis'
  | 'trigger_w2'
  | 'trigger_w3'
  | 'trigger_w4'
  | 'trigger_w5'

export type CommitActionType =
  | 'commit_medical_record'
  | 'commit_preliminary_diagnosis'
  | 'commit_check_requests'
  | 'commit_inspection_requests'
  | 'commit_disposal_requests'
  | 'commit_diagnosis'
  | 'commit_prescription'
  | 'commit_archive_visit'

export interface AgentAction {
  type: AgentActionType
  label: string
  description?: string
  reason?: string
}

export interface AgentConfirmAction {
  type: CommitActionType
  label: string
  description?: string
  reason?: string
  payload: Record<string, unknown>
  /** 病历等字段的前后差异（来自 draft 工具 observation） */
  diff?: Record<string, { before?: string; after?: string }>
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
  /** Dify agent_thought 的稳定 id，用于流式增量合并 */
  id?: string
  position?: string | number
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
  confirms?: AgentConfirmAction[]
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

export interface CopilotPrepareActionResponse {
  confirmationToken: string
  actionType: string
  registerId: number
  expiresAt: string
  payload: Record<string, unknown>
}

export interface CopilotConfirmActionResponse {
  actionType: string
  registerId: number
  sessionId: number
  success: boolean
  summary: string
  data?: unknown
}

/** 会话内已完成的 Copilot 确认提交（来自审计日志） */
export interface CopilotConfirmCompletion {
  actionType: CommitActionType | string
  completedAt: string
}

/** 病历字段标签 */
export const MEDICAL_RECORD_FIELD_LABELS: Record<string, string> = {
  readme: '主诉',
  present: '现病史',
  presentTreat: '现病治疗',
  history: '既往史',
  allergy: '过敏史',
  physique: '体格检查',
  proposal: '检查/检验建议',
  preliminaryDiagnosis: '初步诊断',
  diagnosis: '确诊病名',
  cure: '治疗方案',
  careful: '注意事项',
  confirmedDiagnosis: '确诊病名（处方）',
}

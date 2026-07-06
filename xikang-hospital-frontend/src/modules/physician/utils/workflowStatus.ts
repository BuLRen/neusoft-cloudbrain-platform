import { physicianRoute } from '../constants/visitState'
import { physicianApi, type MedicalRecord } from '@/shared/api/modules/physician'
import type { AgentActionType, CopilotMessage } from '@/shared/types/copilot'

export type WorkflowId = 'preliminary' | 'w2' | 'w3' | 'w4' | 'w5'
export type WorkflowRunState = 'idle' | 'running' | 'completed'

export interface WorkflowCatalogItem {
  id: WorkflowId
  label: string
  prompt: string
  actionType: AgentActionType
  toolName: string
  /** 查看运行结果对应的门诊诊疗步骤页 */
  resultPath: string
}

/** 与 Copilot 可触发工作流对齐（首项为初步诊断，非 W1 病历结构化） */
export const WORKFLOW_CATALOG: WorkflowCatalogItem[] = [
  {
    id: 'preliminary',
    label: '初步诊断',
    prompt: '请根据当前患者信息运行初步诊断',
    actionType: 'trigger_preliminary_diagnosis',
    toolName: 'tool_run_preliminary_diagnosis',
    resultPath: '/physician/record',
  },
  {
    id: 'w2',
    label: 'W2 检查推荐',
    prompt: '请运行检查推荐工作流',
    actionType: 'trigger_w2',
    toolName: 'tool_run_w2',
    resultPath: '/physician/orders',
  },
  {
    id: 'w3',
    label: 'W3 结果解读',
    prompt: '请运行结果解读工作流',
    actionType: 'trigger_w3',
    toolName: 'tool_run_w3',
    resultPath: '/physician/results',
  },
  {
    id: 'w4',
    label: 'W4 确诊推理',
    prompt: '请运行确诊推理工作流',
    actionType: 'trigger_w4',
    toolName: 'tool_run_w4',
    resultPath: '/physician/diagnosis',
  },
  {
    id: 'w5',
    label: 'W5 智能荐药',
    prompt: '请运行智能荐药工作流',
    actionType: 'trigger_w5',
    toolName: 'tool_run_w5',
    resultPath: '/physician/prescription',
  },
]

const TOOL_TO_WORKFLOW: Record<string, WorkflowId> = Object.fromEntries(
  WORKFLOW_CATALOG.map((item) => [item.toolName, item.id]),
) as Record<string, WorkflowId>

const ACTION_TO_WORKFLOW: Record<string, WorkflowId> = Object.fromEntries(
  WORKFLOW_CATALOG.map((item) => [item.actionType, item.id]),
) as Record<string, WorkflowId>

export function emptyWorkflowCompletion(): Record<WorkflowId, boolean> {
  return {
    preliminary: false,
    w2: false,
    w3: false,
    w4: false,
    w5: false,
  }
}

export function hasPreliminaryWorkflowCompleted(record: MedicalRecord | null | undefined): boolean {
  if (!record) return false
  const meta = record.preliminaryAiMeta
  if (meta?.aiDiagnosis?.trim() || meta?.diagnosisBasis?.trim() || meta?.primaryDiagnosis?.trim()) {
    return true
  }
  if (meta?.clinicalSummary?.trim()) return true
  if (meta?.suggestedDiseases?.length) return true
  return false
}

export function workflowCompletionFromMessages(messages: CopilotMessage[]): Partial<Record<WorkflowId, boolean>> {
  const completed: Partial<Record<WorkflowId, boolean>> = {}

  for (const msg of messages) {
    if (msg.role === 'action_result' && msg.actionResult?.success) {
      const id = ACTION_TO_WORKFLOW[msg.actionResult.actionType]
      if (id) completed[id] = true
    }

    if (msg.role !== 'assistant' || !msg.agentThoughts?.length) continue
    for (const thought of msg.agentThoughts) {
      if (!thought.tool || !thought.observation?.trim()) continue
      const id = TOOL_TO_WORKFLOW[thought.tool]
      if (id) completed[id] = true
    }
  }

  return completed
}

export function detectRunningWorkflow(
  messages: CopilotMessage[],
  loading: boolean,
): WorkflowId | null {
  if (!loading) return null

  const lastAssistant = [...messages].reverse().find((msg) => msg.role === 'assistant')
  if (!lastAssistant) return null

  for (const thought of [...(lastAssistant.agentThoughts ?? [])].reverse()) {
    if (!thought.tool || thought.observation?.trim()) continue
    const id = TOOL_TO_WORKFLOW[thought.tool]
    if (id) return id
  }

  if (lastAssistant.agentStatus?.includes('…') || lastAssistant.agentStatus?.includes('正在')) {
    for (const item of WORKFLOW_CATALOG) {
      const label = item.label.replace(/^W\d+\s*/, '')
      if (lastAssistant.agentStatus.includes(label)) return item.id
    }
  }

  return null
}

export async function fetchWorkflowCompletion(
  registerId: number,
  record: MedicalRecord | null | undefined,
): Promise<Record<WorkflowId, boolean>> {
  const completed = emptyWorkflowCompletion()
  completed.preliminary = hasPreliminaryWorkflowCompleted(record)

  const [w2Status, w3Status, w4Status, w5Suggestions] = await Promise.all([
    physicianApi.w2Status(registerId).catch(() => null),
    physicianApi.w3Status(registerId).catch(() => null),
    physicianApi.w4Status(registerId).catch(() => null),
    physicianApi.w5Suggestions(registerId).catch(() => []),
  ])

  if (w2Status?.completed) completed.w2 = true
  if (w3Status?.completed) completed.w3 = true
  if (w4Status?.completed) completed.w4 = true
  if (w5Suggestions.length > 0) completed.w5 = true

  return completed
}

export function mergeWorkflowCompletion(
  base: Record<WorkflowId, boolean>,
  patch: Partial<Record<WorkflowId, boolean>>,
): Record<WorkflowId, boolean> {
  const merged = { ...base }
  for (const item of WORKFLOW_CATALOG) {
    if (patch[item.id]) merged[item.id] = true
  }
  return merged
}

export function resolveWorkflowState(
  id: WorkflowId,
  completed: Record<WorkflowId, boolean>,
  runningId: WorkflowId | null,
): WorkflowRunState {
  if (runningId === id) return 'running'
  if (completed[id]) return 'completed'
  return 'idle'
}

export function workflowStatusLabel(state: WorkflowRunState): string {
  switch (state) {
    case 'running':
      return '运行中'
    case 'completed':
      return '已完成'
    default:
      return '未运行'
  }
}

export function workflowResultRoute(id: WorkflowId, registerId: number) {
  const item = WORKFLOW_CATALOG.find((entry) => entry.id === id)
  if (!item) return physicianRoute('/physician/record', registerId)
  return physicianRoute(item.resultPath, registerId)
}

export interface PreliminaryAiModelOption {
  id: string
  label: string
  provider: string
  description?: string
}

export interface PreliminaryAiModelGroup {
  provider: string
  providerLabel: string
  models: PreliminaryAiModelOption[]
}

/** 初步诊断工作流可选大模型（与 Dify 工作流 inputs.model 一致） */
export const PRELIMINARY_AI_MODEL_GROUPS: PreliminaryAiModelGroup[] = [
  {
    provider: 'deepseek',
    providerLabel: '深度求索',
    models: [
      { id: 'deepseek-v4-flash', label: 'deepseek-v4-flash', provider: 'deepseek', description: '快速响应' },
      { id: 'deepseek-v4-pro', label: 'deepseek-v4-pro', provider: 'deepseek', description: '更强推理' },
      { id: 'deepseek-chat', label: 'deepseek-chat', provider: 'deepseek' },
    ],
  },
  {
    provider: 'qwen',
    providerLabel: '通义',
    models: [
      { id: 'qwen3.6-plus', label: 'qwen3.6-plus', provider: 'qwen' },
      { id: 'qwen3.6-flash', label: 'qwen3.6-flash', provider: 'qwen', description: '快速响应' },
    ],
  },
]

export const DEFAULT_PRELIMINARY_AI_MODEL = 'deepseek-v4-flash'

export const PRELIMINARY_AI_MODEL_IDS = PRELIMINARY_AI_MODEL_GROUPS.flatMap((g) => g.models.map((m) => m.id))

export function findPreliminaryAiModel(modelId: string): PreliminaryAiModelOption | undefined {
  for (const group of PRELIMINARY_AI_MODEL_GROUPS) {
    const found = group.models.find((m) => m.id === modelId)
    if (found) return found
  }
  return undefined
}

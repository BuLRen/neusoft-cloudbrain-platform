import { http } from '../request'

export interface SimulationDiseaseMapping {
  keywords: string[] | string
  hint: string
  priority?: number
}

export interface SimulationPromptSections {
  role: string
  scope: string
  itemCatalog: string
  referenceRanges: string
  normalRules: string
  abnormalRules: string
  outputFormat: string
}

export interface SimulationConfigListItem {
  id: number
  configKey: string
  techCode?: string
  checkName: string
  matchKeywords?: string
  enabled: boolean
  simulationMode: string
  version: number
  updatedAt?: string
}

export interface SimulationConfigDetail extends SimulationConfigListItem {
  promptSections: SimulationPromptSections
  diseaseMappings: SimulationDiseaseMapping[]
  outputSchema: Record<string, unknown>
  defaults: {
    notice?: string
    normalConclusion?: string
  }
  createdAt?: string
}

export type SimulationConfigPayload = {
  configKey: string
  techCode?: string
  checkName: string
  matchKeywords?: string
  enabled: boolean
  simulationMode: string
  promptSections: SimulationPromptSections
  diseaseMappings: SimulationDiseaseMapping[]
  outputSchema?: Record<string, unknown>
  defaults?: {
    notice?: string
    normalConclusion?: string
  }
}

export const simulationConfigApi = {
  list(keyword?: string) {
    return http<SimulationConfigListItem[]>({
      url: '/medtech/simulation-configs',
      method: 'GET',
      params: { keyword: keyword || undefined },
    })
  },

  get(id: number) {
    return http<SimulationConfigDetail>({
      url: `/medtech/simulation-configs/${id}`,
      method: 'GET',
    })
  },

  create(data: SimulationConfigPayload) {
    return http<SimulationConfigDetail>({
      url: '/medtech/simulation-configs',
      method: 'POST',
      data,
    })
  },

  update(id: number, data: SimulationConfigPayload) {
    return http<SimulationConfigDetail>({
      url: `/medtech/simulation-configs/${id}`,
      method: 'PUT',
      data,
    })
  },

  remove(id: number) {
    return http<void>({
      url: `/medtech/simulation-configs/${id}`,
      method: 'DELETE',
    })
  },
}

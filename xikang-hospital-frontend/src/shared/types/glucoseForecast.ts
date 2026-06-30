export interface GlucoseForecastPoint {
  forecastAt: string
  forecastValue: number
  confidence?: number
  modelId?: string
  riskLevel?: string
}

export interface GlucoseForecastResult {
  registerId: number
  metricCode?: string
  forecasts?: GlucoseForecastPoint[]
  riskLevel?: string
  modelId?: string
  confidence?: number
  observationCount?: number
  message?: string
  glucoseCohort?: boolean
}

export const GLUCOSE_RISK_LABELS: Record<string, string> = {
  low: '低风险',
  medium: '中风险',
  high: '高风险',
  unknown: '未知',
}

export const GLUCOSE_RISK_TONES: Record<string, 'success' | 'warning' | 'danger' | 'neutral'> = {
  low: 'success',
  medium: 'warning',
  high: 'danger',
  unknown: 'neutral',
}

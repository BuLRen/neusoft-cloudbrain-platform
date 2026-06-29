export type OutcomeChartType = 'line' | 'bar'

export interface OutcomeMetricChartDef {
  key: string
  label: string
  chart: OutcomeChartType
  unit?: string
}

export interface OutcomeChartProfile {
  primary: OutcomeMetricChartDef[]
  secondary: OutcomeMetricChartDef[]
}

export const METRIC_LABELS: Record<string, string> = {
  headache_score: '头痛评分',
  attack_frequency: '发作频次',
  spo2: '血氧饱和度',
  cough_score: '咳嗽评分',
  body_temperature: '体温',
  symptom_score: '症状评分',
  blood_pressure_systolic: '收缩压',
  blood_pressure_diastolic: '舒张压',
  blood_glucose: '血糖',
  insulin_total: '胰岛素',
  meal_flag: '进餐',
  exercise_flag: '运动',
  heart_rate: '心率',
  body_weight: '体重',
}

export const SYMPTOM_RELIEF_LABELS: Record<string, string> = {
  relieved: '明显缓解',
  partial: '部分缓解',
  unchanged: '无变化',
  worsened: '加重',
}

/** 缓解程度量化：用于趋势图纵轴（越高越好） */
export const SYMPTOM_RELIEF_SCORE: Record<string, number> = {
  worsened: 1,
  unchanged: 2,
  partial: 3,
  relieved: 4,
}

export const SYMPTOM_RELIEF_COLORS: Record<string, string> = {
  relieved: '#20b486',
  partial: '#1f8cff',
  unchanged: '#f59f00',
  worsened: '#ef4d5a',
  unknown: '#8ba0b6',
}

export const OUTCOME_CHART_PROFILES: Record<string, OutcomeChartProfile> = {
  神经系统疾病: {
    primary: [
      { key: 'headache_score', label: '头痛评分', chart: 'line', unit: '分' },
      { key: 'attack_frequency', label: '发作频次', chart: 'bar', unit: '次/周' },
    ],
    secondary: [
      { key: 'blood_pressure_systolic', label: '收缩压', chart: 'line', unit: 'mmHg' },
      { key: 'blood_pressure_diastolic', label: '舒张压', chart: 'line', unit: 'mmHg' },
      { key: 'heart_rate', label: '心率', chart: 'line', unit: '次/分' },
    ],
  },
  呼吸系统疾病: {
    primary: [
      { key: 'spo2', label: '血氧饱和度', chart: 'line', unit: '%' },
      { key: 'cough_score', label: '咳嗽评分', chart: 'line', unit: '分' },
      { key: 'body_temperature', label: '体温', chart: 'line', unit: '℃' },
    ],
    secondary: [
      { key: 'blood_pressure_systolic', label: '收缩压', chart: 'line', unit: 'mmHg' },
      { key: 'blood_glucose', label: '血糖', chart: 'line', unit: 'mmol/L' },
      { key: 'heart_rate', label: '心率', chart: 'line', unit: '次/分' },
      { key: 'body_weight', label: '体重', chart: 'line', unit: 'kg' },
    ],
  },
  default: {
    primary: [
      { key: 'symptom_score', label: '症状评分', chart: 'line', unit: '分' },
    ],
    secondary: [
      { key: 'blood_pressure_systolic', label: '收缩压', chart: 'line', unit: 'mmHg' },
      { key: 'blood_pressure_diastolic', label: '舒张压', chart: 'line', unit: 'mmHg' },
      { key: 'blood_glucose', label: '血糖', chart: 'line', unit: 'mmol/L' },
      { key: 'heart_rate', label: '心率', chart: 'line', unit: '次/分' },
      { key: 'body_weight', label: '体重', chart: 'line', unit: 'kg' },
    ],
  },
  代谢内分泌疾病: {
    primary: [
      { key: 'blood_glucose', label: '血糖', chart: 'line', unit: 'mmol/L' },
    ],
    secondary: [
      { key: 'insulin_total', label: '胰岛素', chart: 'line', unit: 'U' },
      { key: 'blood_pressure_systolic', label: '收缩压', chart: 'line', unit: 'mmHg' },
      { key: 'heart_rate', label: '心率', chart: 'line', unit: '次/分' },
      { key: 'body_weight', label: '体重', chart: 'line', unit: 'kg' },
    ],
  },
}

export function resolveOutcomeChartProfile(category?: string): OutcomeChartProfile {
  if (!category || category === 'default') {
    return OUTCOME_CHART_PROFILES.default
  }
  return OUTCOME_CHART_PROFILES[category] ?? OUTCOME_CHART_PROFILES.default
}

export function metricLabel(key: string) {
  return METRIC_LABELS[key] ?? key
}

/** 合并主/副视角配置，并补齐数据里出现但未配置的指标 */
export function collectAllMetricDefs(
  profile: OutcomeChartProfile,
  metricKeys: string[] = [],
  unitResolver?: (key: string) => string | undefined,
): OutcomeMetricChartDef[] {
  const map = new Map<string, OutcomeMetricChartDef>()
  for (const def of [...profile.primary, ...profile.secondary]) {
    map.set(def.key, def)
  }
  for (const key of metricKeys) {
    if (!map.has(key)) {
      map.set(key, {
        key,
        label: metricLabel(key),
        chart: 'line',
        unit: unitResolver?.(key),
      })
    }
  }
  return Array.from(map.values())
}

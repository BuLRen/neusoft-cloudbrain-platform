import type { GlucoseAdvice } from '@/shared/types/medtechFollowUp'
import type { GlucoseForecastResult } from '@/shared/types/glucoseForecast'

/** 医护端：基于预测数据生成临床解读 */
export function buildDoctorGlucoseBrief(advice: GlucoseAdvice | null | undefined): string {
  if (!advice) return ''
  const parts: string[] = []

  if (advice.recentReportCount != null) {
    if (advice.recentReportCount < 2) {
      parts.push(`近 48 小时仅录入 ${advice.recentReportCount} 次居家血糖，预测参考价值有限，建议督促患者增加监测。`)
    } else {
      parts.push(`近 48 小时共录入 ${advice.recentReportCount} 次居家血糖，数据量基本满足预测需要。`)
    }
  }

  const min = advice.forecastMin
  const max = advice.forecastMax
  if (min != null && max != null) {
    parts.push(`未来 24 小时预测区间 ${min.toFixed(1)}–${max.toFixed(1)} mmol/L。`)
    if (min < 3.9) {
      parts.push('存在低血糖风险，建议优先电话随访，必要时提醒患者提前到院并评估用药。')
    } else if (max > 10) {
      parts.push('存在高血糖风险，建议关注饮食与用药依从性，并安排复诊评估。')
    } else if (max - min >= 3.5) {
      parts.push('预测波动偏大，建议增加监测频次，重点关注餐后与夜间时段。')
    } else {
      parts.push('预测整体较平稳，可继续按现有随访计划执行。')
    }
  }

  return parts.join('')
}

/** 医护端随访处置要点 */
export function buildDoctorGlucoseActions(advice: GlucoseAdvice | null | undefined): string[] {
  if (!advice) return []
  const actions: string[] = []

  if (advice.recentReportCount != null && advice.recentReportCount < 2) {
    actions.push('提醒患者 48 小时内至少再录入 1 次血糖。')
  }
  if (advice.revisitRecommended) {
    actions.push('通过随访沟通告知患者需到院复诊，并引导其在「我的挂号」自行预约。')
    if (advice.forecastMin != null && advice.forecastMin < 3.9) {
      actions.push('关注低血糖相关症状（出汗、心悸、乏力等），必要时建议立即就医。')
    }
    if (advice.forecastMax != null && advice.forecastMax > 10) {
      actions.push('复核近期用药与饮食记录，评估是否需要调整治疗方案。')
    }
  } else if (advice.riskLevel === 'medium') {
    actions.push('保持每周至少 2 次居家血糖监测，并在沟通中跟进波动原因。')
  } else if (advice.riskLevel === 'low') {
    actions.push('维持现有监测频率，下次随访时复核趋势即可。')
  }

  return actions
}

export function forecastPointStats(forecast: GlucoseForecastResult | null | undefined) {
  const points = forecast?.forecasts ?? []
  if (!points.length) return null
  let min = Number.POSITIVE_INFINITY
  let max = Number.NEGATIVE_INFINITY
  for (const point of points) {
    const value = Number(point.forecastValue)
    min = Math.min(min, value)
    max = Math.max(max, value)
  }
  if (!Number.isFinite(min) || !Number.isFinite(max)) return null
  return { min, max, count: points.length }
}

export function patientForecastHint(forecast: GlucoseForecastResult | null | undefined): string {
  const msg = forecast?.message?.trim()
  if (!msg) return ''
  if (/不足|不可用|failed|Need at least/i.test(msg)) {
    return '录入次数还不够，请继续记录居家血糖后再查看预测。'
  }
  return ''
}

import {
  resolveStructuredOutputFromPayload,
  type SimulatedCheckStructuredOutput,
} from '@/shared/types/simulatedCheckResult'

export interface MedtechSimulationDraftRestore {
  structuredOutput: SimulatedCheckStructuredOutput | null
  isNormal: boolean | null
}

/** 从已持久化的 checkResult / inspectionResult 草稿中恢复模拟工作流输出 */
export function restoreMedtechSimulationDraft(
  storedResult?: string | null,
  defaultCheckName?: string,
): MedtechSimulationDraftRestore {
  const structuredOutput = resolveStructuredOutputFromPayload(storedResult)
  if (!structuredOutput) {
    return { structuredOutput: null, isNormal: null }
  }

  if (!structuredOutput.checkName?.trim() && defaultCheckName) {
    structuredOutput.checkName = defaultCheckName
  }

  return {
    structuredOutput,
    isNormal: structuredOutput.isNormal,
  }
}

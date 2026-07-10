import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useAuthStore } from '@/app/stores/auth'
import { apiUrl } from '@/config/api'
import { criticalValueApi, type CriticalValueAlert } from '@/shared/api/modules/criticalValue'

function parseItems(raw: CriticalValueAlert['criticalItems']): CriticalValueAlert['criticalItems'] {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return []
    }
  }
  return []
}

function normalizeAlert(alert: CriticalValueAlert): CriticalValueAlert {
  return {
    ...alert,
    criticalItems: parseItems(alert.criticalItems),
  }
}

export function useCriticalValueStream(enabled: () => boolean) {
  const authStore = useAuthStore()
  const activeAlert = ref<CriticalValueAlert | null>(null)
  const queue = ref<CriticalValueAlert[]>([])
  let es: EventSource | null = null
  let alarmTimer: ReturnType<typeof setInterval> | null = null

  function playAlarm() {
    try {
      const ctx = new AudioContext()
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = 880
      gain.gain.value = 0.08
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start()
      setTimeout(() => {
        osc.stop()
        void ctx.close()
      }, 200)
    } catch {
      // ignore
    }
  }

  function startAlarm() {
    stopAlarm()
    playAlarm()
    alarmTimer = setInterval(playAlarm, 2500)
  }

  function stopAlarm() {
    if (alarmTimer) {
      clearInterval(alarmTimer)
      alarmTimer = null
    }
  }

  function enqueueAlert(alert: CriticalValueAlert) {
    const normalized = normalizeAlert(alert)
    if (activeAlert.value?.id === normalized.id) {
      activeAlert.value = normalized
      return
    }
    if (queue.value.some((item) => item.id === normalized.id)) return
    if (!activeAlert.value) {
      activeAlert.value = normalized
      startAlarm()
      return
    }
    queue.value.push(normalized)
  }

  function removeAlert(alertId: number) {
    if (activeAlert.value?.id === alertId) {
      activeAlert.value = queue.value.shift() ?? null
      if (!activeAlert.value) stopAlarm()
      return
    }
    queue.value = queue.value.filter((item) => item.id !== alertId)
  }

  function handleSsePayload(raw: string) {
    try {
      const payload = JSON.parse(raw) as CriticalValueAlert & { alertId?: number; type?: string }
      const alert: CriticalValueAlert = {
        ...payload,
        id: payload.id ?? payload.alertId ?? 0,
        registerId: payload.registerId,
        sourceType: payload.sourceType,
        sourceId: payload.sourceId,
        status: payload.status,
      }
      if (!alert.id) return
      if (payload.type === 'CRITICAL_CLOSED' || alert.status === 'HANDLED' || alert.status === 'CLOSED') {
        removeAlert(alert.id)
        return
      }
      enqueueAlert(alert)
    } catch {
      // ignore malformed payload
    }
  }

  async function pullPending() {
    const doctorId = authStore.employeeId
    if (!doctorId) return
    try {
      const pending = await criticalValueApi.pending(doctorId)
      for (const alert of pending) {
        enqueueAlert(alert)
      }
    } catch (err) {
      console.warn('拉取待签收危急值失败:', err)
    }
  }

  function connect() {
    const doctorId = authStore.employeeId
    if (!doctorId || !enabled()) return
    es?.close()
    es = new EventSource(apiUrl(`/medtech/critical-value/stream/doctor/${doctorId}`))
    es.addEventListener('CRITICAL_NEW', (e) => handleSsePayload((e as MessageEvent).data))
    es.addEventListener('CRITICAL_ESCALATED', (e) => handleSsePayload((e as MessageEvent).data))
    es.addEventListener('CRITICAL_CLOSED', (e) => handleSsePayload((e as MessageEvent).data))
    void pullPending()
  }

  function disconnect() {
    es?.close()
    es = null
    stopAlarm()
    activeAlert.value = null
    queue.value = []
  }

  function dismissCurrent() {
    if (!activeAlert.value) return
    const currentId = activeAlert.value.id
    removeAlert(currentId)
  }

  watch(
    () => [authStore.employeeId, enabled()] as const,
    ([doctorId, isEnabled]) => {
      disconnect()
      if (doctorId && isEnabled) connect()
    },
    { immediate: true },
  )

  onMounted(() => {
    if (enabled() && authStore.employeeId) connect()
  })

  onUnmounted(disconnect)

  return {
    activeAlert,
    queue,
    dismissCurrent,
    stopAlarm,
    pullPending,
  }
}

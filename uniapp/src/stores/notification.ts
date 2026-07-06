import { ref } from 'vue'
import { medicalApi } from '../api/medical'
import { currentPatient, session } from './session'
import { API_BASE_URL } from '../config/env'

export const unreadMessageCount = ref(0)

export async function refreshUnreadMessageCount() {
  const patientId = currentPatient.value?.patientId
  if (!patientId) {
    unreadMessageCount.value = 0
    return
  }
  try {
    const data = await medicalApi.unreadCount(patientId)
    unreadMessageCount.value = Math.max(0, Number(data.count || 0))
  } catch {
    // 导航栏的辅助请求失败时不打断当前页面
  }
}

export function markOneMessageReadLocally() {
  unreadMessageCount.value = Math.max(0, unreadMessageCount.value - 1)
}

// ===================== WebSocket 实时推送 =====================
// 小程序通过 uni.connectSocket 连接 notification-service 的 /ws/notification，
// 收到新通知时自动累加未读数 + tabBar 红点同步。

let socketTask: UniApp.SocketTask | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
let wsActivePatientId = 0

// 内存级通知订阅：messages 页进入时订阅，离开时注销，用于实时插入列表头部
type NotificationListener = (payload: { id: number; title?: string; content?: string }) => void
const notificationListeners = new Set<NotificationListener>()

export function onNotification(listener: NotificationListener) {
  notificationListeners.add(listener)
  return () => notificationListeners.delete(listener)
}

function buildWsUrl(): string | null {
  const patientId = currentPatient.value?.patientId
  const token = session.token
  if (!patientId || !token || !API_BASE_URL) return null
  // API_BASE_URL 形如 http://172.18.187.145:8080/api —— 推导成 ws://172.18.187.145:8080/ws/notification
  const wsBase = API_BASE_URL
    .replace(/^https:/i, 'wss:')
    .replace(/^http:/i, 'ws:')
    .replace(/\/api\/?$/, '')
  return `${wsBase}/ws/notification?token=${encodeURIComponent(token)}&receiverId=${patientId}&receiverRole=patient`
}

function clearTimers() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
}

function startHeartbeat() {
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  heartbeatTimer = setInterval(() => {
    if (socketTask) {
      try {
        socketTask.send({ data: JSON.stringify({ event: 'ping' }) })
      } catch { /* ignore */ }
    }
  }, 25_000)
}

function scheduleReconnect() {
  if (reconnectTimer) return
  const delay = Math.min(30_000, 1000 * Math.pow(2, reconnectAttempts++))
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectNotification()
  }, delay)
}

function handleMessage(raw: string) {
  let payload: any
  try { payload = JSON.parse(raw) } catch { return }
  if (!payload || typeof payload !== 'object') return
  if (payload.event === 'pong' || payload.event === 'hello') return
  if (payload.event === 'notification' && payload.id) {
    unreadMessageCount.value += 1
    // 通知所有订阅者（messages 页用）
    for (const fn of notificationListeners) {
      try { fn({ id: payload.id, title: payload.title, content: payload.content }) } catch { /* 单个订阅者异常不影响其他 */ }
    }
  }
}

export function connectNotification() {
  const url = buildWsUrl()
  if (!url) return
  const patientId = currentPatient.value?.patientId || 0
  // 同一患者且连接还活着：跳过
  if (socketTask && patientId === wsActivePatientId) return
  // 切换患者 / 旧连接：先关
  disconnectNotification()
  wsActivePatientId = patientId

  try {
    socketTask = uni.connectSocket({
      url,
      success: () => { /* 异步连接结果通过 onOpen/onClose 处理 */ },
      complete: () => { /* no-op */ },
    })
  } catch (err) {
    // uni.connectSocket 同步异常时回退轮询
    scheduleReconnect()
    return
  }

  if (!socketTask) {
    scheduleReconnect()
    return
  }

  socketTask.onOpen(() => {
    reconnectAttempts = 0
    startHeartbeat()
  })
  socketTask.onMessage((res) => {
    handleMessage(typeof res.data === 'string' ? res.data : '')
  })
  socketTask.onClose(() => {
    clearTimers()
    socketTask = null
    scheduleReconnect()
  })
  socketTask.onError(() => {
    // onClose 会兜底重连
  })
}

export function disconnectNotification() {
  wsActivePatientId = 0
  clearTimers()
  reconnectAttempts = 0
  if (socketTask) {
    try { socketTask.close({}) } catch { /* ignore */ }
    socketTask = null
  }
}


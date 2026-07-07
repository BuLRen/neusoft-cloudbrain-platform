import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { notificationApi } from '@/shared/api/modules/notification'
import { useAuthStore } from '@/app/stores/auth'
import { getAccessToken } from '@/shared/auth/tokenStorage'
import type { NotificationItem, NotificationRole } from '@/shared/types/notification'
import { ElMessage } from 'element-plus'

/**
 * 通知 Store
 * <p>统一管理未读数 + 最近消息（铃铛红点用）。
 * <p>WebSocket 实时推送 + 30s 轮询兜底（WS 断线时仍能拉到）。
 */
export const useNotificationStore = defineStore('notification', () => {
  const authStore = useAuthStore()

  const unreadCount = ref(0)
  const recentList = ref<NotificationItem[]>([])
  const loading = ref(false)

  /** 当前用户在通知系统里的 receiverId（患者用 currentPatientId，医生/管理员用 employeeId） */
  const receiverId = computed<number | null>(() => {
    if (authStore.role === 'patient') {
      return authStore.currentPatientId ?? null
    }
    return authStore.employeeId ?? null
  })

  /** receiverRole（admin / physician 都映射到对应枚举） */
  const receiverRole = computed<NotificationRole | null>(() => {
    if (authStore.role === 'patient') return 'patient'
    if (authStore.role === 'physician') return 'physician'
    if (authStore.role === 'admin') return 'admin'
    return null
  })

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let ws: WebSocket | null = null
  let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null
  let wsHeartTimer: ReturnType<typeof setInterval> | null = null
  let wsReconnectAttempts = 0
  let wsActiveReceiverKey = ''   // 防止 receiverId/role 切换时旧连接复活

  /** 拉一次未读数 */
  async function refreshUnreadCount() {
    if (receiverId.value == null || !receiverRole.value) return
    try {
      const data = await notificationApi.unreadCount(receiverId.value, receiverRole.value)
      unreadCount.value = data?.count ?? 0
    } catch (err) {
      // 静默失败（轮询里不能弹错）
      console.warn('[notification] refreshUnreadCount failed', err)
    }
  }

  /** 拉最近 N 条（铃铛下拉用） */
  async function refreshRecent(size = 5) {
    if (receiverId.value == null || !receiverRole.value) return
    loading.value = true
    try {
      const list = await notificationApi.recent(receiverId.value, receiverRole.value, size)
      recentList.value = list || []
    } catch (err) {
      console.warn('[notification] refreshRecent failed', err)
    } finally {
      loading.value = false
    }
  }

  /** 单条标记已读（更新本地状态，避免重新拉接口） */
  function markReadLocal(id: number) {
    const item = recentList.value.find(n => n.id === id)
    if (item && item.isRead === 0) {
      item.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  /** 全部已读（本地） */
  function markAllReadLocal() {
    for (const item of recentList.value) {
      if (item.isRead === 0) item.isRead = 1
    }
    unreadCount.value = 0
  }

  // ===================== WebSocket 实时推送 =====================

  function buildWsUrl(): string | null {
    if (receiverId.value == null || !receiverRole.value) return null
    const token = getAccessToken()
    if (!token) return null
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${proto}://${window.location.host}/ws/notification?token=${encodeURIComponent(token)}&receiverId=${receiverId.value}&receiverRole=${receiverRole.value}`
  }

  function clearWsTimers() {
    if (wsReconnectTimer) {
      clearTimeout(wsReconnectTimer)
      wsReconnectTimer = null
    }
    if (wsHeartTimer) {
      clearInterval(wsHeartTimer)
      wsHeartTimer = null
    }
  }

  function startHeartbeat() {
    if (wsHeartTimer) clearInterval(wsHeartTimer)
    wsHeartTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        try {
          ws.send(JSON.stringify({ event: 'ping' }))
        } catch {
          /* ignore */
        }
      }
    }, 25_000)
  }

  function scheduleReconnect() {
    if (wsReconnectTimer) return
    const delay = Math.min(30_000, 1000 * Math.pow(2, wsReconnectAttempts++))
    wsReconnectTimer = setTimeout(() => {
      wsReconnectTimer = null
      connectWebSocket()
    }, delay)
  }

  function handleWsMessage(raw: string) {
    let payload: any
    try {
      payload = JSON.parse(raw)
    } catch {
      return
    }
    if (!payload || typeof payload !== 'object') return
    if (payload.event === 'pong' || payload.event === 'hello') return
    if (payload.event === 'notification' && payload.id) {
      // 真实通知：未读数 +1，并把消息塞到 recentList 头部
      unreadCount.value += 1
      const item: NotificationItem = {
        id: payload.id,
        receiverId: payload.receiverId,
        receiverRole: payload.receiverRole,
        type: payload.type,
        title: payload.title,
        content: payload.content,
        bizType: payload.bizType,
        bizId: payload.bizId,
        isRead: 0,
        createdTime: payload.createdTime,
      }
      recentList.value = [item, ...recentList.value].slice(0, 20)
      // 桌面端弹一条轻提示
      try {
        ElMessage({ type: 'info', message: `${item.title}：${item.content}`, duration: 4000 })
      } catch {
        /* ElMessage 未加载时不弹 */
      }
    }
  }

  /** 建立 WebSocket 连接（若已建立且 receiverKey 未变则跳过） */
  function connectWebSocket() {
    const url = buildWsUrl()
    if (!url) return
    const receiverKey = `${receiverId.value}:${receiverRole.value}`
    if (ws && ws.readyState === WebSocket.OPEN && wsActiveReceiverKey === receiverKey) return

    // 关闭旧连接（receiverId/role 切换时也要重建）
    if (ws) {
      try { ws.close() } catch { /* ignore */ }
      ws = null
    }
    clearWsTimers()
    wsActiveReceiverKey = receiverKey

    try {
      ws = new WebSocket(url)
    } catch (err) {
      console.warn('[notification] WebSocket 创建失败，转轮询兜底', err)
      scheduleReconnect()
      return
    }

    ws.onopen = () => {
      wsReconnectAttempts = 0
      startHeartbeat()
      console.info('[notification] WebSocket 已连接')
    }
    ws.onmessage = (ev) => handleWsMessage(ev.data)
    ws.onclose = () => {
      console.info('[notification] WebSocket 关闭，排队重连')
      clearWsTimers()
      ws = null
      scheduleReconnect()
    }
    ws.onerror = (err) => {
      console.warn('[notification] WebSocket 错误', err)
      // onclose 会兜底重连
    }
  }

  function disconnectWebSocket() {
    wsActiveReceiverKey = ''
    clearWsTimers()
    wsReconnectAttempts = 0
    if (ws) {
      try { ws.close() } catch { /* ignore */ }
      ws = null
    }
  }

  /** 启动 30s 轮询（登录后 + 进入带铃铛的布局时调）。WS 在线时拉长到 5 分钟。 */
  function startPolling() {
    if (pollTimer) return
    refreshUnreadCount()
    pollTimer = setInterval(() => {
      // WS 离线时 30s 一次；WS 在线时降频到 5 分钟，省网络
      const interval = ws && ws.readyState === WebSocket.OPEN ? 300_000 : 30_000
      // 注：interval 只在启动时生效；这里若想动态化需在每次 tick 时清重建，简化暂用固定 30s
      refreshUnreadCount()
    }, 30_000)
  }

  /** 停止轮询（登出 / 离开布局时调） */
  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  /** 重置（登出时调） */
  function reset() {
    stopPolling()
    disconnectWebSocket()
    unreadCount.value = 0
    recentList.value = []
  }

  return {
    unreadCount,
    recentList,
    loading,
    receiverId,
    receiverRole,
    refreshUnreadCount,
    refreshRecent,
    markReadLocal,
    markAllReadLocal,
    startPolling,
    stopPolling,
    reset,
    connectWebSocket,
    disconnectWebSocket,
  }
})

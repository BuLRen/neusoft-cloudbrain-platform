import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { notificationApi } from '@/shared/api/modules/notification'
import { useAuthStore } from '@/app/stores/auth'
import type { NotificationItem, NotificationRole } from '@/shared/types/notification'

/**
 * 通知 Store
 * <p>统一管理未读数 + 最近消息（铃铛红点用）。
 * <p>30s 轮询：组件 mount 时调 startPolling()，unmount 时调 stopPolling()。
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

  /** 启动 30s 轮询（登录后 + 进入带铃铛的布局时调） */
  function startPolling() {
    if (pollTimer) return
    refreshUnreadCount()
    pollTimer = setInterval(() => {
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
  }
})

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '@/app/stores/auth'
import { useNotificationStore } from '@/app/stores/notification'
import { notificationApi } from '@/shared/api/modules/notification'
import type { NotificationItem, NotificationType } from '@/shared/types/notification'
import { NOTIFICATION_TYPE_LABEL } from '@/shared/types/notification'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()

const dropdownVisible = ref(false)

const receiverId = computed(() => notificationStore.receiverId)
const receiverRole = computed(() => notificationStore.receiverRole)
const unread = computed(() => notificationStore.unreadCount)
const recent = computed<NotificationItem[]>(() => notificationStore.recentList)

const allMessagesPath = computed(() => {
  if (authStore.role === 'patient') return '/patient/messages'
  if (authStore.role === 'physician') return '/physician/messages'
  return '/admin/messages'
})

function typeTagType(t: NotificationType) {
  if (t === 'doctor_change') return 'warning'
  if (t === 'leave_approved' || t === 'adjust_confirmed') return 'success'
  if (t === 'leave_rejected') return 'danger'
  return 'info'
}

function formatTime(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  return d.toLocaleString('zh-CN', { hour12: false })
}

async function onDropdownOpen(visible: boolean) {
  if (!visible) return
  if (receiverId.value == null || !receiverRole.value) return
  await notificationStore.refreshRecent(5)
}

async function onClickItem(item: NotificationItem) {
  if (item.isRead === 0 && receiverId.value != null) {
    try {
      await notificationApi.markRead(item.id, receiverId.value)
      notificationStore.markReadLocal(item.id)
    } catch (err) {
      console.warn('[NotificationBell] markRead failed', err)
    }
  }
  // 关闭下拉
  dropdownVisible.value = false
  // 跳转到消息中心
  router.push(allMessagesPath.value)
}

async function markAllRead() {
  if (receiverId.value == null || !receiverRole.value) return
  try {
    await notificationApi.markAllRead(receiverId.value, receiverRole.value)
    notificationStore.markAllReadLocal()
  } catch (err) {
    console.warn('[NotificationBell] markAllRead failed', err)
  }
}

function goAllMessages() {
  dropdownVisible.value = false
  router.push(allMessagesPath.value)
}

onMounted(() => {
  notificationStore.startPolling()
  notificationStore.connectWebSocket()
})
onUnmounted(() => {
  // 注意：AppHeader 在所有布局都常驻，这里不停止轮询 / 不断开 WS。
  // 真正停止由登出动作触发（PatientLayout.logout 已处理；其他布局登出也会清 store）。
})
</script>

<template>
  <el-dropdown
    v-model:visible="dropdownVisible"
    trigger="click"
    placement="bottom-end"
    @visible-change="onDropdownOpen"
  >
    <button
      class="bell-trigger"
      :class="{ 'has-unread': unread > 0 }"
      type="button"
      aria-label="消息通知"
    >
      <span class="bell-trigger__icon">
        <el-icon><Bell /></el-icon>
      </span>
      <span class="bell-trigger__label">消息</span>
      <span v-if="unread > 0" class="bell-trigger__count">
        {{ unread > 99 ? '99+' : unread }}
      </span>
    </button>
    <template #dropdown>
      <el-dropdown-menu class="bell-dropdown">
        <div class="bell-dropdown__header">
          <span>消息通知<template v-if="unread > 0">（{{ unread }} 条未读）</template></span>
          <el-button
            v-if="unread > 0"
            text
            size="small"
            @click="markAllRead"
          >全部已读</el-button>
        </div>
        <div v-if="recent.length === 0" class="bell-dropdown__empty">
          暂无消息
        </div>
        <el-dropdown-item
          v-for="item in recent"
          :key="item.id"
          class="bell-item"
          :class="{ 'is-unread': item.isRead === 0 }"
          @click="onClickItem(item)"
        >
          <span v-if="item.isRead === 0" class="unread-dot" />
          <div class="bell-item__body">
            <div class="bell-item__title-row">
              <el-tag :type="typeTagType(item.type)" size="small" effect="light">
                {{ NOTIFICATION_TYPE_LABEL[item.type] || item.type }}
              </el-tag>
              <span class="bell-item__time">{{ formatTime(item.createdTime) }}</span>
            </div>
            <div class="bell-item__title">{{ item.title }}</div>
            <div class="bell-item__content">{{ item.content }}</div>
          </div>
        </el-dropdown-item>
        <el-dropdown-item divided class="bell-dropdown__footer" @click="goAllMessages">
          查看全部消息
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
/* ===================== 触发按钮（带文字 + 未读数 + 高亮） ===================== */
.bell-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-strong, #fff);
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all var(--duration-base, 0.18s) var(--ease-standard, ease);
  position: relative;
}
.bell-trigger:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft, #f0f7ff);
}
.bell-trigger__icon {
  display: inline-flex;
  align-items: center;
  font-size: 16px;
}
.bell-trigger__label {
  letter-spacing: 0.02em;
}
.bell-trigger__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 0 0 2px var(--color-surface-strong, #fff);
}

/* 未读 > 0 时：按钮主色背景 + 白字 + 脉动动画提醒 */
.bell-trigger.has-unread {
  background: linear-gradient(135deg, #f56c6c 0%, #e6453f 100%);
  border-color: transparent;
  color: #fff;
  animation: bell-pulse 1.6s ease-in-out infinite;
}
.bell-trigger.has-unread:hover {
  background: linear-gradient(135deg, #e6453f 0%, #c8362f 100%);
  color: #fff;
}
.bell-trigger.has-unread .bell-trigger__count {
  background: #fff;
  color: #f56c6c;
  box-shadow: none;
}
@keyframes bell-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.45);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(245, 108, 108, 0);
  }
}

:deep(.bell-dropdown) {
  width: 360px;
  max-width: 90vw;
}

.bell-dropdown__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--color-border);
}

.bell-dropdown__empty {
  padding: 28px 12px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

:deep(.bell-item) {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  align-items: flex-start;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border-light, #f0f0f0);
}
:deep(.bell-item.is-unread) {
  background: rgba(31, 140, 255, 0.05);
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  margin-top: 6px;
  flex: 0 0 auto;
}

.bell-item__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bell-item__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.bell-item__time {
  font-size: 11px;
  color: var(--color-text-muted);
}

.bell-item__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.bell-item__content {
  font-size: 12px;
  color: var(--color-text-muted);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.bell-dropdown__footer {
  text-align: center;
  color: var(--color-primary) !important;
  font-weight: 600;
}
</style>

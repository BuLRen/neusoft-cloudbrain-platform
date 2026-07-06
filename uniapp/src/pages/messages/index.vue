<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import BottomNav from '../../components/BottomNav.vue'
import ServiceIcon from '../../components/ServiceIcon.vue'
import { medicalApi, type NotificationItem } from '../../api/medical'
import { currentPatient } from '../../stores/session'
import {
  markOneMessageReadLocally,
  onNotification,
  refreshUnreadMessageCount,
} from '../../stores/notification'

type MessageTab = 'all' | 'unread' | 'service' | 'system'

const tabs: Array<{ key: MessageTab; label: string }> = [
  { key: 'all', label: '全部消息' },
  { key: 'unread', label: '未读消息' },
  { key: 'service', label: '服务消息' },
  { key: 'system', label: '系统通知' },
]

const activeTab = ref<MessageTab>('all')
const messages = ref<NotificationItem[]>([])
const loading = ref(false)
let unsubscribe: (() => void) | null = null

const filteredMessages = computed(() => {
  return messages.value.filter(item => {
    if (activeTab.value === 'all') return true
    if (activeTab.value === 'unread') return item.isRead === 0
    return messageCategory(item) === activeTab.value
  })
})

const unreadTotal = computed(() => messages.value.filter(item => item.isRead === 0).length)

function messageCategory(item: NotificationItem): 'service' | 'system' {
  const text = `${item.type || ''}${item.title || ''}${item.content || ''}`
  return /系统|公告|通知|维护|升级/.test(text) ? 'system' : 'service'
}

function messageIcon(item: NotificationItem) {
  const text = `${item.title || ''}${item.content || ''}`
  if (/报告|检验|检查|病历/.test(text)) return 'compose'
  if (/缴费|支付|订单/.test(text)) return 'wallet'
  if (/系统|通知|公告/.test(text)) return 'notification'
  return 'calendar'
}

function tone(item: NotificationItem) {
  if (item.isRead === 0) return 'green'
  if (messageCategory(item) === 'system') return 'blue'
  return 'purple'
}

function formatTime(value?: string) {
  if (!value) return ''
  const normalized = value.replace('T', ' ')
  const match = normalized.match(/(\d{2})-(\d{2}).*?(\d{2}:\d{2})/)
  if (match) return `${match[1]}-${match[2]} ${match[3]}`
  return normalized.slice(5, 16)
}

async function load() {
  const patient = currentPatient.value
  if (!patient) {
    messages.value = []
    return
  }
  loading.value = true
  try {
    const data = await medicalApi.notifications(patient.patientId)
    messages.value = Array.isArray(data.list) ? data.list : []
    await refreshUnreadMessageCount()
  } finally {
    loading.value = false
  }
}

async function open(item: NotificationItem) {
  if (item.isRead === 0 && currentPatient.value) {
    await medicalApi.markRead(item.id, currentPatient.value.patientId)
    item.isRead = 1
    markOneMessageReadLocally()
  }
  uni.showModal({
    title: item.title || '消息详情',
    content: item.content || '暂无详细内容',
    showCancel: false,
    confirmText: '知道了',
  })
}

function bindRealtime() {
  if (unsubscribe) return
  unsubscribe = onNotification(payload => {
    if (messages.value.some(m => m.id === payload.id)) return
    messages.value.unshift({
      id: payload.id,
      title: payload.title || '新消息',
      content: payload.content || '',
      type: payload.type,
      isRead: 0,
      createdTime: new Date().toISOString(),
    } as NotificationItem)
  })
}

function unbindRealtime() {
  if (!unsubscribe) return
  unsubscribe()
  unsubscribe = null
}

onShow(() => {
  bindRealtime()
  void load()
})
onHide(unbindRealtime)
</script>

<template>
  <view class="messages-page">
    <view class="ambient circle-a" />
    <view class="ambient circle-b" />
    <view class="page-head">
      <view class="headline">
        <text class="title">消息中心</text>
        <text class="subtitle">重要消息及时提醒，不错过任何动态</text>
      </view>
      <image class="hero" src="/static/messages/message-hero.svg" mode="aspectFit" />
    </view>

    <view class="notice-card">
      <view class="notice-icon">
        <ServiceIcon type="notification-filled" tone="blue" />
      </view>
      <view class="notice-copy">
        <text>重要消息及时提醒</text>
        <text>就医动态 · 系统通知 · 贴心服务</text>
      </view>
    </view>

    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        class="tab"
        :class="{ active: activeTab === tab.key }"
        @tap="activeTab = tab.key"
      >
        <text>{{ tab.label }}</text>
        <text v-if="tab.key === 'unread' && unreadTotal > 0" class="tab-dot">{{ unreadTotal }}</text>
      </view>
    </view>

    <view class="message-list">
      <view v-if="loading" class="state-card">
        <view class="pulse-icon" />
        <text>正在同步最新消息…</text>
      </view>

      <view
        v-for="item in filteredMessages"
        :key="item.id"
        class="message-card"
        :class="{ unread: item.isRead === 0 }"
        @tap="open(item)"
      >
        <view class="message-icon">
          <ServiceIcon :type="messageIcon(item)" :tone="tone(item)" />
        </view>
        <view class="message-main">
          <view class="message-top">
            <text class="message-title">{{ item.title }}</text>
            <text class="message-time">{{ formatTime(item.createdTime) }}</text>
          </view>
          <text class="message-content">{{ item.content }}</text>
        </view>
        <view class="message-tail">
          <text v-if="item.isRead === 0" class="unread-dot" />
          <text class="chevron">›</text>
        </view>
      </view>

      <view v-if="!loading && !filteredMessages.length" class="empty-card">
        <view class="empty-icon">
          <ServiceIcon type="chat" tone="blue" />
        </view>
        <text>{{ activeTab === 'unread' ? '当前没有未读消息' : '当前没有相关消息' }}</text>
        <text>新的就医动态会第一时间出现在这里</text>
      </view>
    </view>

    <BottomNav :active="2" />
  </view>
</template>

<style scoped lang="scss">
.messages-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  padding: 116rpx 34rpx calc(178rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background:
    radial-gradient(circle at 49% 7%, rgba(220, 237, 255, 0.92) 0, rgba(239, 247, 255, 0) 235rpx),
    linear-gradient(180deg, #f5fbff 0%, #fbfdff 48%, #f4f9ff 100%);
}

.ambient {
  position: absolute;
  border-radius: 999rpx;
  pointer-events: none;
  background: rgba(225, 239, 255, 0.58);
  filter: blur(1rpx);
}

.circle-a {
  left: -165rpx;
  bottom: 142rpx;
  width: 420rpx;
  height: 420rpx;
}

.circle-b {
  right: -175rpx;
  bottom: 178rpx;
  width: 405rpx;
  height: 405rpx;
}

.page-head {
  position: relative;
  z-index: 1;
  min-height: 190rpx;
  display: flex;
  align-items: flex-start;
}

.headline {
  flex: 1;
  padding-top: 2rpx;
  display: flex;
  flex-direction: column;
}

.title {
  color: #08245c;
  font-size: 43rpx;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: 0.5rpx;
}

.subtitle {
  margin-top: 18rpx;
  color: #7888a5;
  font-size: 22rpx;
  line-height: 1.4;
}

.hero {
  position: absolute;
  right: -14rpx;
  top: -18rpx;
  width: 270rpx;
  height: 214rpx;
}

.notice-card {
  position: relative;
  z-index: 1;
  height: 150rpx;
  padding: 0 32rpx;
  display: flex;
  align-items: center;
  gap: 26rpx;
  border-radius: 26rpx;
  background: linear-gradient(100deg, rgba(255, 255, 255, 0.96) 0%, rgba(236, 246, 255, 0.96) 55%, rgba(211, 231, 255, 0.9) 100%);
  box-shadow: 0 18rpx 42rpx rgba(61, 116, 183, 0.1);
  border: 1rpx solid rgba(255, 255, 255, 0.86);
}

.notice-icon {
  width: 70rpx;
  height: 70rpx;
  padding: 12rpx;
  flex: none;
  border-radius: 35rpx;
  background: rgba(229, 241, 255, 0.9);
  box-sizing: border-box;
}

.notice-copy {
  display: flex;
  flex-direction: column;
  gap: 13rpx;
}

.notice-copy text:first-child {
  color: #0b2862;
  font-size: 29rpx;
  font-weight: 760;
}

.notice-copy text:last-child {
  color: #7c8ba8;
  font-size: 22rpx;
}

.tabs {
  position: relative;
  z-index: 1;
  height: 88rpx;
  margin-top: 20rpx;
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid rgba(221, 229, 242, 0.9);
}

.tab {
  position: relative;
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  color: #6f7d98;
  font-size: 24rpx;
}

.tab.active {
  color: #2577ff;
  font-weight: 700;
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 18rpx;
  right: 18rpx;
  bottom: -1rpx;
  height: 6rpx;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #2d89ff, #61a6ff);
}

.tab-dot {
  min-width: 25rpx;
  height: 25rpx;
  padding: 0 6rpx;
  border-radius: 999rpx;
  background: #ff5d4f;
  color: #fff;
  font-size: 16rpx;
  line-height: 25rpx;
  text-align: center;
  box-sizing: border-box;
}

.message-list {
  position: relative;
  z-index: 1;
  padding-top: 28rpx;
}

.message-card {
  min-height: 150rpx;
  margin-bottom: 24rpx;
  padding: 30rpx 24rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 23rpx;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.94);
  border: 1rpx solid rgba(238, 244, 252, 0.96);
  box-shadow: 0 18rpx 45rpx rgba(45, 83, 143, 0.08);
  box-sizing: border-box;
}

.message-card.unread {
  box-shadow: 0 20rpx 50rpx rgba(50, 117, 218, 0.12);
}

.message-icon {
  width: 72rpx;
  height: 72rpx;
  padding: 10rpx;
  flex: none;
  border-radius: 24rpx;
  background: #f5f8ff;
  box-sizing: border-box;
}

.message-main {
  flex: 1;
  min-width: 0;
}

.message-top {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
}

.message-title {
  flex: 1;
  min-width: 0;
  color: #0b2862;
  font-size: 27rpx;
  line-height: 1.35;
  font-weight: 760;
}

.message-time {
  flex: none;
  color: #8794ad;
  font-size: 20rpx;
  line-height: 1.4;
}

.message-content {
  margin-top: 15rpx;
  display: -webkit-box;
  overflow: hidden;
  color: #71809a;
  font-size: 22rpx;
  line-height: 1.72;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.message-tail {
  width: 34rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 13rpx;
}

.unread-dot {
  width: 12rpx;
  height: 12rpx;
  flex: none;
  border-radius: 50%;
  background: #ff5d4f;
}

.chevron {
  color: #98a6bd;
  font-size: 48rpx;
  line-height: 1;
}

.state-card,
.empty-card {
  min-height: 178rpx;
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.78);
  border: 1rpx solid rgba(236, 243, 252, 0.9);
  box-shadow: 0 16rpx 44rpx rgba(45, 83, 143, 0.06);
  color: #8190aa;
  font-size: 23rpx;
  gap: 12rpx;
}

.empty-icon {
  width: 76rpx;
  height: 76rpx;
  padding: 14rpx;
  border-radius: 28rpx;
  background: #eef6ff;
  box-sizing: border-box;
}

.empty-card text:first-of-type {
  color: #143166;
  font-size: 27rpx;
  font-weight: 700;
}

.pulse-icon {
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #2d86ff, #65c7db);
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(0.82);
    opacity: 0.55;
  }
  50% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>

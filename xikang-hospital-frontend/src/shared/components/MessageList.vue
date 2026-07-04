<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import { useNotificationStore } from '@/app/stores/notification'
import { notificationApi } from '@/shared/api/modules/notification'
import type { NotificationItem, NotificationRole, NotificationType } from '@/shared/types/notification'
import { NOTIFICATION_TYPE_LABEL } from '@/shared/types/notification'

const props = defineProps<{
  /** 当前用户角色（决定 receiverRole / 标题） */
  role: NotificationRole
  /** 页面标题 */
  pageTitle: string
  /** 副标题 */
  pageDescription: string
}>()

const router = useRouter()

/** 默认回退目标：按角色回到对应首页（避免 router.back() 跳到登录页或外部站点） */
function fallbackPath(): string {
  if (props.role === 'patient') return '/patient/overview'
  if (props.role === 'physician') return '/physician/queue'
  return '/admin/triage'
}

/**
 * 返回上一页：
 * - vue-router 4 把当前位置记在 window.history.state.position（递增计数），
 *   如果 position > 0，说明当前 SPA session 内确实有更早的页面可回退 → router.back()
 * - 否则（直接打开 URL / 从外部跳转过来）→ router.push(fallback)
 */
function goBack() {
  const state = window.history.state
  if (state && typeof state.position === 'number' && state.position > 0) {
    router.back()
  } else {
    router.push(fallbackPath())
  }
}

const notificationStore = useNotificationStore()

const list = ref<NotificationItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const readFilter = ref<0 | 1 | ''>('')

const receiverId = computed(() => notificationStore.receiverId)
const receiverRole = computed<NotificationRole>(() => props.role)

async function load() {
  if (receiverId.value == null) return
  loading.value = true
  try {
    const data = await notificationApi.list({
      receiverId: receiverId.value,
      receiverRole: receiverRole.value,
      isRead: readFilter.value === '' ? undefined : readFilter.value,
      page: page.value,
      size: size.value,
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch (err) {
    console.warn('[MessageList] load failed', err)
  } finally {
    loading.value = false
  }
}

async function onClickMessage(item: NotificationItem) {
  if (item.isRead === 0 && receiverId.value != null) {
    try {
      await notificationApi.markRead(item.id, receiverId.value)
      item.isRead = 1
      notificationStore.markReadLocal(item.id)
    } catch (err) {
      console.warn('[MessageList] markRead failed', err)
    }
  }
}

async function markAllRead() {
  if (receiverId.value == null) return
  try {
    const res = await notificationApi.markAllRead(receiverId.value, receiverRole.value)
    ElMessage.success(`已标记 ${res?.affected ?? 0} 条为已读`)
    notificationStore.markAllReadLocal()
    await load()
  } catch (err) {
    console.warn('[MessageList] markAllRead failed', err)
  }
}

async function deleteOne(item: NotificationItem) {
  if (receiverId.value == null) return
  try {
    await ElMessageBox.confirm(`确认删除消息"${item.title}"？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await notificationApi.delete(item.id, receiverId.value)
    ElMessage.success('已删除')
    list.value = list.value.filter(n => n.id !== item.id)
    total.value = Math.max(0, total.value - 1)
    if (item.isRead === 0) notificationStore.markReadLocal(item.id)
  } catch (err) {
    console.warn('[MessageList] delete failed', err)
  }
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function onFilterChange() {
  page.value = 1
  load()
}

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
  return d.toLocaleString('zh-CN', { hour12: false })
}

// 当用户切换（receiverId 变化）时重新拉
watch(receiverId, () => load())

onMounted(load)
</script>

<template>
  <div class="message-list-page">
    <!-- 顶部返回按钮 -->
    <el-button class="back-btn" text :icon="ArrowLeft" @click="goBack">返回</el-button>

    <div class="message-list-page__header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <div class="message-list-page__actions">
        <el-button :disabled="notificationStore.unreadCount === 0" @click="markAllRead">
          全部标记已读
        </el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <GlassCard class="filter-bar">
      <el-radio-group v-model="readFilter" @change="onFilterChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button :value="0">未读</el-radio-button>
        <el-radio-button :value="1">已读</el-radio-button>
      </el-radio-group>
      <span class="filter-bar__summary">
        共 {{ total }} 条，未读 {{ notificationStore.unreadCount }} 条
      </span>
    </GlassCard>

    <GlassCard v-loading="loading" class="messages-card">
      <EmptyState v-if="!loading && list.length === 0" title="暂无消息" description="您还没有收到任何通知。" />
      <ul v-else class="msg-list">
        <li
          v-for="item in list"
          :key="item.id"
          class="msg-item"
          :class="{ 'is-unread': item.isRead === 0 }"
          @click="onClickMessage(item)"
        >
          <div class="msg-item__left">
            <span v-if="item.isRead === 0" class="unread-dot" />
            <el-tag :type="typeTagType(item.type)" size="small" effect="light">
              {{ NOTIFICATION_TYPE_LABEL[item.type] || item.type }}
            </el-tag>
          </div>
          <div class="msg-item__body">
            <div class="msg-item__title">{{ item.title }}</div>
            <div class="msg-item__content">{{ item.content }}</div>
            <div class="msg-item__time">{{ formatTime(item.createdTime) }}</div>
          </div>
          <div class="msg-item__actions" @click.stop>
            <el-button
              v-if="item.isRead === 0"
              text
              size="small"
              @click="onClickMessage(item)"
            >标已读</el-button>
            <el-button text size="small" type="danger" @click="deleteOne(item)">删除</el-button>
          </div>
        </li>
      </ul>
      <div v-if="total > size" class="pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.message-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 88%;
  max-width: 1280px;
  margin: 0 auto;
}

/* 顶部返回按钮：text 风格，无 padding 干扰 */
.back-btn {
  align-self: flex-start;
  padding-left: 0;
  font-size: 13px;
  font-weight: 500;
}

.message-list-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
}
.message-list-page__header h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: -0.01em;
}
.message-list-page__header p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 13px;
}
.message-list-page__actions {
  display: flex;
  gap: 8px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-5);
}

.filter-bar__summary {
  color: var(--color-text-muted);
  font-size: 13px;
}

.messages-card {
  padding: var(--space-3) var(--space-4);
  min-height: 300px;
}

.msg-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.msg-item {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-3);
  border-radius: var(--radius-md, 10px);
  cursor: pointer;
  transition: background var(--duration-base, 0.18s);
  border-bottom: 1px solid var(--color-border);
}
.msg-item:last-child {
  border-bottom: none;
}
.msg-item:hover {
  background: var(--color-surface-strong, #f7f9fc);
}
.msg-item.is-unread {
  background: rgba(31, 140, 255, 0.06);
}

.msg-item__left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 90px;
}
.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  display: inline-block;
}

.msg-item__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.msg-item__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
.msg-item.is-unread .msg-item__title {
  color: var(--color-primary);
}
.msg-item__content {
  font-size: 13px;
  color: var(--color-text-muted);
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-item__time {
  font-size: 12px;
  color: var(--color-text-muted);
}

.msg-item__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
}
</style>

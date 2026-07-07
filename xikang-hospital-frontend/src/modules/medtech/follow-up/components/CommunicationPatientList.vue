<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElInput } from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { FOLLOW_UP_PRIORITY_LABELS } from '@/modules/medtech/follow-up/constants/followUpPriority'
import type { FollowUpCommunicationSession } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  sessions: FollowUpCommunicationSession[]
  activeSessionId?: number
}>()

const emit = defineEmits<{
  select: [session: FollowUpCommunicationSession]
}>()

const keyword = ref('')

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return props.sessions
  return props.sessions.filter((item) => {
    const haystack = [item.realName, item.caseNumber, String(item.registerId)].filter(Boolean).join(' ').toLowerCase()
    return haystack.includes(q)
  })
})

function priorityTone(priority?: string) {
  if (priority === 'critical') return 'danger' as const
  if (priority === 'high') return 'warning' as const
  return 'primary' as const
}
</script>

<template>
  <div class="comm-patient-list">
    <ElInput v-model="keyword" clearable placeholder="搜索患者" class="comm-patient-list__search" />
    <div class="comm-patient-list__items">
      <button
        v-for="item in filtered"
        :key="item.id"
        type="button"
        class="comm-patient-list__item"
        :class="{ 'comm-patient-list__item--active': item.id === activeSessionId }"
        @click="emit('select', item)"
      >
        <div class="comm-patient-list__head">
          <strong>{{ item.realName ?? '未知' }}</strong>
          <span v-if="(item.unreadCount ?? 0) > 0" class="comm-patient-list__unread">{{ item.unreadCount }}</span>
          <StatusTag v-if="item.priorityLevel" :tone="priorityTone(item.priorityLevel)" compact>
            {{ FOLLOW_UP_PRIORITY_LABELS[item.priorityLevel] }}
          </StatusTag>
        </div>
        <span class="comm-patient-list__meta">{{ item.caseNumber ?? item.registerId }}</span>
        <p v-if="item.lastMessagePreview" class="comm-patient-list__preview">{{ item.lastMessagePreview }}</p>
      </button>
    </div>
  </div>
</template>

<style scoped>
.comm-patient-list {
  display: grid;
  gap: var(--space-3);
}

.comm-patient-list__items {
  display: grid;
  gap: var(--space-2);
  overflow-y: auto;
  max-height: min(520px, calc(100dvh - 280px));
}

.comm-patient-list__item {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  text-align: start;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.comm-patient-list__item--active {
  border-color: color-mix(in srgb, var(--color-primary) 45%, var(--color-border));
  background: var(--color-primary-soft);
}

.comm-patient-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.comm-patient-list__unread {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-danger, #e74c3c);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.comm-patient-list__meta {
  display: block;
  margin-block-start: 2px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.comm-patient-list__preview {
  margin: var(--space-1) 0 0;
  color: var(--color-text-soft);
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>

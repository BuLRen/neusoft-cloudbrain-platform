<script setup lang="ts">
import { ElButton, ElIcon } from 'element-plus'
import { CircleCheck, Close, MagicStick } from '@element-plus/icons-vue'
import type { AgentAction, AgentActionStatus } from '@/shared/types/copilot'

defineProps<{
  action: AgentAction
  status: AgentActionStatus
}>()

const emit = defineEmits<{
  confirm: []
  dismiss: []
}>()
</script>

<template>
  <div class="agent-action-card" :class="`is-${status}`">
    <div class="agent-action-card__head">
      <span class="agent-action-card__icon" aria-hidden="true">
        <ElIcon :size="16"><MagicStick /></ElIcon>
      </span>
      <div class="agent-action-card__title">
        <strong>{{ action.label }}</strong>
        <p v-if="action.description">{{ action.description }}</p>
      </div>
    </div>

    <p v-if="action.reason" class="agent-action-card__reason">{{ action.reason }}</p>

    <div v-if="status === 'pending' || status === 'loading'" class="agent-action-card__actions">
      <ElButton
        type="primary"
        size="small"
        :loading="status === 'loading'"
        @click="emit('confirm')"
      >
        确认执行
      </ElButton>
      <ElButton size="small" :disabled="status === 'loading'" @click="emit('dismiss')">
        忽略
      </ElButton>
    </div>

    <div v-else-if="status === 'done'" class="agent-action-card__done">
      <ElIcon><CircleCheck /></ElIcon>
      <span>已执行</span>
    </div>

    <div v-else class="agent-action-card__dismissed">
      <ElIcon><Close /></ElIcon>
      <span>已忽略</span>
    </div>
  </div>
</template>

<style scoped>
.agent-action-card {
  margin-block-start: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(240, 249, 255, 0.95) 0%, rgba(255, 255, 255, 0.98) 100%);
  box-shadow: inset 0 0 0 1px rgba(125, 211, 252, 0.45);
}

.agent-action-card.is-done {
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.95) 0%, rgba(255, 255, 255, 0.98) 100%);
  box-shadow: inset 0 0 0 1px rgba(110, 231, 183, 0.45);
}

.agent-action-card.is-dismissed {
  opacity: 0.65;
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.agent-action-card__head {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.agent-action-card__icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: #0284c7;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 0 0 1px rgba(125, 211, 252, 0.5);
  flex-shrink: 0;
}

.agent-action-card__title strong {
  display: block;
  font-size: 14px;
  color: #0369a1;
}

.agent-action-card__title p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.agent-action-card__reason {
  margin: var(--space-2) 0 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.agent-action-card__actions {
  display: flex;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.agent-action-card__done,
.agent-action-card__dismissed {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-block-start: var(--space-3);
  font-size: 12px;
  color: var(--color-text-muted);
}

.agent-action-card__done {
  color: #059669;
}
</style>

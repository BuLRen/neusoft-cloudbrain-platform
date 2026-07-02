<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { formatBeijingDateTime } from '@/shared/utils/beijingDate'
import { stripMarkdown } from '@/shared/utils/plainText'
import CommunicationCardDetailDialog from '@/modules/medtech/follow-up/components/CommunicationCardDetailDialog.vue'
import type { FollowUpCommunicationMessage } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  messages: FollowUpCommunicationMessage[]
  loading?: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)
const cardDialogVisible = ref(false)
const activeCard = ref<FollowUpCommunicationMessage | null>(null)

watch(
  () => props.messages,
  async () => {
    await nextTick()
    const el = containerRef.value
    if (el) el.scrollTop = el.scrollHeight
  },
  { deep: true },
)

function senderLabel(type: string) {
  if (type === 'doctor') return '医生'
  if (type === 'patient') return '患者'
  if (type === 'ai') return 'AI 助手'
  return '系统'
}

function isCardMessage(msg: FollowUpCommunicationMessage) {
  return msg.messageType === 'drug_card' || msg.messageType === 'diagnosis_card'
}

function displayContent(msg: FollowUpCommunicationMessage) {
  if (msg.messageType === 'case_summary') {
    return stripMarkdown(msg.content)
  }
  return msg.content
}

function openCard(msg: FollowUpCommunicationMessage) {
  if (!isCardMessage(msg)) return
  activeCard.value = msg
  cardDialogVisible.value = true
}
</script>

<template>
  <div ref="containerRef" class="comm-thread" v-loading="loading">
    <article
      v-for="msg in messages"
      :key="msg.id"
      class="comm-thread__bubble"
      :class="[
        `comm-thread__bubble--${msg.senderType}`,
        {
          'comm-thread__bubble--summary': msg.messageType === 'case_summary',
          'comm-thread__bubble--card': isCardMessage(msg),
        },
      ]"
      :role="isCardMessage(msg) ? 'button' : undefined"
      :tabindex="isCardMessage(msg) ? 0 : undefined"
      @click="openCard(msg)"
      @keydown.enter="openCard(msg)"
    >
      <header class="comm-thread__meta">
        <span>{{ senderLabel(msg.senderType) }}</span>
        <time>{{ formatBeijingDateTime(msg.creationTime) }}</time>
      </header>
      <p class="comm-thread__content">{{ displayContent(msg) }}</p>
      <span v-if="isCardMessage(msg)" class="comm-thread__card-hint">点击查看详情</span>
    </article>

    <CommunicationCardDetailDialog
      v-model:visible="cardDialogVisible"
      :message-type="activeCard?.messageType"
      :title="activeCard?.content"
      :card-payload="activeCard?.cardPayload"
    />
  </div>
</template>

<style scoped>
.comm-thread {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-bg-soft) 40%, var(--color-surface-strong));
  overflow-y: auto;
  overflow-x: hidden;
}

.comm-thread__bubble {
  max-width: 85%;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: #fff;
  border: 1px solid var(--color-border);
}

.comm-thread__bubble--card {
  cursor: pointer;
  border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-border));
  background: color-mix(in srgb, var(--color-primary) 6%, #fff);
}

.comm-thread__bubble--card:hover {
  border-color: var(--color-primary);
}

.comm-thread__bubble--doctor {
  margin-left: auto;
  background: color-mix(in srgb, var(--color-primary) 8%, #fff);
}

.comm-thread__bubble--patient {
  margin-right: auto;
}

.comm-thread__bubble--ai,
.comm-thread__bubble--system {
  margin-right: auto;
  background: var(--color-bg-soft);
}

.comm-thread__bubble--summary {
  max-width: 92%;
  border-style: dashed;
}

.comm-thread__meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  font-size: 12px;
  color: var(--color-text-muted);
}

.comm-thread__content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.comm-thread__card-hint {
  display: inline-block;
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--color-primary);
}
</style>

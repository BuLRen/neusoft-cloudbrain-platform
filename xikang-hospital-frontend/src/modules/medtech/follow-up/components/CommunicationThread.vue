<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { formatBeijingDateTime } from '@/shared/utils/beijingDate'
import { stripMarkdown } from '@/shared/utils/plainText'
import type { FollowUpCommunicationMessage } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  messages: FollowUpCommunicationMessage[]
  loading?: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)

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

function displayContent(msg: FollowUpCommunicationMessage) {
  if (msg.messageType === 'case_summary') {
    return stripMarkdown(msg.content)
  }
  return msg.content
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
        { 'comm-thread__bubble--summary': msg.messageType === 'case_summary' },
      ]"
    >
      <header class="comm-thread__meta">
        <span>{{ senderLabel(msg.senderType) }}</span>
        <time>{{ formatBeijingDateTime(msg.creationTime) }}</time>
      </header>
      <p class="comm-thread__content">{{ displayContent(msg) }}</p>
    </article>
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

.comm-thread__bubble--doctor {
  margin-inline-start: auto;
  background: color-mix(in srgb, var(--color-primary) 8%, #fff);
  border-color: color-mix(in srgb, var(--color-primary) 25%, var(--color-border));
}

.comm-thread__bubble--patient {
  margin-inline-end: auto;
}

.comm-thread__bubble--ai {
  margin-inline-end: auto;
  background: color-mix(in srgb, #7c5cff 6%, #fff);
  border-color: color-mix(in srgb, #7c5cff 25%, var(--color-border));
}

.comm-thread__bubble--system {
  margin-inline: auto;
  max-width: 100%;
  background: transparent;
  border-style: dashed;
  text-align: center;
}

.comm-thread__bubble--summary {
  max-width: 100%;
  background: color-mix(in srgb, #22c55e 6%, #fff);
  border-color: color-mix(in srgb, #22c55e 30%, var(--color-border));
}

.comm-thread__meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  margin-block-end: var(--space-1);
  color: var(--color-text-soft);
  font-size: 11px;
}

.comm-thread__content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
}
</style>

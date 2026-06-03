<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = withDefaults(
  defineProps<{
    source?: string
  }>(),
  { source: '' },
)

marked.setOptions({
  gfm: true,
  breaks: true,
})

const sanitizedHtml = computed(() => {
  const text = props.source?.trim()
  if (!text) return ''
  const raw = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } })
})
</script>

<template>
  <div v-if="sanitizedHtml" class="markdown-content" v-html="sanitizedHtml" />
  <p v-else class="markdown-content__empty">暂无内容</p>
</template>

<style scoped>
.markdown-content {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted, rgba(255, 255, 255, 0.04));
  line-height: 1.75;
  color: var(--color-text);
  font-size: var(--font-size-sm);
  max-height: 320px;
  overflow-y: auto;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  margin-block: var(--space-3) var(--space-2);
  font-weight: 600;
  line-height: 1.4;
}

.markdown-content :deep(h1) {
  font-size: 1.15rem;
}

.markdown-content :deep(h2) {
  font-size: 1.05rem;
}

.markdown-content :deep(p) {
  margin-block: var(--space-2);
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin-block: var(--space-2);
  padding-inline-start: 1.25rem;
}

.markdown-content :deep(li) {
  margin-block: var(--space-1);
}

.markdown-content :deep(strong) {
  font-weight: 600;
}

.markdown-content :deep(code) {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--color-border);
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  margin-block: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-border);
  overflow-x: auto;
}

.markdown-content :deep(blockquote) {
  margin-block: var(--space-2);
  padding-inline-start: var(--space-3);
  border-inline-start: 3px solid var(--color-border);
  color: var(--color-text-muted);
}

.markdown-content__empty {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
</style>

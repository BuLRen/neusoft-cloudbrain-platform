
<script setup lang="ts">
defineProps<{
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'ai' | 'neutral'
}>()
</script>

<template>
  <span class="status-tag" :data-tone="tone || 'neutral'">
    <slot />
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 var(--space-3);
  border-radius: 999px;
  color: var(--tag-color, var(--color-text-muted));
  font-size: 12px;
  font-weight: 750;
  background: var(--tag-bg, rgba(95, 114, 136, 0.12));
}

.status-tag[data-tone="primary"] {
  --tag-color: var(--color-primary-strong);
  --tag-bg: var(--color-primary-soft);
}

.status-tag[data-tone="success"] {
  --tag-color: var(--color-success);
  --tag-bg: rgba(32, 180, 134, 0.14);
}

.status-tag[data-tone="warning"] {
  --tag-color: var(--color-warning);
  --tag-bg: rgba(245, 159, 0, 0.16);
}

.status-tag[data-tone="danger"] {
  --tag-color: var(--color-danger);
  --tag-bg: rgba(239, 77, 90, 0.14);
}

.status-tag[data-tone="ai"] {
  --tag-color: var(--color-ai);
  --tag-bg: rgba(124, 92, 255, 0.14);
}
</style>


<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="page-header">
    <div>
      <p v-if="eyebrow" class="page-header__eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="page-header__description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.page-header h1 {
  font-size: clamp(24px, 2.4vw, 34px);
  line-height: 1.1;
  letter-spacing: -0.04em;
}

.page-header__eyebrow {
  margin-block-end: var(--space-1);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-header__description {
  max-width: 760px;
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.7;
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  justify-content: flex-end;
}
</style>

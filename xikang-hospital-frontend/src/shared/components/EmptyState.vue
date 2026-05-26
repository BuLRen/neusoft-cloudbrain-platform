
<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<template>
  <div class="state-card">
    <div class="state-card__mark">—</div>
    <h3>{{ title || '暂无内容' }}</h3>
    <p>{{ description || '这里预留给后续业务页面开发。' }}</p>
  </div>
</template>

<style scoped>
.state-card {
  display: grid;
  place-items: center;
  min-height: 220px;
  padding: var(--space-8);
  color: var(--color-text-muted);
  text-align: center;
}

.state-card__mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-block-end: var(--space-4);
  border-radius: 18px;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  font-size: 28px;
  font-weight: 800;
}

.state-card h3 {
  color: var(--color-text);
  font-size: 18px;
}

.state-card p {
  margin-block-start: var(--space-2);
}
</style>

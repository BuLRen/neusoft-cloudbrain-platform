<script setup lang="ts">
import { computed } from 'vue'
import { resultPayloadEntries } from '@/shared/types/resultForm'

const props = defineProps<{
  raw?: string | null
  compact?: boolean
}>()

const entries = computed(() => resultPayloadEntries(props.raw))

const compactText = computed(() => {
  if (!entries.value.length) return '-'
  if (entries.value.length === 1) return entries.value[0].value
  return entries.value.map((item) => `${item.label}: ${item.value}`).join('；')
})
</script>

<template>
  <div class="result-payload-viewer">
    <template v-if="compact">
      <span>{{ compactText }}</span>
    </template>
    <dl v-else class="result-payload-viewer__list">
      <template v-if="entries.length">
        <div v-for="item in entries" :key="item.key" class="result-payload-viewer__row">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </template>
      <span v-else class="result-payload-viewer__empty">-</span>
    </dl>
  </div>
</template>

<style scoped>
.result-payload-viewer__list {
  margin: 0;
}

.result-payload-viewer__row + .result-payload-viewer__row {
  margin-block-start: var(--space-2);
}

.result-payload-viewer__row dt {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.result-payload-viewer__row dd {
  margin: var(--space-1) 0 0;
  white-space: pre-wrap;
}

.result-payload-viewer__empty {
  color: var(--color-text-muted);
}
</style>

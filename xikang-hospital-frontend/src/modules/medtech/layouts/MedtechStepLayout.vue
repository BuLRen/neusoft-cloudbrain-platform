<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDivider, ElSteps, ElStep } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'

const props = withDefaults(
  defineProps<{
    step: number
    totalSteps: number
    title: string
    description?: string
    prevPath?: string
    nextPath?: string
    showSteps?: boolean
    moduleLabel?: string
  }>(),
  { showSteps: true, moduleLabel: '医技管理' },
)

const router = useRouter()
const eyebrow = computed(() =>
  props.showSteps
    ? `${props.moduleLabel} · 第 ${props.step}/${props.totalSteps} 步`
    : props.moduleLabel,
)
</script>

<template>
  <div class="u-page-grid">
    <PageHeader :title="title" :description="description" :eyebrow="eyebrow">
      <template v-if="$slots['header-actions']" #actions>
        <slot name="header-actions" />
      </template>
    </PageHeader>

    <GlassCard class="step-layout__panel">
      <template v-if="showSteps">
        <ElSteps :active="step - 1" align-center finish-status="success">
          <ElStep v-for="n in totalSteps" :key="n" :title="`第 ${n} 步`" />
        </ElSteps>
        <ElDivider />
      </template>
      <slot />
      <div class="step-layout__nav">
        <ElButton v-if="prevPath" @click="router.push(prevPath)">上一步</ElButton>
        <ElButton v-if="nextPath" type="primary" @click="router.push(nextPath)">下一步</ElButton>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.step-layout__panel {
  padding: var(--space-5);
  min-width: 0;
  overflow: hidden;
}

.step-layout__panel :deep(.el-steps) {
  padding-inline: var(--space-2);
}

.step-layout__nav {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-5);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}
</style>


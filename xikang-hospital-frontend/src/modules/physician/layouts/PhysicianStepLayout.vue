<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDescriptions, ElDescriptionsItem } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import { useEncounterStore } from '@/app/stores/encounter'

const props = defineProps<{
  groupLabel: string
  title: string
  description?: string
  prevPath?: string
  nextPath?: string
}>()

const router = useRouter()
const encounterStore = useEncounterStore()

const eyebrow = computed(() => props.groupLabel)
</script>

<template>
  <div class="u-page-grid">
    <PageHeader :title="title" :description="description" :eyebrow="eyebrow">
      <template #actions>
        <slot name="headerActions" />
      </template>
    </PageHeader>

    <GlassCard class="step-layout__summary">
      <template v-if="encounterStore.hasEncounter && encounterStore.patientSummary">
        <ElDescriptions :column="4" border>
          <ElDescriptionsItem label="患者">{{ encounterStore.patientSummary.realName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="病历号">{{ encounterStore.patientSummary.caseNumber }}</ElDescriptionsItem>
          <ElDescriptionsItem label="性别">{{ encounterStore.patientSummary.gender || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="年龄">{{ encounterStore.patientSummary.age ?? '-' }}</ElDescriptionsItem>
        </ElDescriptions>
        <p v-if="encounterStore.aiConsultSummary?.aiSummary || encounterStore.aiConsultSummary?.chiefComplaint" class="step-layout__ai">
          <strong>AI 预问诊摘要：</strong>
          <span>{{ encounterStore.aiConsultSummary.aiSummary || encounterStore.aiConsultSummary.chiefComplaint }}</span>
        </p>
      </template>
      <template v-else>
        <EmptyState title="尚未选择就诊患者" description="请先从「待诊接诊」页面选择患者进入流程。" />
        <div class="step-layout__actions">
          <ElButton type="primary" @click="router.push('/physician/queue')">返回待诊接诊</ElButton>
        </div>
      </template>
    </GlassCard>

    <GlassCard class="step-layout__panel">
      <slot />
      <div class="step-layout__nav">
        <ElButton v-if="prevPath" @click="router.push(prevPath)">上一步</ElButton>
        <ElButton v-if="nextPath" type="primary" @click="router.push(nextPath)">下一步</ElButton>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.step-layout__summary,
.step-layout__panel {
  padding: var(--space-5);
}

.step-layout__ai {
  margin-block-start: var(--space-3);
  color: var(--color-text-muted);
  line-height: 1.8;
}

.step-layout__actions {
  display: flex;
  justify-content: center;
  margin-block-start: var(--space-4);
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


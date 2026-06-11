<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElIcon } from 'element-plus'
import { Calendar, Document, Tickets, User } from '@element-plus/icons-vue'
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
  patientCardVariant?: 'default' | 'profile'
}>()

const router = useRouter()
const encounterStore = useEncounterStore()

const eyebrow = computed(() => props.groupLabel)
const patientSummary = computed(() => encounterStore.patientSummary)
const patientSummaryText = computed(() => {
  const summary = encounterStore.aiConsultSummary
  return summary?.aiSummary || summary?.chiefComplaint || ''
})
const patientBadge = computed(() => {
  const patient = patientSummary.value
  if (!patient) return ''
  const parts = [patient.gender, patient.age != null ? `${patient.age}岁` : ''].filter(Boolean)
  return parts.join(' / ')
})
</script>

<template>
  <div class="u-page-grid">
    <PageHeader :title="title" :description="description" :eyebrow="eyebrow">
      <template #actions>
        <slot name="headerActions" />
      </template>
    </PageHeader>

    <GlassCard class="step-layout__summary">
      <template v-if="encounterStore.hasEncounter && patientSummary">
        <div v-if="patientCardVariant === 'profile'" class="patient-profile">
          <div class="patient-profile__main">
            <div class="patient-profile__avatar" aria-hidden="true">
              <ElIcon :size="28"><User /></ElIcon>
            </div>
            <div class="patient-profile__identity">
              <div class="patient-profile__name-row">
                <h2 class="patient-profile__name">{{ patientSummary.realName }}</h2>
                <span v-if="patientBadge" class="patient-profile__badge">{{ patientBadge }}</span>
              </div>
            </div>
            <div class="patient-profile__stats">
              <div class="patient-profile__stat">
                <span class="patient-profile__stat-icon" aria-hidden="true">
                  <ElIcon><Tickets /></ElIcon>
                </span>
                <div>
                  <span class="patient-profile__stat-label">病历号</span>
                  <strong class="patient-profile__stat-value">{{ patientSummary.caseNumber || '-' }}</strong>
                </div>
              </div>
              <div class="patient-profile__divider" aria-hidden="true" />
              <div class="patient-profile__stat">
                <span class="patient-profile__stat-icon" aria-hidden="true">
                  <ElIcon><User /></ElIcon>
                </span>
                <div>
                  <span class="patient-profile__stat-label">性别</span>
                  <strong class="patient-profile__stat-value">{{ patientSummary.gender || '-' }}</strong>
                </div>
              </div>
              <div class="patient-profile__divider" aria-hidden="true" />
              <div class="patient-profile__stat">
                <span class="patient-profile__stat-icon" aria-hidden="true">
                  <ElIcon><Calendar /></ElIcon>
                </span>
                <div>
                  <span class="patient-profile__stat-label">年龄</span>
                  <strong class="patient-profile__stat-value">{{ patientSummary.age ?? '-' }}</strong>
                </div>
              </div>
            </div>
          </div>
          <div v-if="patientSummaryText" class="patient-profile__summary">
            <ElIcon class="patient-profile__summary-icon" aria-hidden="true"><Document /></ElIcon>
            <strong class="patient-profile__summary-label">患者摘要</strong>
            <span class="patient-profile__summary-text">{{ patientSummaryText }}</span>
          </div>
        </div>
        <template v-else>
          <ElDescriptions :column="4" border>
            <ElDescriptionsItem label="患者">{{ patientSummary.realName }}</ElDescriptionsItem>
            <ElDescriptionsItem label="病历号">{{ patientSummary.caseNumber }}</ElDescriptionsItem>
            <ElDescriptionsItem label="性别">{{ patientSummary.gender || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="年龄">{{ patientSummary.age ?? '-' }}</ElDescriptionsItem>
          </ElDescriptions>
          <p v-if="patientSummaryText" class="step-layout__ai">
            <strong>患者摘要：</strong>
            <span>{{ patientSummaryText }}</span>
          </p>
        </template>
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

.patient-profile__main {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  flex-wrap: wrap;
}

.patient-profile__avatar {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  color: var(--color-primary-strong);
  background: linear-gradient(145deg, rgba(31, 140, 255, 0.18), rgba(47, 216, 196, 0.12));
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.12);
}

.patient-profile__identity {
  flex: 1 1 180px;
  min-width: 0;
}

.patient-profile__name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.patient-profile__name {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.patient-profile__badge {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 var(--space-3);
  border-radius: 999px;
  color: var(--color-primary-strong);
  font-size: 13px;
  font-weight: 600;
  background: var(--color-primary-soft);
}

.patient-profile__stats {
  display: flex;
  align-items: center;
  flex: 1 1 360px;
  gap: var(--space-4);
  justify-content: flex-end;
}

.patient-profile__stat {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.patient-profile__stat-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: var(--color-primary);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.patient-profile__stat-label {
  display: block;
  color: var(--color-text-soft);
  font-size: 12px;
}

.patient-profile__stat-value {
  display: block;
  margin-block-start: 2px;
  font-size: 15px;
  font-weight: 700;
}

.patient-profile__divider {
  width: 1px;
  height: 42px;
  background: var(--color-border);
}

.patient-profile__summary {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-block-start: var(--space-5);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-md);
  color: var(--color-text);
  line-height: 1.7;
  background: linear-gradient(90deg, rgba(31, 140, 255, 0.1), rgba(47, 216, 196, 0.06));
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.1);
}

.patient-profile__summary-icon {
  flex-shrink: 0;
  margin-block-start: 2px;
  color: var(--color-primary);
}

.patient-profile__summary-label {
  flex-shrink: 0;
  color: var(--color-primary-strong);
}

.patient-profile__summary-text {
  color: var(--color-text-muted);
}

@media (max-width: 900px) {
  .patient-profile__stats {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .patient-profile__divider {
    display: none;
  }
}
</style>

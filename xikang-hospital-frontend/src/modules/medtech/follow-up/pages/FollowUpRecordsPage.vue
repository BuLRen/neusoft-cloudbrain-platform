<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElEmpty, ElOption, ElSelect, ElTag, ElTimeline, ElTimelineItem } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { formatBeijingDateTime } from '@/shared/utils/beijingDate'
import { formatHistoryEventSummary, formatHistoryEventTitle } from '@/shared/utils/followUpHistoryDisplay'
import type { FollowUpHistoryEvent, FollowUpHistoryEventType, FollowUpPatientOption } from '@/shared/types/medtechFollowUp'

const route = useRoute()

const loading = ref(false)
const patients = ref<FollowUpPatientOption[]>([])
const events = ref<FollowUpHistoryEvent[]>([])
const selectedRegisterId = ref<number | undefined>()
const selectedEventType = ref<FollowUpHistoryEventType | ''>('')

const EVENT_LABELS: Record<FollowUpHistoryEventType, string> = {
  patient_feedback: '患者反馈',
  glucose_entry: '血糖录入',
  observation_confirmed: '观察确认',
  interview_scheduled: '访谈安排',
  interview_completed: '访谈完成',
  communication_message: '医患沟通',
  drug_card: '荐药卡片',
  diagnosis_card: '病况卡片',
  case_summary: '病例总结',
  revisit_reminder: '复诊提醒',
  forecast_alert: '血糖预警',
}

const eventTypeOptions = computed(() =>
  Object.entries(EVENT_LABELS).map(([value, label]) => ({ value: value as FollowUpHistoryEventType, label })),
)

function eventTone(type: FollowUpHistoryEventType) {
  if (type === 'glucose_entry' || type === 'forecast_alert') return 'warning'
  if (type === 'drug_card' || type === 'diagnosis_card') return 'success'
  if (type === 'patient_feedback') return 'primary'
  return 'info'
}

async function loadPatients() {
  patients.value = await medtechFollowUpApi.listPatients()
  if (!selectedRegisterId.value && patients.value.length) {
    selectedRegisterId.value = patients.value[0]!.registerId
  }
}

async function loadEvents() {
  loading.value = true
  try {
    events.value = await medtechFollowUpApi.listHistoryEvents({
      registerId: selectedRegisterId.value,
      eventType: selectedEventType.value || undefined,
      limit: 200,
    })
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query.registerId,
  (value) => {
    const id = Number(value)
    if (id) selectedRegisterId.value = id
  },
  { immediate: true },
)

watch([selectedRegisterId, selectedEventType], () => {
  void loadEvents()
})

onMounted(async () => {
  await loadPatients()
  await loadEvents()
})
</script>

<template>
  <div class="records-page">
    <PageHeader
      title="随访记录"
      description="按时间线查看患者在管期间的反馈、血糖、沟通卡片与访谈安排（数据来自业务表，不含 AI 占位表）。"
    />

    <GlassCard class="records-page__toolbar" v-loading="loading">
      <div class="records-page__filters">
        <span class="records-page__label">患者</span>
        <ElSelect v-model="selectedRegisterId" filterable clearable placeholder="全部在管患者" class="records-page__select">
          <ElOption
            v-for="item in patients"
            :key="item.registerId"
            :label="`${item.realName ?? '未知'}（${item.caseNumber ?? item.registerId}）`"
            :value="item.registerId"
          />
        </ElSelect>
        <span class="records-page__label">事件类型</span>
        <ElSelect v-model="selectedEventType" clearable placeholder="全部类型" class="records-page__select">
          <ElOption v-for="item in eventTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </ElSelect>
      </div>

      <ElTimeline v-if="events.length" class="records-page__timeline">
        <ElTimelineItem
          v-for="item in events"
          :key="item.id"
          :timestamp="formatBeijingDateTime(item.occurredAt)"
          placement="top"
        >
          <div class="records-page__item">
            <div class="records-page__item-head">
              <strong>{{ formatHistoryEventTitle(item) }}</strong>
              <ElTag size="small" :type="eventTone(item.eventType)" effect="plain">
                {{ EVENT_LABELS[item.eventType] ?? item.eventType }}
              </ElTag>
            </div>
            <p class="records-page__summary">{{ formatHistoryEventSummary(item) }}</p>
            <p v-if="item.patientName" class="records-page__meta">
              {{ item.patientName }} · {{ item.caseNumber }}
            </p>
          </div>
        </ElTimelineItem>
      </ElTimeline>
      <ElEmpty v-else description="暂无随访历史记录" />
    </GlassCard>
  </div>
</template>

<style scoped>
.records-page {
  display: grid;
  gap: var(--space-4);
}

.records-page__toolbar {
  padding: var(--space-5);
}

.records-page__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.records-page__label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.records-page__select {
  min-width: 220px;
}

.records-page__item {
  display: grid;
  gap: var(--space-2);
}

.records-page__item-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.records-page__summary {
  margin: 0;
  line-height: 1.6;
  color: var(--color-text);
}

.records-page__meta {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>

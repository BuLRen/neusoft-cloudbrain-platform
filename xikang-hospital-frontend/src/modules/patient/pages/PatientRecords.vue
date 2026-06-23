<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import ClinicalRecordAffix from '@/modules/physician/components/ClinicalRecordAffix.vue'
import { clinicalRecordApi, type ClinicalVisitSummary } from '@/shared/api/modules/clinicalRecord'
import { useAuthStore } from '@/app/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const records = ref<ClinicalVisitSummary[]>([])
const loading = ref(false)
const selectedRegisterId = ref<number | null>(null)
const affixRef = ref<InstanceType<typeof ClinicalRecordAffix> | null>(null)

const patientId = computed(() => authStore.currentPatientId || authStore.currentPatient?.patientId)

const selectedRecord = computed(() =>
  records.value.find((record) => record.registerId === selectedRegisterId.value) ?? null,
)

const affixSubtitle = computed(() => {
  const record = selectedRecord.value
  if (!record) return ''
  return [record.departmentName, record.physicianName].filter(Boolean).join(' · ')
})

async function loadRecords() {
  if (!patientId.value) return
  loading.value = true
  try {
    records.value = await clinicalRecordApi.patientVisits(patientId.value)
    if (selectedRegisterId.value && !records.value.some((item) => item.registerId === selectedRegisterId.value)) {
      selectedRegisterId.value = null
    }
  } catch (error) {
    console.warn('加载电子病历失败:', error)
  } finally {
    loading.value = false
  }
}

function formatDate(record: ClinicalVisitSummary) {
  const raw = record.visitDate
  if (!raw) return '-'
  return String(raw).slice(0, 10)
}

function selectRecord(record: ClinicalVisitSummary) {
  selectedRegisterId.value = record.registerId
}

function openDetail(record: ClinicalVisitSummary) {
  selectRecord(record)
  router.push({ name: 'PatientVisitRecord', params: { registerId: record.registerId } })
}

function openRecordDrawer(record: ClinicalVisitSummary) {
  selectRecord(record)
  affixRef.value?.open()
}

onMounted(loadRecords)
</script>

<template>
  <div class="patient-records">
    <GlassCard class="records-list">
      <div class="list-header">
        <div>
          <h2>电子病历</h2>
          <p>选择一条就诊记录后，点击右下角「病历本」或「查看病历」打开抽屉查看详情。</p>
        </div>
        <button class="btn-outline" @click="router.push('/patient/clinical-profile')">长期健康档案</button>
      </div>

      <div v-if="loading" class="empty-state">
        <p>正在加载电子病历...</p>
      </div>

      <div v-else class="record-items">
        <div
          v-for="record in records"
          :key="record.registerId"
          class="record-item"
          :class="{ 'record-item--active': selectedRegisterId === record.registerId }"
          @click="selectRecord(record)"
        >
          <div class="record-date">
            <span class="date-day">{{ formatDate(record).split('-')[2] || '-' }}</span>
            <span class="date-month">{{ formatDate(record).slice(0, 7) }}</span>
          </div>
          <div class="record-info">
            <div class="record-main">
              <span class="record-dept">{{ record.departmentName || '未分配科室' }}</span>
              <span class="record-doctor">{{ record.physicianName || '待分配医生' }}</span>
              <StatusTag :tone="record.archived ? 'success' : 'warning'">
                {{ record.archived ? '已归档' : '待医生归档' }}
              </StatusTag>
            </div>
            <div class="record-diagnosis">
              <span>病历号：{{ record.caseNumber || record.registerId }}</span>
              <span v-if="record.archived && record.diagnosis">诊断：{{ record.diagnosis }}</span>
              <span v-else-if="!record.archived">完整病历将在医生归档后开放查看</span>
            </div>
          </div>
          <div class="record-actions">
            <button class="btn-outline" @click.stop="openRecordDrawer(record)">查看病历</button>
            <button class="btn-outline" @click.stop="openDetail(record)">查看详情</button>
          </div>
        </div>
      </div>

      <div v-if="!loading && !records.length" class="empty-state">
        <p>暂无电子病历</p>
        <span>完成挂号并就诊后，医生归档的病历会显示在这里。</span>
      </div>
    </GlassCard>

    <ClinicalRecordAffix
      ref="affixRef"
      :register-id="selectedRegisterId"
      mode="patient"
      :disabled="!selectedRegisterId"
      disabled-hint="请先选择一条就诊记录"
      :subtitle="affixSubtitle"
    />
  </div>
</template>

<style scoped>
.patient-records {
  display: grid;
  gap: var(--space-4);
}

.records-list {
  padding: var(--space-5);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-4);
  margin-block-end: var(--space-5);
}

.list-header h2 {
  margin: 0 0 var(--space-2);
}

.list-header p {
  margin: 0;
  color: var(--color-text-muted);
}

.record-items {
  display: grid;
  gap: var(--space-3);
}

.record-item {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.55);
  box-shadow: inset 0 0 0 1px var(--color-border);
  cursor: pointer;
  transition: box-shadow 0.2s ease, background 0.2s ease;
}

.record-item--active {
  background: rgba(31, 140, 255, 0.08);
  box-shadow: inset 0 0 0 2px rgba(31, 140, 255, 0.35);
}

.record-date {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.date-day {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary-strong);
}

.date-month {
  font-size: 12px;
  color: var(--color-text-soft);
}

.record-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-block-end: var(--space-2);
}

.record-dept {
  font-weight: 600;
}

.record-doctor,
.record-diagnosis {
  color: var(--color-text-muted);
  font-size: 14px;
}

.record-diagnosis {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.record-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.btn-outline {
  min-height: 36px;
  padding: 0 var(--space-4);
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  cursor: pointer;
}

.empty-state {
  padding: var(--space-8) var(--space-4);
  text-align: center;
  color: var(--color-text-muted);
}

@media (max-width: 720px) {
  .record-item {
    grid-template-columns: 1fr;
  }

  .record-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>

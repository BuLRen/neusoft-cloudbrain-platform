<script setup lang="ts">
import { onMounted, ref } from 'vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import type { RegistrationRecord } from '@/shared/types/registration'
import { useAuthStore } from '@/app/stores/auth'

const authStore = useAuthStore()
const records = ref<RegistrationRecord[]>([])
const loading = ref(false)

async function loadRecords() {
  const patientId = authStore.currentPatientId || authStore.currentPatient?.patientId
  if (!patientId) return
  loading.value = true
  try {
    records.value = await registrationApi.registrationsByPatient(patientId)
  } catch (error) {
    console.warn('加载电子病历失败:', error)
  } finally {
    loading.value = false
  }
}

function formatDate(record: RegistrationRecord) {
  return record.visitDate || record.createTime?.slice(0, 10) || '-'
}

function statusText(record: RegistrationRecord) {
  return record.statusName || record.payStatusName || '待就诊'
}

onMounted(loadRecords)
</script>

<template>
  <div class="patient-records">
    <GlassCard class="records-list">
      <div class="list-header">
        <h2>就诊记录</h2>
        <p>按一次挂号/就诊形成一份电子病历入口，医生开具病历、检查、处方后可继续对接展示。</p>
      </div>

      <div v-if="loading" class="empty-state">
        <p>正在加载电子病历...</p>
      </div>

      <div v-else class="record-items">
        <div v-for="record in records" :key="record.id" class="record-item">
          <div class="record-date">
            <span class="date-day">{{ formatDate(record).split('-')[2] || '-' }}</span>
            <span class="date-month">{{ formatDate(record).slice(0, 7) }}</span>
          </div>
          <div class="record-info">
            <div class="record-main">
              <span class="record-dept">{{ record.departmentName || '未分配科室' }}</span>
              <span class="record-doctor">{{ record.physicianName || '待分配医生' }}</span>
              <StatusTag tone="primary">{{ statusText(record) }}</StatusTag>
            </div>
            <div class="record-diagnosis">
              <span>挂号单号：{{ record.id }}</span>
              <span v-if="record.complaint">主诉：{{ record.complaint }}</span>
            </div>
            <div class="record-tags">
              <StatusTag tone="warning">病历待医生端对接</StatusTag>
              <StatusTag tone="neutral">检查报告待对接</StatusTag>
              <StatusTag tone="primary">处方入口已预留</StatusTag>
            </div>
          </div>
          <div class="record-actions">
            <button class="btn-outline">查看详情</button>
          </div>
        </div>
      </div>

      <div v-if="!loading && !records.length" class="empty-state">
        <p>暂无电子病历</p>
        <span>完成挂号后会先形成就诊入口，医生开具诊断、处方、检查单后再补充完整内容。</span>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.patient-records {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.records-list {
  padding: var(--space-5);
}

.list-header {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.list-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.list-header p {
  color: var(--color-text-muted);
  margin: 0;
}

.record-items {
  display: grid;
  gap: var(--space-4);
}

.record-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.record-date {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  text-align: center;
}

.date-day {
  font-size: 20px;
  font-weight: 700;
}

.date-month {
  font-size: 11px;
  color: var(--color-text-muted);
}

.record-info {
  flex: 1;
  display: grid;
  gap: var(--space-2);
}

.record-main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.record-dept {
  font-size: 16px;
  font-weight: 600;
}

.record-doctor {
  color: var(--color-text-muted);
}

.record-diagnosis {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}

.record-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.record-actions {
  display: flex;
  gap: var(--space-2);
}

.btn-outline {
  padding: var(--space-2) var(--space-4);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.empty-state {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-muted);
}
</style>
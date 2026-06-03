<script setup lang="ts">
import { ref } from 'vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'

// 就诊记录（待后端实现后通过 API 获取）
const records = ref<any[]>([])
</script>

<template>
  <div class="patient-records">
    <GlassCard class="records-list">
      <div class="list-header">
        <h2>就诊记录</h2>
        <p>查看您的历史就诊信息、病历和处方</p>
      </div>

      <div class="record-items">
        <div v-for="record in records" :key="record.id" class="record-item">
          <div class="record-date">
            <span class="date-day">{{ record.date.split('-')[2] }}</span>
            <span class="date-month">{{ record.date.slice(0, 7) }}</span>
          </div>
          <div class="record-info">
            <div class="record-main">
              <span class="record-dept">{{ record.department }}</span>
              <span class="record-doctor">{{ record.doctor }}</span>
            </div>
            <div class="record-diagnosis">
              <StatusTag tone="warning">{{ record.diagnosis }}</StatusTag>
            </div>
            <div class="record-tags">
              <StatusTag v-if="record.hasPrescription" tone="primary">有处方</StatusTag>
              <StatusTag v-if="record.hasReport" tone="neutral">有报告</StatusTag>
            </div>
          </div>
          <div class="record-actions">
            <button class="btn-outline">查看详情</button>
          </div>
        </div>
      </div>

      <div v-if="!records.length" class="empty-state">
        <p>暂无就诊记录</p>
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

.record-tags {
  display: flex;
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
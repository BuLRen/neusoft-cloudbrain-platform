<script setup lang="ts">
import { ref } from 'vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'

// 处方记录（待后端实现后通过 API 获取）
const prescriptions = ref<any[]>([])

const expandedId = ref<number | null>(null)

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

function statusTone(status: string) {
  if (status === 'active') return 'primary'
  if (status === 'completed') return 'success'
  return 'neutral'
}

function statusText(status: string) {
  if (status === 'active') return '待取药'
  if (status === 'completed') return '已完成'
  return '进行中'
}

function pharmacyTone(status: string) {
  if (status === 'pending') return 'warning'
  if (status === 'completed') return 'success'
  return 'neutral'
}

function pharmacyText(status: string) {
  if (status === 'pending') return '待取药'
  if (status === 'completed') return '已取药'
  return status
}
</script>

<template>
  <div class="patient-prescription">
    <GlassCard class="prescription-list">
      <div class="list-header">
        <h2>我的处方</h2>
        <p>查看您的处方记录和用药详情</p>
      </div>

      <div class="prescription-items">
        <div
          v-for="rx in prescriptions"
          :key="rx.id"
          class="prescription-item"
        >
          <div class="rx-header" @click="toggleExpand(rx.id)">
            <div class="rx-date">
              <span class="date-day">{{ rx.date.split('-')[2] }}</span>
              <span class="date-month">{{ rx.date.slice(0, 7) }}</span>
            </div>
            <div class="rx-info">
              <div class="rx-main">
                <span class="rx-dept">{{ rx.department }}</span>
                <span class="rx-doctor">{{ rx.doctor }}</span>
                <StatusTag :tone="statusTone(rx.status)">
                  {{ statusText(rx.status) }}
                </StatusTag>
                <StatusTag :tone="pharmacyTone(rx.pharmacyStatus)">
                  {{ pharmacyText(rx.pharmacyStatus) }}
                </StatusTag>
              </div>
              <div class="rx-meta">
                <span>诊断：{{ rx.diagnosis }}</span>
                <span>处方号：{{ rx.id }}</span>
                <span>金额：¥{{ rx.totalAmount.toFixed(2) }}</span>
              </div>
            </div>
            <div class="rx-expand">
              <span :class="['expand-icon', { rotated: expandedId === rx.id }]">
                ▼
              </span>
            </div>
          </div>

          <div v-if="expandedId === rx.id" class="rx-detail">
            <div class="medicines-header">
              <span>药品名称</span>
              <span>规格</span>
              <span>数量</span>
              <span>用法用量</span>
              <span>疗程</span>
              <span>单价</span>
            </div>
            <div
              v-for="(med, idx) in rx.medicines"
              :key="idx"
              class="medicine-row"
            >
              <span class="med-name">{{ med.name }}</span>
              <span>{{ med.dosage }}</span>
              <span>{{ med.quantity }}</span>
              <span class="med-frequency">{{ med.frequency }}</span>
              <span>{{ med.duration }}</span>
              <span>¥{{ med.price.toFixed(2) }}</span>
            </div>
            <div class="rx-footer">
              <span class="total-label">合计</span>
              <span class="total-amount">¥{{ rx.totalAmount.toFixed(2) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!prescriptions.length" class="empty-state">
        <p>暂无处方记录</p>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.patient-prescription {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.prescription-list {
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

.prescription-items {
  display: grid;
  gap: var(--space-4);
}

.prescription-item {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.rx-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  cursor: pointer;
  transition: background var(--duration-fast);
}

.rx-header:hover {
  background: var(--color-surface);
}

.rx-date {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  text-align: center;
  flex-shrink: 0;
}

.date-day {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
}

.date-month {
  font-size: 11px;
  color: var(--color-primary);
}

.rx-info {
  flex: 1;
  display: grid;
  gap: var(--space-2);
}

.rx-main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.rx-dept {
  font-size: 16px;
  font-weight: 600;
}

.rx-doctor {
  color: var(--color-text-muted);
}

.rx-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  font-size: 13px;
  color: var(--color-text-muted);
}

.rx-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
}

.expand-icon {
  font-size: 10px;
  color: var(--color-text-muted);
  transition: transform var(--duration-fast);
}

.expand-icon.rotated {
  transform: rotate(180deg);
}

/* 药品详情 */
.rx-detail {
  border-top: 1px solid var(--color-border);
  padding: var(--space-4);
  background: var(--color-surface);
}

.medicines-header,
.medicine-row {
  display: grid;
  grid-template-columns: 2fr 1fr 0.8fr 2fr 1fr 0.8fr;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-2);
  align-items: center;
}

.medicines-header {
  font-size: 12px;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--color-border);
}

.medicine-row {
  font-size: 13px;
  border-bottom: 1px solid var(--color-border);
}

.medicine-row:last-child {
  border-bottom: none;
}

.med-name {
  font-weight: 600;
}

.med-frequency {
  color: var(--color-text-muted);
}

.rx-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
  margin-top: var(--space-3);
}

.total-label {
  font-weight: 600;
  color: var(--color-text-muted);
}

.total-amount {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-primary);
}

.empty-state {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .medicines-header {
    display: none;
  }

  .medicine-row {
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }
}
</style>
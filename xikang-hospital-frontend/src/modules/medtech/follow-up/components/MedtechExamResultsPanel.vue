<script setup lang="ts">
import { ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import type { FollowUpMedtechExamReport } from '@/shared/types/medtechFollowUp'

defineProps<{
  exams: FollowUpMedtechExamReport[]
  loading?: boolean
  /** 嵌入父级卡片时不再套一层 GlassCard */
  embedded?: boolean
}>()

function statusTone(status?: string) {
  if (status === 'high' || status === 'positive' || status === 'critical') return 'danger' as const
  if (status === 'low') return 'warning' as const
  return 'success' as const
}

function statusLabel(status?: string) {
  if (status === 'high') return '偏高'
  if (status === 'low') return '偏低'
  if (status === 'positive') return '阳性'
  if (status === 'critical') return '危急'
  if (status === 'normal') return '正常'
  return status ?? '—'
}
</script>

<template>
  <component
    :is="embedded ? 'div' : GlassCard"
    class="medtech-exam-panel"
    :class="{ 'medtech-exam-panel--embedded': embedded }"
  >
    <div class="medtech-exam-panel__head">
      <h3>医技检查 / 检验结果</h3>
      <span class="medtech-exam-panel__hint">数据来自检验科、放射科等医技申请单（只读）</span>
    </div>

    <ElEmpty v-if="!loading && !exams.length" description="暂无医技检查结果" />

    <div v-else class="medtech-exam-panel__list">
      <section v-for="exam in exams" :key="`${exam.examType}-${exam.requestId}`" class="medtech-exam-panel__card">
        <header class="medtech-exam-panel__card-head">
          <div>
            <strong>{{ exam.techName ?? exam.techCode ?? '医技项目' }}</strong>
            <span class="medtech-exam-panel__meta">
              {{ exam.examTypeLabel ?? exam.examType }} · {{ exam.observedAt ?? '—' }}
            </span>
          </div>
          <StatusTag v-if="exam.isNormal != null" :tone="exam.isNormal ? 'success' : 'warning'" compact>
            {{ exam.isNormal ? '未见明显异常' : '存在异常' }}
          </StatusTag>
        </header>

        <p v-if="exam.conclusion" class="medtech-exam-panel__conclusion">{{ exam.conclusion }}</p>

        <ElTable
          v-if="exam.resultItems?.length"
          :data="exam.resultItems"
          size="small"
          stripe
          class="medtech-exam-panel__table"
        >
          <ElTableColumn prop="itemName" label="项目" min-width="120" />
          <ElTableColumn label="结果" min-width="100">
            <template #default="{ row }">
              <span>{{ row.value ?? '—' }}</span>
              <span v-if="row.unit" class="medtech-exam-panel__unit">{{ row.unit }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="referenceRange" label="参考范围" min-width="100" />
          <ElTableColumn label="判定" width="88">
            <template #default="{ row }">
              <ElTag v-if="row.status" :type="statusTone(row.status)" size="small" effect="plain">
                {{ statusLabel(row.status) }}
              </ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </section>
    </div>
  </component>
</template>

<style scoped>
.medtech-exam-panel {
  padding: var(--space-4);
  display: grid;
  gap: var(--space-3);
}

.medtech-exam-panel--embedded {
  padding: 0;
  margin-block-start: var(--space-2);
}

.medtech-exam-panel__head h3 {
  margin: 0;
  font-size: 16px;
}

.medtech-exam-panel__hint {
  display: block;
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.medtech-exam-panel__list {
  display: grid;
  gap: var(--space-3);
}

.medtech-exam-panel__card {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
}

.medtech-exam-panel__card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-2);
}

.medtech-exam-panel__meta {
  display: block;
  margin-top: 2px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.medtech-exam-panel__conclusion {
  margin: var(--space-2) 0 0;
  color: var(--color-text-soft);
  font-size: 13px;
  line-height: 1.6;
}

.medtech-exam-panel__table {
  margin-top: var(--space-2);
}

.medtech-exam-panel__unit {
  margin-left: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>

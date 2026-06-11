<script setup lang="ts">
import { ElTable, ElTableColumn, ElTag } from 'element-plus'
import type { SimulatedCheckStructuredOutput } from '@/shared/types/simulatedCheckResult'
import { statusLabel, statusTone } from '@/shared/types/simulatedCheckResult'

defineProps<{
  data: SimulatedCheckStructuredOutput | null
}>()
</script>

<template>
  <template v-if="data && (data.resultItems.length || data.conclusion || data.checkName)">
    <!-- <div class="dialog-meta">
      <StatusTag :tone="data.isNormal ? 'success' : 'warning'">
        {{ data.isNormal ? '模拟正常' : '模拟异常' }}
      </StatusTag>
      <div v-if="data.simulatedForDiseases.length" class="dialog-meta__diseases">
        <span class="dialog-meta__label">参考疾病：</span>
        <ElTag v-for="disease in data.simulatedForDiseases" :key="disease" size="small" type="info">
          {{ disease }}
        </ElTag>
      </div>
    </div> -->

    <ElTable :data="data.resultItems" border size="small" class="result-table">
      <ElTableColumn prop="itemName" label="项目" min-width="110" />
      <ElTableColumn label="结果" min-width="120">
        <template #default="{ row }">
          {{ row.value }}<span v-if="row.unit"> {{ row.unit }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="referenceRange" label="参考范围" min-width="120" />
      <ElTableColumn label="状态" width="88">
        <template #default="{ row }">
          <ElTag :type="statusTone(row.status)" size="small">{{ statusLabel(row.status) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="meaning" label="含义" min-width="180" show-overflow-tooltip />
    </ElTable>

    <section class="dialog-section">
      <h4 class="dialog-section__title">结论</h4>
      <p class="dialog-section__text">{{ data.conclusion || '-' }}</p>
    </section>

    <section v-if="data.notice" class="dialog-section">
      <h4 class="dialog-section__title">提示</h4>
      <p class="dialog-section__text">{{ data.notice }}</p>
    </section>
  </template>
  <p v-else class="dialog-empty">暂无可展示的结构化结果</p>
</template>

<style scoped>
.dialog-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.dialog-meta__diseases {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.dialog-meta__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.result-table {
  margin-block-end: var(--space-4);
}

.dialog-section + .dialog-section {
  margin-block-start: var(--space-4);
}

.dialog-section__title {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.dialog-section__text {
  margin: 0;
  white-space: pre-wrap;
  color: var(--color-text);
  line-height: 1.6;
}

.dialog-empty {
  margin: 0;
  color: var(--color-text-muted);
  text-align: center;
}
</style>

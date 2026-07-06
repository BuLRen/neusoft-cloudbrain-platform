<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDialog, ElTable, ElTableColumn, ElTag } from 'element-plus'
import type { CriticalDetectResult } from '@/shared/api/modules/criticalValue'

const props = defineProps<{
  visible: boolean
  detect: CriticalDetectResult | null
  reporting?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: []
  skip: []
}>()

const items = computed(() => props.detect?.items ?? [])

function severityTag(severity?: string) {
  if (severity === 'CRITICAL') return 'danger'
  return 'warning'
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="危急值复核确认"
    width="640px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <p class="cv-confirm__lead">
      系统识别到疑似危急值，请医技人员复核后确认上报。确认后将实时推送给开单医生签收处置。
    </p>

    <ElTable :data="items" size="small" border stripe>
      <ElTableColumn prop="itemName" label="项目" min-width="120" />
      <ElTableColumn label="结果" min-width="100">
        <template #default="{ row }">
          {{ row.value }}{{ row.unit ? ` ${row.unit}` : '' }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="referenceRange" label="参考/阈值" min-width="120" />
      <ElTableColumn label="严重度" width="90">
        <template #default="{ row }">
          <ElTag :type="severityTag(row.severity)" size="small">{{ row.severity || 'CRITICAL' }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="rule" label="命中规则" min-width="120" />
    </ElTable>

    <template #footer>
      <ElButton @click="emit('skip')">非危急值，跳过</ElButton>
      <ElButton type="danger" :loading="reporting" @click="emit('confirm')">确认上报</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.cv-confirm__lead {
  margin: 0 0 12px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
</style>

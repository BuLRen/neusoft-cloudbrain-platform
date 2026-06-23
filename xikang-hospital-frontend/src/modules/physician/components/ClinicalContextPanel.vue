<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElDescriptions, ElDescriptionsItem, ElSkeleton, ElTag } from 'element-plus'
import type { MedicalRecord, PreliminaryAiMeta } from '@/shared/api/modules/physician'
import GlassCard from '@/shared/components/GlassCard.vue'

const props = defineProps<{
  record: MedicalRecord | null
  loading?: boolean
}>()

function doctorDiagnosisFromMeta(meta: PreliminaryAiMeta | undefined): string {
  if (!meta) return ''
  if (meta.suggestedDiseaseNames?.length) {
    return meta.suggestedDiseaseNames.join('、')
  }
  return (meta.suggestedDiseases || [])
    .map((item) => item.diseaseName?.trim())
    .filter((name): name is string => Boolean(name))
    .join('、')
}

function resolveConfirmedDiagnosis(record: MedicalRecord | null): string {
  if (!record) return ''
  const saved = record.preliminaryDiagnosis?.trim()
  if (saved) return saved
  const fromMeta = doctorDiagnosisFromMeta(record.preliminaryAiMeta)
  if (fromMeta) return fromMeta
  return record.preliminaryAiMeta?.primaryDiagnosis?.trim() || ''
}

const confirmedDiagnosis = computed(() => resolveConfirmedDiagnosis(props.record))
const clinicalSummary = computed(() => props.record?.preliminaryAiMeta?.clinicalSummary?.trim() || '')
const redFlags = computed(() => props.record?.preliminaryAiMeta?.redFlags?.filter(Boolean) ?? [])

interface ContextRow {
  key: string
  label: string
  value: string
  highlight?: boolean
}

const contextRows = computed((): ContextRow[] => {
  const record = props.record
  if (!record) return []

  const rows: ContextRow[] = []
  const push = (key: string, label: string, value: string | undefined, highlight?: boolean) => {
    const trimmed = value?.trim()
    if (trimmed) rows.push({ key, label, value: trimmed, highlight })
  }

  push('diagnosis', '初步诊断', confirmedDiagnosis.value, true)
  push('proposal', '检查/检验建议', record.proposal, true)
  push('readme', '主诉', record.readme)
  push('present', '现病史', record.present)
  push('physique', '体格检查', record.physique)
  push('allergy', '过敏史', record.allergy)
  push('history', '既往史', record.history)
  push('presentTreat', '现病治疗情况', record.presentTreat)

  return rows
})

const hasContext = computed(
  () => contextRows.value.length > 0 || Boolean(clinicalSummary.value) || redFlags.value.length > 0,
)
</script>

<template>
  <GlassCard class="clinical-context">
    <header class="clinical-context__header">
      <h2 class="clinical-context__title">病历与诊断摘要</h2>
      <p class="clinical-context__subtitle">来自上一步已保存的病历与初步诊断，供开立检查检验参考</p>
    </header>

    <ElSkeleton v-if="loading" :rows="4" animated />

    <template v-else-if="!hasContext">
      <ElAlert
        type="warning"
        :closable="false"
        show-icon
        title="暂无病历或初步诊断数据"
        description="请返回「病历与初步诊断」填写并保存病历、初步诊断后再开立检查检验。"
      />
    </template>

    <template v-else>
      <ElAlert
        v-if="redFlags.length"
        type="error"
        :closable="false"
        show-icon
        class="clinical-context__alert"
        title="警示征象"
      >
        <ul class="clinical-context__flags">
          <li v-for="(flag, index) in redFlags" :key="`flag-${index}`">{{ flag }}</li>
        </ul>
      </ElAlert>

      <p v-if="clinicalSummary" class="clinical-context__summary">
        <strong>AI 临床摘要：</strong>{{ clinicalSummary }}
      </p>

      <ElDescriptions :column="1" border class="clinical-context__desc">
        <ElDescriptionsItem
          v-for="row in contextRows"
          :key="row.key"
          :label="row.label"
          :class="{ 'clinical-context__row--highlight': row.highlight }"
        >
          <span class="clinical-context__value">{{ row.value }}</span>
          <ElTag v-if="row.highlight" size="small" type="success" class="clinical-context__tag">重点</ElTag>
        </ElDescriptionsItem>
      </ElDescriptions>
    </template>
  </GlassCard>
</template>

<style scoped>
.clinical-context {
  padding: var(--space-5);
  margin-block-end: var(--space-5);
}

.clinical-context__header {
  margin-block-end: var(--space-4);
}

.clinical-context__title {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.clinical-context__subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.clinical-context__alert {
  margin-block-end: var(--space-4);
}

.clinical-context__flags {
  margin: var(--space-2) 0 0;
  padding-inline-start: var(--space-5);
  line-height: 1.7;
}

.clinical-context__summary {
  margin: 0 0 var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-text);
  line-height: 1.75;
}

.clinical-context__value {
  white-space: pre-wrap;
  line-height: 1.65;
}

.clinical-context__tag {
  margin-inline-start: var(--space-2);
  vertical-align: middle;
}

.clinical-context__desc :deep(.clinical-context__row--highlight .el-descriptions__label) {
  font-weight: 600;
  color: var(--color-primary);
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { ElIcon, ElSkeleton, ElTag } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import type { MedicalRecord, PreliminaryAiMeta } from '@/shared/api/modules/physician'

const props = withDefaults(defineProps<{
  record?: MedicalRecord | null
  loading?: boolean
}>(), {
  loading: false,
})

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

function resolvePreliminaryDiagnosis(record: MedicalRecord | null | undefined): string {
  if (!record) return ''
  const saved = record.preliminaryDiagnosis?.trim()
  if (saved) return saved
  const fromMeta = doctorDiagnosisFromMeta(record.preliminaryAiMeta)
  if (fromMeta) return fromMeta
  return record.preliminaryAiMeta?.primaryDiagnosis?.trim() || ''
}

const preliminaryDiagnosis = computed(() => resolvePreliminaryDiagnosis(props.record))

const hasRecordContent = computed(() => {
  const record = props.record
  if (!record) return false
  return Boolean(
    record.readme?.trim()
    || record.present?.trim()
    || record.presentTreat?.trim()
    || record.history?.trim()
    || record.allergy?.trim()
    || record.physique?.trim()
    || record.proposal?.trim()
    || preliminaryDiagnosis.value,
  )
})
</script>

<template>
  <section class="medical-record-summary">
    <ElSkeleton v-if="loading" :rows="4" animated />

    <div v-else-if="hasRecordContent" class="record-card">
      <header class="record-card__header">
        <div class="record-card__title">
          <span class="record-card__icon" aria-hidden="true">
            <ElIcon :size="18"><DocumentChecked /></ElIcon>
          </span>
          <strong>门诊病历</strong>
        </div>
        <ElTag v-if="preliminaryDiagnosis" type="success" size="small" effect="light" round>
          已保存
        </ElTag>
      </header>

      <div class="record-card__grid">
        <div class="record-card__item">
          <label>主诉</label>
          <p>{{ record?.readme?.trim() || '—' }}</p>
        </div>
        <div class="record-card__item record-card__item--highlight">
          <label>初步诊断</label>
          <p>{{ preliminaryDiagnosis || '—' }}</p>
        </div>
        <div class="record-card__item full">
          <label>现病史</label>
          <p>{{ record?.present?.trim() || '—' }}</p>
        </div>
        <div class="record-card__item">
          <label>既往史</label>
          <p>{{ record?.history?.trim() || '—' }}</p>
        </div>
        <div class="record-card__item">
          <label>过敏史</label>
          <p>{{ record?.allergy?.trim() || '—' }}</p>
        </div>
        <div class="record-card__item">
          <label>现病治疗情况</label>
          <p>{{ record?.presentTreat?.trim() || '—' }}</p>
        </div>
        <div class="record-card__item">
          <label>体格检查</label>
          <p>{{ record?.physique?.trim() || '—' }}</p>
        </div>
        <div v-if="record?.proposal?.trim()" class="record-card__item full">
          <label>检查/检验建议</label>
          <p>{{ record.proposal.trim() }}</p>
        </div>
      </div>
    </div>

    <div v-else class="record-card record-card--empty">
      <header class="record-card__header">
        <div class="record-card__title">
          <span class="record-card__icon" aria-hidden="true">
            <ElIcon :size="18"><DocumentChecked /></ElIcon>
          </span>
          <strong>门诊病历</strong>
        </div>
      </header>
      <p class="record-card__empty-text">
        暂无已保存病历。进入流程后，可在「病历与初步诊断」中填写并保存。
      </p>
    </div>
  </section>
</template>

<style scoped>
.record-card {
  padding: var(--space-5);
  border-radius: var(--radius-lg);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98) 0%, rgba(255, 255, 255, 0.98) 100%);
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.record-card--empty {
  color: var(--color-text-muted);
}

.record-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.record-card__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 15px;
  color: var(--color-text);
}

.record-card__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.12);
}

.record-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.record-card__item {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.record-card__item--highlight {
  background: rgba(31, 140, 255, 0.06);
  box-shadow: inset 0 0 0 1px rgba(31, 140, 255, 0.16);
}

.record-card__item.full {
  grid-column: 1 / -1;
}

.record-card__item label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-soft);
}

.record-card__item--highlight label {
  color: var(--color-primary-strong);
}

.record-card__item p {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text);
  white-space: pre-wrap;
}

.record-card__empty-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
}

@media (max-width: 720px) {
  .record-card__grid {
    grid-template-columns: 1fr;
  }
}
</style>

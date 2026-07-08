<script setup lang="ts">
import { ref } from 'vue'

export interface CtPatientInfoField {
  label: string
  value?: string | number | null
}

withDefaults(
  defineProps<{
    patientName?: string
    fields: CtPatientInfoField[]
  }>(),
  {
    patientName: '',
  },
)

const collapsed = ref(false)
function toggle() {
  collapsed.value = !collapsed.value
}
</script>

<template>
  <section class="ct-patient-info" :class="{ 'ct-patient-info--collapsed': collapsed }">
    <header class="ct-patient-info__header" @click="toggle">
      <span class="ct-patient-info__title">患者信息</span>
      <button type="button" class="ct-patient-info__toggle" :aria-expanded="!collapsed">
        {{ collapsed ? '▸' : '▾' }}
      </button>
    </header>
    <div v-show="!collapsed" class="ct-patient-info__body">
      <div v-if="patientName" class="ct-patient-info__name">{{ patientName }}</div>
      <div class="ct-patient-info__grid">
        <div v-for="field in fields" :key="field.label" class="ct-patient-info__row">
          <span class="ct-patient-info__label">{{ field.label }}</span>
          <span class="ct-patient-info__value">{{ field.value ?? '-' }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ct-patient-info {
  border-block-end: 1px solid var(--ct-border);
  background: var(--ct-surface);
}

.ct-patient-info__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  cursor: pointer;
  user-select: none;
}

.ct-patient-info__title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ct-text-muted);
}

.ct-patient-info__toggle {
  border: none;
  background: transparent;
  color: var(--ct-text-dim);
  font-size: 11px;
  cursor: pointer;
  padding: 2px 4px;
}

.ct-patient-info__toggle:hover {
  color: var(--ct-accent);
}

.ct-patient-info__body {
  padding: 0 14px 14px;
}

.ct-patient-info__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ct-text);
  margin-bottom: 8px;
}

.ct-patient-info__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px 0;
}

.ct-patient-info__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  padding: 3px 0;
  border-block-end: 1px dashed var(--ct-border);
}

.ct-patient-info__row:last-child {
  border-block-end: none;
}

.ct-patient-info__label {
  color: var(--ct-text-dim);
  flex-shrink: 0;
}

.ct-patient-info__value {
  color: var(--ct-text-muted);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

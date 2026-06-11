<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElOption, ElSelect } from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import { physicianApi, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import { visitStateLabel } from '../constants/visitState'

const route = useRoute()
const router = useRouter()
const encounterStore = useEncounterStore()

const loading = ref(false)
const patients = ref<PhysicianPatient[]>([])
const selectedId = ref<number | undefined>(undefined)

async function loadPatients() {
  loading.value = true
  try {
    const page = await physicianApi.patients({ page: 1, size: 50 })
    patients.value = page.records
  } finally {
    loading.value = false
  }
}

async function onSelect(registerId: number) {
  if (!registerId || registerId === encounterStore.registerId) return
  await encounterStore.switchEncounter(registerId)
  await router.replace({
    path: route.path,
    query: { ...route.query, registerId: String(registerId) },
  })
}

watch(
  () => encounterStore.registerId,
  (id) => {
    selectedId.value = id ?? undefined
  },
  { immediate: true },
)

onMounted(() => {
  void loadPatients()
})
</script>

<template>
  <div class="patient-switcher">
    <span class="patient-switcher__label">切换患者</span>
    <ElSelect
      v-model="selectedId"
      class="patient-switcher__select"
      placeholder="选择进行中患者"
      filterable
      :loading="loading"
      @change="onSelect"
    >
      <ElOption
        v-for="patient in patients"
        :key="patient.registerId"
        :label="`${patient.realName}（${patient.caseNumber}）`"
        :value="patient.registerId"
      >
        <div class="patient-switcher__option">
          <span>{{ patient.realName }} · {{ patient.caseNumber }}</span>
          <StatusTag :tone="visitStateLabel(patient.visitState).tone">
            {{ visitStateLabel(patient.visitState).text }}
          </StatusTag>
        </div>
      </ElOption>
    </ElSelect>
  </div>
</template>

<style scoped>
.patient-switcher {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.patient-switcher__label {
  font-size: var(--font-size-sm, 0.875rem);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.patient-switcher__select {
  min-width: 220px;
}

.patient-switcher__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}
</style>

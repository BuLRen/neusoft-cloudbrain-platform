<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElDialog, ElEmpty, ElIcon, ElInput } from 'element-plus'
import { Search, User } from '@element-plus/icons-vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { physicianApi, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import { usePhysicianPatientSelectStore } from '@/app/stores/physicianPatientSelect'
import { physicianPathTitle, physicianRoute, VISIT_STATE, visitStateLabel } from '../constants/visitState'

const route = useRoute()
const router = useRouter()
const encounterStore = useEncounterStore()
const selectStore = usePhysicianPatientSelectStore()

const loading = ref(false)
const keyword = ref('')
const patients = ref<PhysicianPatient[]>([])
const confirming = ref(false)

const dialogTitle = computed(() => {
  const target = selectStore.targetPath
  if (!target) return '选择患者'
  return `选择患者 — 进入「${physicianPathTitle(target)}」`
})

const visible = computed({
  get: () => selectStore.visible,
  set: (value: boolean) => {
    if (!value) selectStore.close()
  },
})

async function loadPatients() {
  loading.value = true
  try {
    const page = await physicianApi.patients({ keyword: keyword.value, page: 1, size: 50 })
    patients.value = page.records
  } finally {
    loading.value = false
  }
}

async function confirmPatient(patient: PhysicianPatient) {
  if (confirming.value || !selectStore.targetPath) return
  confirming.value = true
  try {
    let visitState = patient.visitState
    if (visitState === VISIT_STATE.REGISTERED) {
      const result = await physicianApi.startEncounter(patient.registerId)
      visitState = result.visitState
    }
    encounterStore.applyPatient({ ...patient, visitState })
    const target = selectStore.targetPath
    selectStore.close()
    await router.push(physicianRoute(target, patient.registerId))
  } finally {
    confirming.value = false
  }
}

watch(visible, (open) => {
  if (open) {
    keyword.value = ''
    void loadPatients()
  }
})

watch(
  () => route.query.selectFor,
  (raw) => {
    const path = typeof raw === 'string' ? raw : ''
    if (!path) return
    selectStore.open(path)
    const { selectFor: _removed, ...rest } = route.query
    void router.replace({ path: route.path, query: rest })
  },
  { immediate: true },
)
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="min(560px, 92vw)"
    append-to-body
    destroy-on-close
    class="patient-select-dialog"
  >
    <p class="patient-select-dialog__hint">
      请选择本次要诊疗的患者。待接诊患者将自动开始接诊并进入所选页面。
    </p>

    <div class="patient-select-dialog__search">
      <ElInput
        v-model="keyword"
        placeholder="搜索病历号或姓名"
        clearable
        @keyup.enter="loadPatients"
      >
        <template #prefix>
          <ElIcon><Search /></ElIcon>
        </template>
      </ElInput>
      <ElButton type="primary" :loading="loading" @click="loadPatients">搜索</ElButton>
    </div>

    <div v-loading="loading" class="patient-select-dialog__list">
      <button
        v-for="patient in patients"
        :key="patient.registerId"
        type="button"
        class="patient-select-dialog__item"
        :disabled="confirming"
        @click="confirmPatient(patient)"
      >
        <span class="patient-select-dialog__avatar"><ElIcon><User /></ElIcon></span>
        <span class="patient-select-dialog__body">
          <strong>{{ patient.realName }}</strong>
          <span>{{ patient.caseNumber }}</span>
        </span>
        <StatusTag :tone="visitStateLabel(patient.visitState).tone">
          {{ visitStateLabel(patient.visitState).text }}
        </StatusTag>
      </button>
      <ElEmpty v-if="!loading && !patients.length" description="暂无患者" />
    </div>
  </ElDialog>
</template>

<style scoped>
.patient-select-dialog__hint {
  margin: 0 0 var(--space-4);
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.patient-select-dialog__search {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.patient-select-dialog__list {
  display: grid;
  gap: var(--space-2);
  max-height: 360px;
  overflow-y: auto;
}

.patient-select-dialog__item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: var(--space-3);
  align-items: center;
  width: 100%;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.patient-select-dialog__item:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.patient-select-dialog__item:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.patient-select-dialog__avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #fff;
  color: var(--color-primary);
}

.patient-select-dialog__body {
  display: grid;
  gap: 2px;
  font-size: 13px;
}

.patient-select-dialog__body span {
  color: var(--color-text-muted);
}
</style>

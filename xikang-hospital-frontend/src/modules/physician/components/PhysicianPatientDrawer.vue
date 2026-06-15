<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDrawer, ElEmpty, ElIcon, ElInput } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { physicianApi, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import { VISIT_STATE, physicianRoute, resumePathForVisitState, visitStateLabel } from '../constants/visitState'

const visible = defineModel<boolean>('visible', { default: false })

const router = useRouter()
const encounterStore = useEncounterStore()

const loading = ref(false)
const keyword = ref('')
const patients = ref<PhysicianPatient[]>([])

const activePatients = computed(() =>
  patients.value.filter((patient) => patient.visitState !== VISIT_STATE.REGISTERED),
)

async function loadPatients() {
  loading.value = true
  try {
    const page = await physicianApi.patients({ keyword: keyword.value, page: 1, size: 50 })
    patients.value = page.records
  } finally {
    loading.value = false
  }
}

async function onSelect(patient: PhysicianPatient) {
  if (patient.registerId === encounterStore.registerId) {
    visible.value = false
    return
  }
  await encounterStore.switchEncounter(patient.registerId)
  const path = resumePathForVisitState(patient.visitState)
  await router.replace(physicianRoute(path, patient.registerId))
  visible.value = false
}

function goToQueue() {
  visible.value = false
  void router.push('/physician/queue')
}

watch(visible, (open) => {
  if (open) {
    keyword.value = ''
    void loadPatients()
  }
})
</script>

<template>
  <ElDrawer
    v-model="visible"
    title="切换进行中患者"
    direction="rtl"
    size="min(480px, 100vw)"
    append-to-body
    :lock-scroll="true"
    class="patient-drawer patient-drawer--page"
  >
    <div class="patient-drawer__content">
      <p class="patient-drawer__hint">仅显示接诊中或检查检验进行中的患者。新患者请从待诊接诊进入。</p>

      <ElInput
        v-model="keyword"
        placeholder="搜索病历号或姓名"
        clearable
        class="patient-drawer__search"
        @keyup.enter="loadPatients"
      >
        <template #append>
          <ElButton :loading="loading" @click="loadPatients">查询</ElButton>
        </template>
      </ElInput>

      <div class="patient-drawer__scroll" role="region" aria-label="进行中患者列表">
        <ul v-if="activePatients.length > 0" class="patient-drawer__list" role="listbox" aria-label="进行中患者">
          <li
            v-for="patient in activePatients"
            :key="patient.registerId"
            class="patient-drawer__item"
            :class="{ 'patient-drawer__item--active': patient.registerId === encounterStore.registerId }"
            role="option"
            :aria-selected="patient.registerId === encounterStore.registerId"
            tabindex="0"
            @click="onSelect(patient)"
            @keydown.enter="onSelect(patient)"
          >
            <span class="patient-drawer__item-main">
              <span class="patient-drawer__item-name">{{ patient.realName }}</span>
              <span class="patient-drawer__item-meta">{{ patient.caseNumber }}</span>
            </span>
            <span class="patient-drawer__item-tags">
              <StatusTag :tone="visitStateLabel(patient.visitState).tone">
                {{ visitStateLabel(patient.visitState).text }}
              </StatusTag>
              <ElIcon
                v-if="patient.registerId === encounterStore.registerId"
                class="patient-drawer__check"
              >
                <Check />
              </ElIcon>
            </span>
          </li>
        </ul>

        <ElEmpty v-else-if="!loading" description="暂无进行中患者" />
      </div>
    </div>

    <template #footer>
      <div class="patient-drawer__footer">
        <ElButton text type="primary" @click="goToQueue">返回待诊接诊</ElButton>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped>
.patient-drawer__content {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.patient-drawer__hint {
  flex-shrink: 0;
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  line-height: 1.6;
}

.patient-drawer__search {
  flex-shrink: 0;
  margin-block-end: var(--space-4);
}

.patient-drawer__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-inline: calc(var(--space-4) * -1);
  padding-inline: var(--space-4);
}

.patient-drawer__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.patient-drawer__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.patient-drawer__item:hover {
  background: var(--color-menu-hover);
}

.patient-drawer__item--active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.patient-drawer__item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.patient-drawer__item-name {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text);
}

.patient-drawer__item-meta {
  font-size: 12px;
  color: var(--color-text-soft);
}

.patient-drawer__item-tags {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.patient-drawer__check {
  color: var(--color-primary);
  font-size: 18px;
}

.patient-drawer__footer {
  display: flex;
  justify-content: flex-start;
  width: 100%;
}
</style>

<style>
.patient-drawer--page.el-drawer {
  height: 100%;
}

.patient-drawer--page .el-drawer__body {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-block-end: 0;
}
</style>

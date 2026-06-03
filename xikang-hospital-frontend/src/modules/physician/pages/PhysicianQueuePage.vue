<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElInput } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { physicianApi, type PhysicianPatient } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'

const router = useRouter()
const encounterStore = useEncounterStore()

const loading = ref(false)
const patients = ref<PhysicianPatient[]>([])
const selectedPatient = ref<PhysicianPatient | null>(null)
const keyword = ref('')
const stats = reactive({ totalVisited: 0, totalWaiting: 0 })

const selectedRegisterId = computed(() => selectedPatient.value?.registerId)

async function loadPatients() {
  loading.value = true
  try {
    const [patientPage, patientStats] = await Promise.all([
      physicianApi.patients({ keyword: keyword.value, page: 1, size: 20 }),
      physicianApi.patientStats(),
    ])
    patients.value = patientPage.records
    stats.totalVisited = patientStats.totalVisited || 0
    stats.totalWaiting = patientStats.totalWaiting || 0
    if (!selectedPatient.value && patients.value.length > 0) {
      selectedPatient.value = patients.value[0]
    }
  } finally {
    loading.value = false
  }
}

function enterEncounter() {
  if (!selectedPatient.value || !selectedRegisterId.value) return
  encounterStore.setEncounter({
    registerId: selectedRegisterId.value,
    patientSummary: {
      realName: selectedPatient.value.realName,
      caseNumber: selectedPatient.value.caseNumber,
      gender: selectedPatient.value.gender,
      age: selectedPatient.value.age,
    },
    aiConsultSummary: selectedPatient.value.aiConsultSummary
      ? {
          aiSummary: selectedPatient.value.aiConsultSummary.aiSummary,
          chiefComplaint: selectedPatient.value.aiConsultSummary.chiefComplaint,
        }
      : null,
  })
  router.push('/physician/record')
}

onMounted(() => {
  void loadPatients()
})
</script>

<template>
  <div class="physician-queue u-page-grid">
    <PageHeader title="待诊接诊" description="第一步：选择待诊患者，进入后续诊疗流程。" eyebrow="门诊诊疗 · 第 1/6 步">
      <template #actions>
        <ElButton type="primary" @click="loadPatients">刷新患者</ElButton>
      </template>
    </PageHeader>

    <section class="queue-grid">
      <GlassCard class="patient-panel">
        <div class="panel-heading">
          <div>
            <h2>待诊患者</h2>
            <p>待诊 {{ stats.totalWaiting }} 人，已完成 {{ stats.totalVisited }} 人</p>
          </div>
        </div>
        <ElInput v-model="keyword" placeholder="搜索病历号或姓名" clearable @keyup.enter="loadPatients">
          <template #append>
            <ElButton @click="loadPatients">查询</ElButton>
          </template>
        </ElInput>
        <div class="patient-list">
          <ElAlert v-if="loading" type="info" :closable="false" title="正在加载患者列表" />
          <button
            v-for="patient in patients"
            :key="patient.registerId"
            class="patient-item"
            :class="{ 'is-active': patient.registerId === selectedRegisterId }"
            type="button"
            @click="selectedPatient = patient"
          >
            <strong>{{ patient.realName }}</strong>
            <span>{{ patient.caseNumber }}</span>
            <StatusTag :tone="patient.visitState === 1 ? 'warning' : 'primary'">
              {{ patient.visitState === 1 ? '待接诊' : '接诊中' }}
            </StatusTag>
          </button>
          <ElEmpty v-if="!loading && patients.length === 0" description="暂无待诊患者" />
        </div>
      </GlassCard>

      <main class="work-panel">
        <GlassCard v-if="selectedPatient" class="patient-summary">
          <p class="summary-title">已选择患者</p>
          <div class="summary-main">
            <strong>{{ selectedPatient.realName }}</strong>
            <span>病历号：{{ selectedPatient.caseNumber }}</span>
          </div>
          <p v-if="selectedPatient.aiConsultSummary?.aiSummary || selectedPatient.aiConsultSummary?.chiefComplaint" class="summary-ai">
            <strong>AI 预问诊摘要：</strong>
            <span>{{ selectedPatient.aiConsultSummary.aiSummary || selectedPatient.aiConsultSummary.chiefComplaint }}</span>
          </p>
          <div class="summary-actions">
            <ElButton type="primary" @click="enterEncounter">进入流程（下一步）</ElButton>
          </div>
        </GlassCard>

        <GlassCard v-else class="work-panel-card">
          <ElEmpty description="请选择左侧待诊患者" />
        </GlassCard>
      </main>
    </section>
  </div>
</template>

<style scoped>
.queue-grid {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.patient-panel,
.patient-summary,
.work-panel-card {
  padding: var(--space-5);
}

.panel-heading h2 {
  margin: 0;
  font-size: 16px;
}

.panel-heading p {
  margin-block-start: 6px;
  color: var(--color-text-muted);
}

.patient-list {
  margin-block-start: var(--space-3);
  display: grid;
  gap: var(--space-2);
}

.patient-item {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-align: start;
  background: transparent;
  cursor: pointer;
}

.patient-item.is-active {
  border-color: rgba(31, 140, 255, 0.4);
  background: var(--color-primary-soft);
}

.patient-item span {
  color: var(--color-text-muted);
}

.summary-title {
  color: var(--color-text-muted);
}

.summary-main {
  display: grid;
  gap: 6px;
  margin-block-start: var(--space-3);
}

.summary-ai {
  margin-block-start: var(--space-3);
  color: var(--color-text-muted);
  line-height: 1.8;
}

.summary-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-block-start: var(--space-5);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}
</style>


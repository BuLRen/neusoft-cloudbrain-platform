<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElEmpty, ElMessage, ElOption, ElSelect } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import PatientCommunicationPanel from '@/modules/patient/components/PatientCommunicationPanel.vue'
import PatientGlucoseEntryForm from '@/modules/patient/components/PatientGlucoseEntryForm.vue'
import RevisitAdviceCard from '@/modules/patient/components/RevisitAdviceCard.vue'
import LastVisitSnapshotPanel from '@/modules/medtech/follow-up/components/LastVisitSnapshotPanel.vue'
import GlucoseForecastPanel from '@/modules/medtech/follow-up/components/GlucoseForecastPanel.vue'
import { clinicalRecordApi, type ClinicalVisitSummary } from '@/shared/api/modules/clinicalRecord'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { isEndocrineDepartment } from '@/shared/constants/followUpDepartments'
import { useAuthStore } from '@/app/stores/auth'
import type {
  PatientFollowUpPlanItem,
  PatientMedicationItem,
} from '@/shared/types/medtechFollowUp'

const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref('lastVisit')
const loading = ref(false)

const visits = ref<ClinicalVisitSummary[]>([])
const selectedRegisterId = ref<number | undefined>()
const followupPlans = ref<PatientFollowUpPlanItem[]>([])
const medications = ref<PatientMedicationItem[]>([])

const patientId = computed(() => {
  if (authStore.currentPatientId) return authStore.currentPatientId
  if (authStore.currentPatient?.patientId) return authStore.currentPatient.patientId
  const primary = authStore.patients.find((p) => p.isPrimary === 1) || authStore.patients[0]
  return primary?.patientId
})

const visitsLoading = ref(false)

const visitOptions = computed(() =>
  visits.value.map((item) => ({
    value: item.registerId,
    label: `${item.departmentName ?? '科室'} · ${item.visitDate?.slice(0, 10) ?? item.registerId}`,
  })),
)

const selectedVisit = computed(() =>
  visits.value.find((item) => item.registerId === selectedRegisterId.value),
)

const isEndocrineVisit = computed(() => isEndocrineDepartment(selectedVisit.value?.departmentId))

const pageSubtitle = computed(() =>
  isEndocrineVisit.value
    ? '上次看诊、居家血糖、复诊提醒与医患沟通'
    : '上次看诊、复诊提醒与医患沟通',
)

const revisitPlans = computed(() =>
  followupPlans.value.filter(
    (plan) => plan.followUpType === 'revisit' && plan.planStatus !== 'completed',
  ),
)

const glucosePanelRef = ref<InstanceType<typeof GlucoseForecastPanel> | null>(null)

function statusTone(status?: string) {
  if (status === 'completed') return 'success'
  if (status === 'pending' || status === 'overdue') return 'warning'
  return 'neutral'
}

function statusText(status?: string) {
  if (status === 'completed') return '已完成'
  if (status === 'pending') return '待完成'
  if (status === 'overdue') return '已逾期'
  if (status === 'cancelled') return '已取消'
  return '进行中'
}

function planTitle(plan: PatientFollowUpPlanItem) {
  const typeMap: Record<string, string> = {
    medication: '用药随访',
    side_effect: '副作用随访',
    recovery: '康复随访',
    revisit: '复诊提醒',
  }
  return typeMap[plan.followUpType ?? ''] ?? '随访计划'
}

function goToRegistration() {
  void router.push('/patient/registration')
}

async function loadVisits() {
  if (!patientId.value) return
  visitsLoading.value = true
  try {
    visits.value = await clinicalRecordApi.patientVisits(patientId.value)
    if (!selectedRegisterId.value && visits.value.length) {
      selectedRegisterId.value = visits.value[0]!.registerId
    }
    if (!visits.value.length) {
      await loadFallbackRegisterId()
    }
  } catch {
    ElMessage.error('加载就诊记录失败')
    await loadFallbackRegisterId()
  } finally {
    visitsLoading.value = false
  }
}

async function loadFallbackRegisterId() {
  if (!patientId.value || selectedRegisterId.value) return
  try {
    const plans = await medtechFollowUpApi.listPatientPlans({ patientId: patientId.value })
    const registerId = plans[0]?.registerId
    if (registerId) {
      selectedRegisterId.value = registerId
      visits.value = [
        {
          registerId,
          departmentName: '随访科室',
          visitDate: new Date().toISOString(),
          archived: false,
          patientVisible: true,
          archiveStatus: 'pending',
        },
      ]
    }
  } catch {
    // 由空状态提示用户
  }
}

async function loadPortalData() {
  if (!patientId.value) return
  loading.value = true
  try {
    const registerIds = selectedRegisterId.value ? [selectedRegisterId.value] : undefined
    const params = { patientId: patientId.value, registerIds }
    const [plans, meds] = await Promise.all([
      medtechFollowUpApi.listPatientPlans(params),
      medtechFollowUpApi.listPatientMedications(params),
    ])
    followupPlans.value = plans
    medications.value = meds
  } catch {
    ElMessage.error('加载随访数据失败')
  } finally {
    loading.value = false
  }
}

async function onGlucoseSubmitted() {
  await glucosePanelRef.value?.reloadAfterEntry()
}

async function markComplete(planId: number) {
  try {
    await medtechFollowUpApi.completePatientPlan(planId)
    ElMessage.success('已标记为完成')
    await loadPortalData()
  } catch {
    ElMessage.error('操作失败')
  }
}

watch(selectedRegisterId, () => {
  if (activeTab.value === 'glucose' && !isEndocrineVisit.value) {
    activeTab.value = 'lastVisit'
  }
  void loadPortalData()
})

watch(
  () => [authStore.sessionChecked, patientId.value] as const,
  ([checked, pid]) => {
    if (checked && pid) {
      void loadVisits()
    }
  },
  { immediate: true },
)

watch(activeTab, (tab) => {
  if (tab !== 'communication') {
    void loadPortalData()
  }
})
</script>

<template>
  <div class="patient-followup">
    <GlassCard class="followup-tabs-card">
      <div class="tabs-header">
        <h2>随访管理</h2>
        <p>{{ pageSubtitle }}</p>
      </div>

      <div class="register-picker">
        <span class="picker-label">就诊记录</span>
        <ElSelect v-model="selectedRegisterId" placeholder="选择就诊" style="min-width: 260px">
          <ElOption
            v-for="item in visitOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </div>

      <div class="tabs-nav">
        <button :class="['tab-btn', { active: activeTab === 'lastVisit' }]" @click="activeTab = 'lastVisit'">
          上次看诊
        </button>
        <button
          v-if="isEndocrineVisit"
          :class="['tab-btn', { active: activeTab === 'glucose' }]"
          @click="activeTab = 'glucose'"
        >
          血糖管理
        </button>
        <button :class="['tab-btn', { active: activeTab === 'revisit' }]" @click="activeTab = 'revisit'">
          复诊提醒
        </button>
        <button :class="['tab-btn', { active: activeTab === 'communication' }]" @click="activeTab = 'communication'">
          医患沟通
        </button>
        <button :class="['tab-btn', { active: activeTab === 'plans' }]" @click="activeTab = 'plans'">
          随访计划
        </button>
        <button :class="['tab-btn', { active: activeTab === 'medications' }]" @click="activeTab = 'medications'">
          用药提醒
        </button>
      </div>
    </GlassCard>

    <LastVisitSnapshotPanel
      v-if="activeTab === 'lastVisit' && selectedRegisterId"
      :register-id="selectedRegisterId"
      :patient-id="patientId"
      mode="patient"
    />
    <GlassCard v-else-if="activeTab === 'lastVisit'" v-loading="visitsLoading" class="empty-card">
      <ElEmpty :description="patientId ? '暂无就诊记录，请联系随访护士' : '正在加载患者档案…'" />
    </GlassCard>

    <template v-if="activeTab === 'glucose' && selectedRegisterId && isEndocrineVisit">
      <PatientGlucoseEntryForm
        :register-id="selectedRegisterId"
        :patient-id="patientId"
        @submitted="onGlucoseSubmitted"
      />
      <GlassCard class="glucose-card">
        <GlucoseForecastPanel
          ref="glucosePanelRef"
          :patient-id="patientId"
          :register-id="selectedRegisterId"
          mode="patient"
          @go-registration="goToRegistration"
        />
      </GlassCard>
    </template>
    <GlassCard v-else-if="activeTab === 'glucose'" v-loading="visitsLoading" class="empty-card">
      <ElEmpty :description="patientId ? '请先选择或等待加载就诊记录' : '正在加载患者档案…'" />
    </GlassCard>

    <template v-if="activeTab === 'revisit'">
      <GlassCard v-if="selectedRegisterId" class="revisit-card" v-loading="loading">
        <div class="section-header">
          <h3>复诊提醒</h3>
        </div>
        <p class="revisit-notice">
          随访系统仅提供复诊提醒，不参与挂号流程。如需到院复诊，请前往
          <button type="button" class="link-btn" @click="goToRegistration">我的挂号</button>
          自行预约。
        </p>

        <RevisitAdviceCard
          :register-id="selectedRegisterId"
          :patient-id="patientId"
          :show-glucose-advice="isEndocrineVisit"
          @go-registration="goToRegistration"
        />

        <div v-if="revisitPlans.length" class="revisit-plans">
          <h4>随访计划中的复诊提醒</h4>
          <div v-for="plan in revisitPlans" :key="plan.id" class="revisit-plan-item">
            <StatusTag :tone="statusTone(plan.planStatus)">{{ statusText(plan.planStatus) }}</StatusTag>
            <div class="revisit-plan-body">
              <strong>{{ planTitle(plan) }}</strong>
              <p v-if="plan.plannedDate">计划日期：{{ plan.plannedDate }}</p>
              <p v-if="plan.contentTemplate" class="plan-content">{{ plan.contentTemplate }}</p>
            </div>
            <button class="btn-outline" @click="goToRegistration">前往预约挂号</button>
          </div>
        </div>
        <ElEmpty v-else-if="!loading" description="暂无待完成的复诊提醒计划" />
      </GlassCard>
      <GlassCard v-else v-loading="visitsLoading" class="empty-card">
        <ElEmpty :description="patientId ? '请先选择就诊记录' : '正在加载患者档案…'" />
      </GlassCard>
    </template>

    <GlassCard v-if="activeTab === 'communication'" class="communication-card">
      <PatientCommunicationPanel />
    </GlassCard>

    <GlassCard v-if="activeTab === 'plans'" class="plans-card" v-loading="loading">
      <div class="section-header">
        <h3>随访计划</h3>
        <StatusTag tone="neutral">{{ followupPlans.length }} 项计划</StatusTag>
      </div>
      <div class="plans-list">
        <div v-for="plan in followupPlans" :key="plan.id" class="plan-item">
          <div class="plan-status">
            <StatusTag :tone="statusTone(plan.planStatus)">
              {{ statusText(plan.planStatus) }}
            </StatusTag>
          </div>
          <div class="plan-info">
            <div class="plan-main">
              <strong class="plan-title">{{ planTitle(plan) }}</strong>
              <span class="plan-doctor">{{ plan.doctorName ?? '随访医生' }}</span>
            </div>
            <div class="plan-meta">
              <span v-if="plan.plannedDate">📅 {{ plan.plannedDate }}</span>
            </div>
            <p v-if="plan.contentTemplate" class="plan-content">{{ plan.contentTemplate }}</p>
          </div>
          <div class="plan-actions">
            <button
              v-if="plan.followUpType === 'revisit' && plan.planStatus !== 'completed'"
              class="btn-outline"
              @click="goToRegistration"
            >
              前往预约挂号
            </button>
            <button
              v-if="plan.planStatus === 'pending' || plan.planStatus === 'overdue'"
              class="btn-outline"
              @click="markComplete(plan.id)"
            >
              标记完成
            </button>
          </div>
        </div>
        <div v-if="!followupPlans.length" class="empty-state">
          <p>暂无随访计划</p>
        </div>
      </div>
    </GlassCard>

    <GlassCard v-if="activeTab === 'medications'" class="medications-card" v-loading="loading">
      <div class="section-header">
        <h3>当前用药</h3>
        <StatusTag tone="warning">{{ medications.length }} 种药物</StatusTag>
      </div>
      <div class="medications-list">
        <div v-for="med in medications" :key="med.id" class="medication-item">
          <div class="med-header">
            <strong class="med-name">{{ med.drugName ?? '药品' }}</strong>
            <span class="med-dosage">{{ med.drugNumber ?? '—' }}</span>
          </div>
          <div class="med-details">
            <div class="med-row">
              <span class="med-label">用法：</span>
              <span>{{ med.drugUsage ?? '遵医嘱' }}</span>
            </div>
          </div>
        </div>
        <div v-if="!medications.length" class="empty-state">
          <p>暂无用药提醒</p>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.patient-followup {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.followup-tabs-card {
  padding: var(--space-5);
}

.tabs-header {
  display: grid;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.tabs-header h2 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.tabs-header p {
  color: var(--color-text-muted);
  margin: 0;
}

.register-picker {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.picker-label {
  font-size: 14px;
  color: var(--color-text-muted);
}

.tabs-nav {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: var(--space-3);
}

.tab-btn {
  padding: var(--space-2) var(--space-4);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  cursor: pointer;
}

.tab-btn.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.empty-card {
  padding: var(--space-8);
}

.plans-card,
.medications-card,
.glucose-card,
.revisit-card,
.communication-card {
  padding: var(--space-5);
}

.glucose-card :deep(.glucose-forecast__chart) {
  min-height: 360px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}

.section-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.revisit-notice {
  margin: 0 0 var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(59, 130, 246, 0.06);
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.link-btn {
  background: none;
  border: none;
  padding: 0;
  color: var(--color-primary);
  cursor: pointer;
  text-decoration: underline;
  font: inherit;
}

.revisit-plans {
  margin-top: var(--space-5);
  display: grid;
  gap: var(--space-3);
}

.revisit-plans h4 {
  margin: 0;
  font-size: 15px;
}

.revisit-plan-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.revisit-plan-body {
  flex: 1;
}

.revisit-plan-body p {
  margin: var(--space-1) 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.plan-content {
  margin-top: var(--space-2);
  line-height: 1.5;
}

.plans-list,
.medications-list {
  display: grid;
  gap: var(--space-4);
}

.plan-item,
.medication-item {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  display: flex;
  gap: var(--space-4);
  align-items: center;
}

.plan-info {
  flex: 1;
}

.plan-main {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.btn-primary,
.btn-outline {
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.btn-outline {
  background: transparent;
  border: 1px solid var(--color-border);
}

.empty-state {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-muted);
}
</style>

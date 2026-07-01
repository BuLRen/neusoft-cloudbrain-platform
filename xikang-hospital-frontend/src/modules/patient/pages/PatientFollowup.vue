<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElOption, ElSelect } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import PatientCommunicationPanel from '@/modules/patient/components/PatientCommunicationPanel.vue'
import PatientGlucoseEntryForm from '@/modules/patient/components/PatientGlucoseEntryForm.vue'
import LastVisitSnapshotPanel from '@/modules/medtech/follow-up/components/LastVisitSnapshotPanel.vue'
import GlucoseForecastPanel from '@/modules/medtech/follow-up/components/GlucoseForecastPanel.vue'
import { clinicalRecordApi, type ClinicalVisitSummary } from '@/shared/api/modules/clinicalRecord'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { useAuthStore } from '@/app/stores/auth'
import type {
  PatientFollowUpPlanItem,
  PatientMedicationItem,
} from '@/shared/types/medtechFollowUp'

const authStore = useAuthStore()
const activeTab = ref('lastVisit')
const loading = ref(false)

const visits = ref<ClinicalVisitSummary[]>([])
const selectedRegisterId = ref<number | undefined>()
const followupPlans = ref<PatientFollowUpPlanItem[]>([])
const medications = ref<PatientMedicationItem[]>([])

const patientId = computed(() => authStore.currentPatientId || authStore.currentPatient?.patientId)

const visitOptions = computed(() =>
  visits.value.map((item) => ({
    value: item.registerId,
    label: `${item.departmentName ?? '科室'} · ${item.visitDate?.slice(0, 10) ?? item.registerId}`,
  })),
)

const revisitForm = ref({ reason: '', urgency: 'normal' as 'normal' | 'urgent' })
const revisitLoading = ref(false)

const glucosePanelKey = ref(0)

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

async function loadVisits() {
  if (!patientId.value) return
  try {
    visits.value = await clinicalRecordApi.patientVisits(patientId.value)
    if (!selectedRegisterId.value && visits.value.length) {
      selectedRegisterId.value = visits.value[0]!.registerId
    }
  } catch {
    ElMessage.error('加载就诊记录失败')
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
  glucosePanelKey.value += 1
}

async function submitRevisit() {
  if (!revisitForm.value.reason.trim()) {
    ElMessage.warning('请填写复诊原因')
    return
  }
  if (!selectedRegisterId.value) {
    ElMessage.warning('请先选择就诊记录')
    return
  }
  revisitLoading.value = true
  try {
    await medtechFollowUpApi.submitRevisitRequest(
      {
        registerId: selectedRegisterId.value,
        reason: revisitForm.value.reason.trim(),
        urgency: revisitForm.value.urgency,
      },
      { patientId: patientId.value },
    )
    ElMessage.success('复诊申请已提交')
    revisitForm.value = { reason: '', urgency: 'normal' }
  } catch {
    ElMessage.error('提交失败')
  } finally {
    revisitLoading.value = false
  }
}

function goToCommunication(prefill?: string) {
  activeTab.value = 'communication'
  if (prefill) {
    revisitForm.value.reason = prefill
  }
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
  void loadPortalData()
})

watch(activeTab, (tab) => {
  if (tab !== 'communication') {
    void loadPortalData()
  }
})

onMounted(async () => {
  await loadVisits()
  await loadPortalData()
})
</script>

<template>
  <div class="patient-followup">
    <GlassCard class="followup-tabs-card">
      <div class="tabs-header">
        <h2>随访管理</h2>
        <p>上次看诊、居家血糖、复诊申请与医患沟通</p>
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
        <button :class="['tab-btn', { active: activeTab === 'glucose' }]" @click="activeTab = 'glucose'">
          血糖管理
        </button>
        <button :class="['tab-btn', { active: activeTab === 'revisit' }]" @click="activeTab = 'revisit'">
          复诊与沟通
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
      compact
    />

    <template v-if="activeTab === 'glucose' && selectedRegisterId">
      <PatientGlucoseEntryForm
        :register-id="selectedRegisterId"
        :patient-id="patientId"
        @submitted="onGlucoseSubmitted"
      />
      <GlassCard class="glucose-card">
        <GlucoseForecastPanel
          :key="glucosePanelKey"
          :patient-id="patientId"
          :register-id="selectedRegisterId"
          mode="patient"
          compact
          @revisit="goToCommunication('根据血糖预测建议，申请复诊调整用药')"
        />
      </GlassCard>
    </template>

    <GlassCard v-if="activeTab === 'revisit'" class="revisit-card">
      <div class="section-header">
        <h3>申请复诊</h3>
      </div>
      <div class="revisit-form">
        <textarea
          v-model="revisitForm.reason"
          class="form-textarea"
          rows="4"
          placeholder="请描述复诊原因，如：血糖持续偏高、出现低血糖症状..."
        />
        <div class="urgency-row">
          <label>
            <input v-model="revisitForm.urgency" type="radio" value="normal" />
            普通
          </label>
          <label>
            <input v-model="revisitForm.urgency" type="radio" value="urgent" />
            紧急
          </label>
        </div>
        <button class="btn-primary" :disabled="revisitLoading" @click="submitRevisit">
          {{ revisitLoading ? '提交中...' : '提交复诊申请' }}
        </button>
      </div>
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
          </div>
          <div class="plan-actions">
            <button
              v-if="plan.followUpType === 'revisit' && plan.planStatus !== 'completed'"
              class="btn-outline"
              @click="goToCommunication()"
            >
              申请复诊
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

.plans-card,
.medications-card,
.glucose-card,
.revisit-card {
  padding: var(--space-5);
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

.revisit-form {
  display: grid;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.form-textarea {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  font-family: inherit;
  resize: vertical;
}

.urgency-row {
  display: flex;
  gap: var(--space-4);
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

.btn-primary {
  background: var(--color-primary);
  color: white;
  border: none;
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

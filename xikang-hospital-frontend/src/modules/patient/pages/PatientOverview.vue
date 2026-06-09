<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'
import GlassCard from '@/shared/components/GlassCard.vue'
import { registrationApi, type DoctorInfo } from '@/shared/api/modules/registration'
import type { DepartmentOption } from '@/shared/types/registration'
import { Warning } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const medicalServices = [
  { key: 'registration', title: '我的挂号', desc: '查看已预约挂号，或继续发起新的导诊挂号', icon: '📋', path: '/patient/registration' },
  { key: 'records', title: '电子病历', desc: '集中查看本次就诊形成的病历、医嘱和检查入口', icon: '📄', path: '/patient/records' },
  { key: 'prescription', title: '我的处方', desc: '查看医生开立的处方和用药信息', icon: '💊', path: '/patient/prescription' },
  { key: 'followup', title: '随访管理', desc: '提交用药反馈，查看复诊和康复随访安排', icon: '📅', path: '/patient/followup' },
]

const healthServices = [
  { key: 'previsit', title: 'AI 预问诊', desc: '挂号后补充主诉、病史和过敏史，辅助医生接诊', icon: '💬', path: '/patient/registration' },
  { key: 'triage', title: '智能导诊', desc: '不确定科室时，先描述症状获取推荐', icon: '🤖', path: '/patient/registration' },
  { key: 'profile', title: '就诊人管理', desc: '维护本人和家人的基础信息、过敏史与账户余额', icon: '👤', path: '/patient/profile' },
]

const guideSteps = [
  { title: '先导诊', desc: '不确定科室时，先通过 AI 描述症状并获得推荐。' },
  { title: '选排班', desc: '根据推荐科室选择医生、日期和号别。' },
  { title: '确认挂号', desc: '核对就诊人、科室、医生、时间和费用后提交。' },
  { title: '预问诊', desc: '挂号完成后补充病史，医生端可用于接诊参考。' },
]

const departments = ref<DepartmentOption[]>([])
const departmentLoading = ref(false)
const selectedDepartment = ref<DepartmentOption | null>(null)
const departmentDoctors = ref<DoctorInfo[]>([])
const doctorLoading = ref(false)

const currentPatient = computed(() => authStore.currentPatient)
const visibleDepartments = computed(() => departments.value.slice(0, 8))

async function loadDepartments() {
  departmentLoading.value = true
  try {
    departments.value = await registrationApi.departments()
    selectedDepartment.value = departments.value[0] || null
    if (selectedDepartment.value) {
      await loadDepartmentDoctors(selectedDepartment.value)
    }
  } catch (error) {
    console.warn('加载科室失败:', error)
    departments.value = []
    selectedDepartment.value = null
    departmentDoctors.value = []
  } finally {
    departmentLoading.value = false
  }
}

async function loadDepartmentDoctors(department: DepartmentOption) {
  selectedDepartment.value = department
  departmentDoctors.value = []
  doctorLoading.value = true
  try {
    departmentDoctors.value = await registrationApi.getDoctorsByDepartment(department.id)
  } catch (error) {
    console.warn('加载科室医生失败:', error)
    departmentDoctors.value = []
  } finally {
    doctorLoading.value = false
  }
}

function navigateTo(path: string) {
  router.push(path)
}

onMounted(loadDepartments)
</script>

<template>
  <div class="patient-overview">
    <GlassCard class="hero-card">
      <div class="hero-content">
        <div class="hero-copy">
          <span class="hero-eyebrow">互联网医院患者端</span>
          <h1>AI 智慧就医助手</h1>
          <p>
            不确定挂哪个科时，先描述症状完成 AI 导诊；系统会按现有流程引导您选择排班、确认挂号，并在挂号后继续进行 AI 预问诊。
          </p>
          <div class="hero-actions">
            <button class="primary-action" @click="navigateTo('/patient/registration')">开始 AI 导诊挂号</button>
            <button class="secondary-action" @click="navigateTo('/patient/records')">查看电子病历</button>
          </div>
        </div>
        <div class="hero-flow" aria-label="挂号流程">
          <div v-for="(step, index) in guideSteps" :key="step.title" class="flow-step">
            <span class="flow-index">{{ index + 1 }}</span>
            <div>
              <strong>{{ step.title }}</strong>
              <span>{{ step.desc }}</span>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>

    <GlassCard class="service-card">
      <template #header>
        <div class="section-heading">
          <div class="card-header">
            <span class="card-icon">🧾</span>
            <span>我的就医服务</span>
          </div>
          <p>围绕挂号后的就诊记录、病历、处方和随访管理，集中处理患者常用事项。</p>
        </div>
      </template>
      <div class="service-grid">
        <button
          v-for="service in medicalServices"
          :key="service.key"
          class="service-item"
          @click="navigateTo(service.path)"
        >
          <span class="service-icon">{{ service.icon }}</span>
          <strong>{{ service.title }}</strong>
          <span>{{ service.desc }}</span>
        </button>
      </div>
    </GlassCard>

    <GlassCard class="department-card">
      <template #header>
        <div class="section-heading">
          <div class="card-header">
            <span class="card-icon">🏥</span>
            <span>科室导航</span>
          </div>
          <p>已明确就诊方向时，可先查看科室简介和医生信息，再进入现有导诊挂号流程。</p>
        </div>
      </template>
      <div v-if="departmentLoading" class="empty-panel">正在加载科室...</div>
      <div v-else-if="!visibleDepartments.length" class="empty-panel">暂无科室数据，请先在后台维护科室信息。</div>
      <div v-else class="department-layout">
        <div class="department-grid">
          <button
            v-for="department in visibleDepartments"
            :key="department.id"
            class="department-item"
            :class="{ 'is-active': selectedDepartment?.id === department.id }"
            @click="loadDepartmentDoctors(department)"
          >
            <strong>{{ department.name }}</strong>
            <span>{{ department.description || '暂无简介，点击查看科室医生' }}</span>
          </button>
        </div>

        <div class="department-detail">
          <div class="department-detail__header">
            <div>
              <span class="detail-kicker">当前科室</span>
              <h3>{{ selectedDepartment?.name || '未选择科室' }}</h3>
            </div>
            <button class="detail-register-btn" :disabled="!selectedDepartment" @click="navigateTo('/patient/registration')">
              去挂号
            </button>
          </div>
          <p class="department-description">
            {{ selectedDepartment?.description || '该科室暂无简介，后续可在科室主数据中维护。' }}
          </p>
          <div class="doctor-section-title">科室医生</div>
          <div v-if="doctorLoading" class="doctor-empty">正在加载医生...</div>
          <div v-else-if="!departmentDoctors.length" class="doctor-empty">暂无医生数据，请先维护该科室医生。</div>
          <div v-else class="doctor-list">
            <div v-for="doctor in departmentDoctors" :key="doctor.id" class="doctor-card">
              <div class="doctor-avatar">{{ doctor.realname?.[0] || '医' }}</div>
              <div>
                <strong>{{ doctor.realname }}</strong>
                <span>{{ doctor.registName || '医生' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>

    <div class="bottom-grid">
      <GlassCard class="service-card">
        <template #header>
          <div class="section-heading">
            <div class="card-header">
              <span class="card-icon">🌿</span>
              <span>健康管理</span>
            </div>
            <p>管理预问诊、就诊人和随访信息，形成连续的个人健康服务入口。</p>
          </div>
        </template>
        <div class="health-service-list">
          <button
            v-for="service in healthServices"
            :key="service.key"
            class="health-service-item"
            @click="navigateTo(service.path)"
          >
            <span>{{ service.icon }}</span>
            <div>
              <strong>{{ service.title }}</strong>
              <p>{{ service.desc }}</p>
            </div>
          </button>
        </div>
      </GlassCard>

      <GlassCard class="guide-card">
        <template #header>
          <div class="section-heading">
            <div class="card-header">
              <span class="card-icon">📌</span>
              <span>就医指南</span>
            </div>
            <p>首页不展示未接入的模拟数据；充值、余额等账户操作请在个人中心完成。</p>
          </div>
        </template>
        <div class="guide-list">
          <div v-for="(step, index) in guideSteps" :key="step.title" class="guide-item">
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ step.title }}</strong>
              <p>{{ step.desc }}</p>
            </div>
          </div>
        </div>
        <div v-if="currentPatient?.allergyHistory" class="allergy-warning">
          <el-icon><Warning /></el-icon>
          <span>当前患者过敏史：{{ currentPatient.allergyHistory }}。就诊时请主动告知医生。</span>
        </div>
      </GlassCard>
    </div>
  </div>
</template>

<style scoped>
.patient-overview {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.hero-card,
.service-card,
.department-card,
.guide-card {
  padding: var(--space-5);
}

.hero-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: var(--space-6);
  align-items: center;
}

.hero-copy {
  display: grid;
  gap: var(--space-4);
}

.hero-eyebrow {
  width: fit-content;
  padding: var(--space-1) var(--space-3);
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}

.hero-copy h1 {
  margin: 0;
  font-size: 34px;
  letter-spacing: -0.04em;
}

.hero-copy p {
  max-width: 720px;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 15px;
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.primary-action,
.secondary-action,
.detail-register-btn {
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-5);
  font-weight: 700;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.primary-action,
.detail-register-btn {
  border: 0;
  background: var(--color-primary);
  color: #fff;
}

.secondary-action {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

.primary-action:hover,
.secondary-action:hover,
.detail-register-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.detail-register-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.hero-flow {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-primary-soft) 0%, rgba(255, 255, 255, 0.8) 100%);
}

.flow-step {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.flow-index {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex: 0 0 auto;
}

.flow-step div {
  display: grid;
  gap: 2px;
}

.flow-step strong {
  color: var(--color-text);
}

.flow-step span:last-child {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 17px;
  font-weight: 700;
}

.section-heading {
  display: grid;
  gap: var(--space-2);
}

.section-heading p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.card-icon {
  font-size: 18px;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}

.service-item {
  display: grid;
  gap: var(--space-3);
  min-height: 148px;
  padding: var(--space-5);
  text-align: left;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.service-item:hover,
.health-service-item:hover,
.department-item:hover,
.department-item.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  transform: translateY(-2px);
}

.service-icon {
  font-size: 28px;
}

.service-item strong {
  color: var(--color-text);
  font-size: 16px;
}

.service-item span:last-child {
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.empty-panel,
.doctor-empty {
  padding: var(--space-5);
  color: var(--color-text-muted);
  text-align: center;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.department-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--space-4);
  align-items: start;
}

.department-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.department-item {
  display: grid;
  gap: var(--space-2);
  min-height: 96px;
  padding: var(--space-4);
  text-align: left;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.department-item.is-active {
  box-shadow: 0 8px 24px rgba(31, 140, 255, 0.12);
}

.department-item strong {
  font-size: 15px;
  color: var(--color-text);
}

.department-item span {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.department-detail {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-primary-soft) 0%, rgba(255, 255, 255, 0.82) 100%);
}

.department-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.detail-kicker {
  color: var(--color-text-muted);
  font-size: 12px;
}

.department-detail h3 {
  margin: var(--space-1) 0 0;
  font-size: 22px;
}

.department-description {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.doctor-section-title {
  font-size: 14px;
  font-weight: 700;
}

.doctor-list {
  display: grid;
  gap: var(--space-3);
}

.doctor-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.76);
}

.doctor-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  background: var(--gradient-primary);
}

.doctor-card div:last-child {
  display: grid;
  gap: 2px;
}

.doctor-card strong {
  color: var(--color-text);
}

.doctor-card span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.9fr);
  gap: var(--space-5);
}

.health-service-list,
.guide-list {
  display: grid;
  gap: var(--space-3);
}

.health-service-item {
  display: flex;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-4);
  text-align: left;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.health-service-item > span {
  font-size: 24px;
}

.health-service-item div {
  display: grid;
  gap: var(--space-1);
}

.health-service-item strong {
  color: var(--color-text);
}

.health-service-item p,
.guide-item p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.guide-item {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.guide-item > span {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  font-size: 12px;
  font-weight: 700;
}

.guide-item div {
  display: grid;
  gap: var(--space-1);
}

.allergy-warning {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin-top: var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  background: rgba(239, 77, 90, 0.12);
  color: var(--color-danger);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .hero-content,
  .department-layout,
  .bottom-grid {
    grid-template-columns: 1fr;
  }

  .service-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .patient-overview {
    width: 100%;
    margin: 0;
  }

  .service-grid,
  .department-grid {
    grid-template-columns: 1fr;
  }

  .hero-copy h1 {
    font-size: 26px;
  }
}
</style>

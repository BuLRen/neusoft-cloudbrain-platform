<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'
import GlassCard from '@/shared/components/GlassCard.vue'
import { registrationApi } from '@/shared/api/modules/registration'
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

const visitPreparations = [
  { title: '确认就诊人信息', desc: '请核对本人或家属的姓名、证件号、联系方式是否正确。' },
  { title: '完善健康资料', desc: '如有过敏史、既往病史、长期用药，请提前维护。' },
  { title: '整理病情描述', desc: '记录症状出现时间、持续情况、诱因和已用药物。' },
  { title: '保留检查资料', desc: '如近期有检查检验报告，可在就诊时提供给医生参考。' },
]

const guideSteps = [
  { title: '先导诊', desc: '不确定科室时，先通过 AI 描述症状并获得推荐。' },
  { title: '选排班', desc: '根据推荐科室选择医生、日期和号别。' },
  { title: '确认挂号', desc: '核对就诊人、科室、医生、时间和费用后提交。' },
  { title: '预问诊', desc: '挂号完成后补充病史，医生端可用于接诊参考。' },
]

const departments = ref<DepartmentOption[]>([])
const departmentLoading = ref(false)
const doctorCountMap = ref<Record<number, number>>({})
const departmentPage = ref(1)
const departmentsPerPage = 8

const currentPatient = computed(() => authStore.currentPatient)
const totalDepartmentPages = computed(() => Math.max(1, Math.ceil(departments.value.length / departmentsPerPage)))
const visibleDepartments = computed(() => {
  const start = (departmentPage.value - 1) * departmentsPerPage
  return departments.value.slice(start, start + departmentsPerPage)
})

function getDoctorCount(departmentId: number) {
  return doctorCountMap.value[departmentId] ?? 0
}

async function loadDepartments() {
  departmentLoading.value = true
  try {
    departments.value = await registrationApi.departments('临床科室')
    departmentPage.value = 1
    await loadDoctorCounts(departments.value)
  } catch (error) {
    console.warn('加载科室失败:', error)
    departments.value = []
    doctorCountMap.value = {}
  } finally {
    departmentLoading.value = false
  }
}

async function loadDoctorCounts(departmentList: DepartmentOption[]) {
  const countEntries = await Promise.all(
    departmentList.map(async (department) => {
      try {
        const doctors = await registrationApi.getDoctorsByDepartment(department.id)
        return [department.id, doctors.length] as const
      } catch (error) {
        console.warn(`加载${department.name}医生数量失败:`, error)
        return [department.id, 0] as const
      }
    }),
  )
  doctorCountMap.value = Object.fromEntries(countEntries)
}

function changeDepartmentPage(direction: number) {
  const nextPage = departmentPage.value + direction
  if (nextPage >= 1 && nextPage <= totalDepartmentPages.value) {
    departmentPage.value = nextPage
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
        <div class="section-heading department-heading">
          <div>
            <div class="card-header">
              <span class="card-icon">🏥</span>
              <span>临床科室导航</span>
            </div>
            <p>展示数据库中已维护的全部临床科室，可先进入科室详情查看医生团队和可约号源。</p>
          </div>
          <div v-if="departments.length" class="department-total">共 {{ departments.length }} 个科室</div>
        </div>
      </template>
      <div v-if="departmentLoading" class="empty-panel">正在加载科室...</div>
      <div v-else-if="!visibleDepartments.length" class="empty-panel">暂无临床科室数据，请先在后台维护科室信息。</div>
      <div v-else class="department-panel">
        <div class="department-grid">
          <article v-for="department in visibleDepartments" :key="department.id" class="department-item">
            <div class="department-item__top">
              <strong>{{ department.name }}</strong>
              <span>{{ getDoctorCount(department.id) }} 位医生</span>
            </div>
            <p>{{ department.description }}</p>
            <button class="department-register-btn" @click="navigateTo(`/patient/departments/${department.id}`)">查看详情</button>
          </article>
        </div>

        <div v-if="totalDepartmentPages > 1" class="department-pagination">
          <button :disabled="departmentPage === 1" @click="changeDepartmentPage(-1)">上一页</button>
          <span>{{ departmentPage }} / {{ totalDepartmentPages }}</span>
          <button :disabled="departmentPage === totalDepartmentPages" @click="changeDepartmentPage(1)">下一页</button>
        </div>
      </div>
    </GlassCard>

    <div class="bottom-grid">
      <GlassCard class="service-card">
        <template #header>
          <div class="section-heading">
            <div class="card-header">
              <span class="card-icon">🧳</span>
              <span>就诊前准备</span>
            </div>
            <p>挂号和就诊前可提前核对信息、整理资料，帮助医生更快了解病情。</p>
          </div>
        </template>
        <div class="preparation-list">
          <div v-for="(item, index) in visitPreparations" :key="item.title" class="preparation-item">
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.desc }}</p>
            </div>
          </div>
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
.secondary-action {
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-5);
  font-weight: 700;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.primary-action {
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
.secondary-action:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
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

.service-item:hover {
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

.department-heading {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
}

.department-total {
  padding: var(--space-2) var(--space-3);
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}

.department-panel {
  display: grid;
  gap: var(--space-4);
}

.department-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.department-item {
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: var(--space-3);
  min-height: 210px;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--color-surface) 100%);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
  transition: all var(--duration-base) var(--ease-standard);
}

.department-item:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  transform: translateY(-2px);
}

.department-item__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.department-item strong {
  color: var(--color-text);
  font-size: 17px;
}

.department-item__top span {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(31, 140, 255, 0.1);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}

.department-item p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.8;
}

.department-register-btn {
  width: 100%;
  border: 0;
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.department-register-btn:hover {
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.department-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
}

.department-pagination button {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-2) var(--space-4);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
}

.department-pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.department-pagination span {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.9fr);
  gap: var(--space-5);
}

.preparation-list,
.guide-list {
  display: grid;
  gap: var(--space-3);
}

.preparation-item {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.preparation-item > span {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}

.preparation-item div {
  display: grid;
  gap: var(--space-1);
}

.preparation-item strong {
  color: var(--color-text);
}

.preparation-item p,
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
  .bottom-grid {
    grid-template-columns: 1fr;
  }

  .service-grid,
  .department-grid {
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

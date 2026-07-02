<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlassCard from '@/shared/components/GlassCard.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import type { DepartmentOption } from '@/shared/types/registration'

const router = useRouter()

const medicalServices = [
  { key: 'registration', title: '我的挂号', desc: '查看已预约挂号，或继续发起新的导诊挂号', icon: '📋', path: '/patient/registration' },
  { key: 'payment', title: '我的账单', desc: '查看挂号、药品等费用明细，余额支付', icon: '💳', path: '/patient/payment' },
  { key: 'records', title: '电子病历', desc: '集中查看本次就诊形成的病历、医嘱和检查入口', icon: '📄', path: '/patient/records' },
  { key: 'prescription', title: '我的处方', desc: '查看医生开立的处方和用药信息', icon: '💊', path: '/patient/prescription' },
  { key: 'followup', title: '随访管理', desc: '查看复诊提醒、居家血糖与医患沟通', icon: '📅', path: '/patient/followup' },
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
          <div class="hero-flow-guide">
            <div v-for="(step, index) in guideSteps" :key="step.title" class="flow-step">
              <span class="flow-index">{{ index + 1 }}</span>
              <div>
                <strong>{{ step.title }}</strong>
                <span>{{ step.desc }}</span>
              </div>
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
          <article
            v-for="department in visibleDepartments"
            :key="department.id"
            class="department-item"
            role="button"
            tabindex="0"
            @click="navigateTo(`/patient/departments/${department.id}`)"
            @keydown.enter="navigateTo(`/patient/departments/${department.id}`)"
          >
            <div class="department-item__top">
              <strong>{{ department.name }}</strong>
              <span class="doctor-count">{{ getDoctorCount(department.id) }} 位医生</span>
            </div>
            <p>{{ department.description }}</p>
            <span class="department-cta">
              查看科室详情
              <span class="department-arrow">→</span>
            </span>
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
          <div v-for="item in visitPreparations" :key="item.title" class="preparation-item">
            <span class="preparation-check">✓</span>
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
      </GlassCard>
    </div>
  </div>
</template>


<style scoped>
/* ====================================================================
   PatientOverview — 医院系统首页
   设计签名：渐变 hero + 信息驱动 + 编号仅用于真顺序
   ==================================================================== */

.patient-overview {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 88%;
  max-width: 1320px;
  margin: 0 auto;
  padding: var(--space-4) 0;
}

.hero-card,
.service-card,
.department-card,
.guide-card {
  padding: var(--space-6);
}

/* ===================== HERO ===================== */
.hero-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-6);
}

.hero-copy {
  display: grid;
  gap: var(--space-4);
}

.hero-eyebrow {
  width: fit-content;
  padding: 5px 12px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin: 0;
  font-size: 38px;
  font-weight: 800;
  letter-spacing: -0.035em;
  line-height: 1.1;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-copy p {
  max-width: 560px;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 14.5px;
  line-height: 1.85;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.primary-action,
.secondary-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: var(--radius-md);
  padding: 13px 24px;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard),
              background var(--duration-base) var(--ease-standard),
              border-color var(--duration-base) var(--ease-standard);
}

.primary-action {
  border: 0;
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: 0 8px 22px rgba(31, 140, 255, 0.28);
}

.primary-action:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(31, 140, 255, 0.38);
}

.secondary-action {
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface-strong);
  color: var(--color-text);
}

.secondary-action:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

/* —— Hero 流程引导（编号 + 描述，单列）—— */
.hero-flow-guide {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-4);
  padding-top: var(--space-5);
  border-top: 1px dashed var(--color-border);
}

.flow-step {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: 4px 0;
}

.flow-index {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 11.5px;
  font-weight: 700;
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}

.flow-step div {
  display: grid;
  gap: 2px;
}

.flow-step strong {
  color: var(--color-text);
  font-size: 13.5px;
  font-weight: 600;
}

.flow-step span:last-child {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

/* ===================== 区块标题 ===================== */
.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.card-icon {
  font-size: 19px;
  filter: drop-shadow(0 2px 4px rgba(31, 140, 255, 0.15));
}

.section-heading {
  display: grid;
  gap: var(--space-2);
}

.section-heading p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.75;
}

/* ===================== 服务网格 ===================== */
.service-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-4);
}

.service-item {
  position: relative;
  display: grid;
  gap: var(--space-2);
  align-content: start;
  min-height: 158px;
  padding: var(--space-5);
  text-align: left;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  cursor: pointer;
  overflow: hidden;
  transition: transform var(--duration-fast) var(--ease-standard),
              border-color var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard);
}

.service-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--gradient-primary);
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-standard);
}

.service-item:hover {
  transform: translateY(-3px);
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-md);
}

.service-item:hover::before {
  opacity: 1;
}

.service-icon {
  font-size: 30px;
  line-height: 1;
}

.service-item strong {
  color: var(--color-text);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.service-item span:last-child {
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

/* ===================== 科室网格 ===================== */
.department-heading {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
}

.department-total {
  padding: 5px 12px;
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
  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: var(--space-3);
  min-height: 210px;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-standard),
              border-color var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard);
}

.department-item:hover {
  transform: translateY(-3px);
  border-color: transparent;
  box-shadow: 0 0 0 2px var(--color-primary),
              var(--shadow-md);
}

.department-item:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--color-primary-soft),
              0 0 0 5px var(--color-primary);
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
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.3;
}

.doctor-count {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11.5px;
  font-weight: 700;
  white-space: nowrap;
}

.department-item p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.75;
}

.department-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 600;
}

.department-arrow {
  transition: transform var(--duration-base) var(--ease-standard);
}

.department-item:hover .department-arrow {
  transform: translateX(4px);
}

.department-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
}

.department-pagination button {
  min-width: 72px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 8px 16px;
  background: var(--color-surface-strong);
  color: var(--color-text);
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.department-pagination button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.department-pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.department-pagination span {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  min-width: 60px;
  text-align: center;
}

.empty-panel {
  padding: var(--space-8);
  color: var(--color-text-muted);
  text-align: center;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
  background: var(--color-control);
  font-size: 14px;
}

/* ===================== 底部双栏 ===================== */
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

/* 就诊前准备：checklist 语义，用 ✓ 而不是编号 */
.preparation-item {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  transition: border-color var(--duration-base) var(--ease-standard),
              background var(--duration-base) var(--ease-standard);
}

.preparation-item:hover {
  border-color: var(--color-border-strong);
  background: var(--color-control-hover);
}

.preparation-check {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: rgba(32, 180, 134, 0.12);
  color: var(--color-success);
  font-size: 13px;
  font-weight: 700;
}

.preparation-item div {
  display: grid;
  gap: 4px;
}

.preparation-item strong {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
}

.preparation-item p,
.guide-item p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

/* 就医指南：真顺序，用编号 */
.guide-item {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  border-left: 3px solid var(--color-primary);
  transition: background var(--duration-base) var(--ease-standard);
}

.guide-item:hover {
  background: var(--color-primary-soft);
}

.guide-item > span {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 12.5px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 3px 8px rgba(31, 140, 255, 0.22);
}

.guide-item div {
  display: grid;
  gap: 4px;
}

.guide-item strong {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
}

/* ===================== 响应式 ===================== */
@media (max-width: 1200px) {
  .bottom-grid {
    grid-template-columns: 1fr;
  }

  .service-grid,
  .department-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-flow-guide {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .patient-overview {
    width: 100%;
    margin: 0;
    padding: 0 var(--space-4);
  }

  .service-grid,
  .department-grid,
  .hero-flow-guide {
    grid-template-columns: 1fr;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .hero-card,
  .service-card,
  .department-card,
  .guide-card {
    padding: var(--space-5);
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}
</style>

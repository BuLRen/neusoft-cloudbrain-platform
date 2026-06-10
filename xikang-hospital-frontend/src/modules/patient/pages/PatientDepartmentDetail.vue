<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi, scheduleApi, type DoctorInfo } from '@/shared/api/modules/registration'
import type { DepartmentOption, RegistLevelOption, SchedulingOption } from '@/shared/types/registration'

interface DayScheduleSummary {
  date: string
  label: string
  weekLabel: string
  schedules: SchedulingOption[]
  loading: boolean
}

const route = useRoute()
const router = useRouter()

const department = ref<DepartmentOption | null>(null)
const doctors = ref<DoctorInfo[]>([])
const registLevels = ref<RegistLevelOption[]>([])
const daySummaries = ref<DayScheduleSummary[]>([])
const selectedDate = ref(formatDate(new Date()))
const loading = ref(false)
const errorMessage = ref('')

const departmentId = computed(() => Number(route.params.departmentId))
const selectedDay = computed(() => daySummaries.value.find(day => day.date === selectedDate.value))
const selectedSchedules = computed(() => selectedDay.value?.schedules || [])
const totalAvailableSchedules = computed(() => daySummaries.value.reduce((sum, day) => sum + day.schedules.length, 0))
const totalAvailableQuota = computed(() => daySummaries.value.reduce((sum, day) => sum + day.schedules.reduce((inner, schedule) => inner + Number(schedule.availableQuota || 0), 0), 0))
const hasAvailableSchedule = computed(() => totalAvailableSchedules.value > 0)
const todaySchedules = computed(() => daySummaries.value[0]?.schedules || [])
const todayQuota = computed(() => todaySchedules.value.reduce((sum, schedule) => sum + Number(schedule.availableQuota || 0), 0))

const schedulesBySlot = computed(() => {
  return selectedSchedules.value.reduce<Record<string, SchedulingOption[]>>((groups, schedule) => {
    const slot = schedule.timeSlot || schedule.timeSlotName || '其他'
    if (!groups[slot]) groups[slot] = []
    groups[slot].push(schedule)
    return groups
  }, {})
})

const sortedSlotEntries = computed(() => {
  const order = ['上午', '下午', '晚上', '其他']
  return Object.entries(schedulesBySlot.value).sort(([left], [right]) => order.indexOf(left) - order.indexOf(right))
})

const levelPriceMap = computed(() => {
  return registLevels.value.reduce<Record<number, number>>((map, level) => {
    map[level.id] = Number(level.price || 0)
    return map
  }, {})
})

const doctorCards = computed(() => {
  return doctors.value.map((doctor) => {
    const schedules = selectedSchedules.value.filter(schedule => schedule.physicianId === doctor.id)
    const firstSchedule = schedules[0]
    return {
      ...doctor,
      schedules,
      hasSchedule: schedules.length > 0,
      displayPrice: firstSchedule?.price ?? levelPriceMap.value[doctor.registLevelId] ?? 0,
      availableQuota: schedules.reduce((sum, schedule) => sum + Number(schedule.availableQuota || 0), 0),
    }
  }).sort((left, right) => Number(right.hasSchedule) - Number(left.hasSchedule) || (right.registLevelId || 0) - (left.registLevelId || 0))
})

const symptomTags = computed(() => getDepartmentTags(department.value?.name || ''))
const departmentStatusText = computed(() => {
  if (todaySchedules.value.length) return `今日 ${todaySchedules.value.length} 个可约时段`
  if (hasAvailableSchedule.value) return `近7日 ${totalAvailableSchedules.value} 个可约时段`
  return '暂无已发布号源'
})

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatShortDate(date: Date) {
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function weekLabel(date: Date, index: number) {
  if (index === 0) return '今天'
  const labels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return labels[date.getDay()]
}

function buildNextSevenDays(): DayScheduleSummary[] {
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date()
    date.setDate(date.getDate() + index)
    return {
      date: formatDate(date),
      label: formatShortDate(date),
      weekLabel: weekLabel(date, index),
      schedules: [],
      loading: true,
    }
  })
}

function getDepartmentTags(name: string) {
  const map: Record<string, string[]> = {
    内科: ['发热乏力', '慢病复诊', '常见内科疾病', '综合性不适'],
    呼吸内科: ['咳嗽咳痰', '气喘胸闷', '肺炎', '慢阻肺'],
    心血管内科: ['胸闷胸痛', '心悸', '高血压', '冠心病', '心律失常'],
    消化内科: ['胃痛腹胀', '反酸嗳气', '腹泻', '肝胆胰问题'],
    神经内科: ['头痛头晕', '失眠', '肢体麻木', '脑血管问题'],
    肾内科: ['水肿', '尿检异常', '肾炎', '慢性肾病'],
    内分泌科: ['糖尿病', '甲状腺疾病', '代谢异常', '肥胖管理'],
    外科: ['外伤', '体表包块', '腹部外科', '手术评估'],
    骨科: ['关节疼痛', '骨折损伤', '颈肩腰腿痛', '运动损伤'],
    妇产科: ['妇科疾病', '孕产咨询', '月经异常', '女性健康'],
    儿科: ['儿童发热', '儿童咳嗽', '腹泻', '生长发育'],
    新生儿科: ['新生儿黄疸', '喂养问题', '早产随访', '健康评估'],
    眼科: ['视力下降', '眼红眼痛', '干眼', '白内障'],
    耳鼻咽喉科: ['鼻炎', '咽喉不适', '耳鸣', '鼻窦问题'],
    口腔科: ['牙痛', '龋齿', '牙周问题', '口腔保健'],
    皮肤科: ['皮疹', '瘙痒', '痤疮', '湿疹过敏'],
    中医科: ['慢病调理', '体质调养', '康复辅助', '中医辨证'],
    肿瘤科: ['肿瘤筛查', '治疗评估', '复查随访', '症状管理'],
    急诊科: ['突发不适', '急性疼痛', '外伤', '快速评估'],
    康复医学科: ['术后康复', '卒中康复', '骨伤康复', '功能恢复'],
  }
  return map[name] || ['科室咨询', '初诊评估', '复诊管理']
}

function levelTone(levelName?: string): 'primary' | 'success' | 'warning' | 'danger' | 'ai' | 'neutral' {
  if (levelName?.includes('主任')) return 'warning'
  if (levelName?.includes('专家')) return 'ai'
  if (levelName?.includes('普通')) return 'primary'
  return 'neutral'
}

function selectDate(date: string) {
  selectedDate.value = date
}

function goBack() {
  router.push('/patient/overview')
}

function goTriage() {
  router.push('/patient/registration')
}

async function goRegister(schedule?: SchedulingOption) {
  try {
    await ElMessageBox.confirm(
      '直接挂号将跳过 AI 导诊。如果您已经确定要挂这个科室，可以继续；如果不确定，建议先通过 AI 导诊确认科室。',
      '是否直接挂号？',
      {
        confirmButtonText: '继续直接挂号',
        cancelButtonText: '先去 AI 导诊',
        type: 'warning',
        distinguishCancelAndClose: true,
      },
    )

    router.push({
      path: '/patient/registration',
      query: {
        departmentId: String(departmentId.value),
        ...(schedule?.id ? { scheduleId: String(schedule.id) } : {}),
        ...(schedule?.workDate ? { date: schedule.workDate } : { date: selectedDate.value }),
      },
    })
  } catch (action) {
    // action === 'cancel' -> 用户点了"先去 AI 导诊"按钮，跳转
    // action === 'close'  -> 用户点了 X / 关闭按钮，停在原界面，不做任何跳转
    if (action === 'cancel') {
      goTriage()
    }
  }
}

async function loadDepartmentDetail() {
  if (!departmentId.value) {
    errorMessage.value = '科室参数无效'
    return
  }

  loading.value = true
  errorMessage.value = ''
  daySummaries.value = buildNextSevenDays()
  selectedDate.value = daySummaries.value[0]?.date || formatDate(new Date())

  try {
    const [departments, doctorList, levels] = await Promise.all([
      registrationApi.departments('临床科室'),
      registrationApi.getDoctorsByDepartment(departmentId.value),
      registrationApi.registLevels(),
    ])

    department.value = departments.find(item => item.id === departmentId.value) || null
    doctors.value = doctorList
    registLevels.value = levels

    if (!department.value) {
      errorMessage.value = '未找到该科室，可能已被停用或删除'
      return
    }

    const scheduleResults = await Promise.all(
      daySummaries.value.map(async (day) => {
        try {
          const schedules = await scheduleApi.schedulingOptions(departmentId.value, day.date)
          return schedules.filter(item => item.status === 1 && Number(item.availableQuota || 0) > 0)
        } catch (error) {
          console.warn(`加载${day.date}号源失败:`, error)
          return []
        }
      }),
    )

    daySummaries.value = daySummaries.value.map((day, index) => ({
      ...day,
      loading: false,
      schedules: scheduleResults[index] || [],
    }))
  } catch (error) {
    console.error('加载科室详情失败:', error)
    errorMessage.value = '加载科室详情失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadDepartmentDetail)
</script>

<template>
  <div class="department-detail-page">
    <button class="breadcrumb" @click="goBack">
      <span>患者首页</span>
      <span>/</span>
      <span>临床科室</span>
      <span v-if="department">/ {{ department.name }}</span>
    </button>

    <div v-if="loading" class="state-card">正在加载科室详情...</div>
    <div v-else-if="errorMessage" class="state-card error-state">
      <strong>{{ errorMessage }}</strong>
      <button class="btn-primary" @click="goBack">返回患者首页</button>
    </div>

    <template v-else-if="department">
      <GlassCard class="hero-card">
        <div class="hero-copy">
          <span class="hero-eyebrow">{{ department.type || '临床科室' }}</span>
          <h1>{{ department.name }}</h1>
          <p>{{ department.description || '暂无科室简介。' }}</p>
          <div class="hero-tags">
            <StatusTag tone="primary">{{ doctors.length }} 位医生</StatusTag>
            <StatusTag :tone="hasAvailableSchedule ? 'success' : 'warning'">{{ departmentStatusText }}</StatusTag>
            <StatusTag v-if="totalAvailableQuota" tone="ai">近7日余号 {{ totalAvailableQuota }}</StatusTag>
          </div>
          <div class="hero-actions">
            <button v-if="hasAvailableSchedule" class="btn-primary" @click="goRegister(selectedSchedules[0] || todaySchedules[0])">立即挂号</button>
            <button v-else class="btn-primary" @click="goTriage">AI 导诊确认科室</button>
            <button class="btn-secondary" @click="goTriage">不确定科室？先导诊</button>
          </div>
        </div>
        <div class="hero-panel">
          <span class="panel-label">今日号源</span>
          <strong>{{ todaySchedules.length ? `${todaySchedules.length} 个可约时段` : '暂无可约时段' }}</strong>
          <p>{{ todaySchedules.length ? `今日剩余 ${todayQuota} 个号，可直接选择医生和时段。` : '当前日期暂无已发布号源，可查看近7日或先进行 AI 导诊。' }}</p>
        </div>
      </GlassCard>

      <GlassCard class="date-card">
        <div class="date-card__header">
          <div>
            <strong>近 7 日号源</strong>
            <p>根据已发布排班展示真实可预约号源。</p>
          </div>
          <StatusTag :tone="hasAvailableSchedule ? 'success' : 'warning'">{{ hasAvailableSchedule ? '可预约' : '暂无号源' }}</StatusTag>
        </div>
        <div class="date-strip">
          <button
            v-for="day in daySummaries"
            :key="day.date"
            class="date-item"
            :class="{ 'is-active': selectedDate === day.date, 'is-empty': !day.schedules.length }"
            @click="selectDate(day.date)"
          >
            <span>{{ day.weekLabel }}</span>
            <strong>{{ day.label }}</strong>
            <em>{{ day.loading ? '加载中' : day.schedules.length ? `${day.schedules.length} 个时段` : '暂无' }}</em>
          </button>
        </div>
      </GlassCard>

      <div class="content-grid">
        <div class="left-column">
          <GlassCard class="info-card">
            <template #default>
              <div class="section-title">
                <strong>本科室适合看什么？</strong>
                <p>结合当前科室简介，为患者快速判断就诊方向。</p>
              </div>
              <div class="symptom-tags">
                <span v-for="tag in symptomTags" :key="tag">{{ tag }}</span>
              </div>
            </template>
          </GlassCard>

          <GlassCard class="info-card advice-card">
            <div class="section-title">
              <strong>就诊前建议</strong>
              <p>提前准备信息，可以帮助医生更快了解病情。</p>
            </div>
            <div class="advice-list">
              <div class="advice-item">
                <span>01</span>
                <p>整理症状出现时间、持续情况、加重或缓解因素。</p>
              </div>
              <div class="advice-item">
                <span>02</span>
                <p>如有既往病历、检查报告、长期用药清单，建议就诊时携带。</p>
              </div>
              <div class="advice-item">
                <span>03</span>
                <p>如果不确定是否适合本科室，可以先通过 AI 导诊描述症状。</p>
              </div>
            </div>
          </GlassCard>
        </div>

        <GlassCard class="schedule-card">
          <div class="section-title schedule-title">
            <div>
              <strong>{{ selectedDay?.weekLabel }}可挂号</strong>
              <p>{{ selectedDate }}</p>
            </div>
            <StatusTag :tone="selectedSchedules.length ? 'success' : 'warning'">{{ selectedSchedules.length ? `${selectedSchedules.length} 个时段` : '暂无排班' }}</StatusTag>
          </div>

          <div v-if="selectedSchedules.length" class="slot-list">
            <div v-for="[slot, schedules] in sortedSlotEntries" :key="slot" class="slot-group">
              <div class="slot-heading">{{ slot }}</div>
              <button v-for="schedule in schedules" :key="schedule.id" class="schedule-option" @click="goRegister(schedule)">
                <div>
                  <strong>{{ schedule.physicianName }}</strong>
                  <span>{{ schedule.registLevelName || '挂号' }}</span>
                </div>
                <div class="schedule-side">
                  <span>余 {{ schedule.availableQuota }}</span>
                  <strong>¥{{ Number(schedule.price || 0).toFixed(2) }}</strong>
                </div>
              </button>
            </div>
          </div>

          <div v-else class="empty-schedule">
            <strong>该日期暂无可挂号排班</strong>
            <p>本科室已有医生信息，但当前选择日期暂无已发布号源。</p>
            <button class="btn-secondary" @click="goTriage">AI 导诊确认科室</button>
          </div>
        </GlassCard>
      </div>

      <GlassCard class="doctor-section">
        <div class="section-title doctor-title">
          <div>
            <strong>医生团队</strong>
            <p>有当前日期排班的医生会优先展示。</p>
          </div>
          <StatusTag tone="primary">{{ doctors.length }} 位医生</StatusTag>
        </div>

        <div v-if="doctorCards.length" class="doctor-grid">
          <article v-for="doctor in doctorCards" :key="doctor.id" class="doctor-card" :class="{ 'has-schedule': doctor.hasSchedule }">
            <div class="doctor-avatar">{{ doctor.realname.slice(-2) }}</div>
            <div class="doctor-main">
              <div class="doctor-name-row">
                <strong>{{ doctor.realname }}</strong>
                <StatusTag :tone="levelTone(doctor.registName)">{{ doctor.registName || '医生' }}</StatusTag>
              </div>
              <p>{{ doctor.hasSchedule ? `当前日期可约 ${doctor.schedules.length} 个时段，剩余 ${doctor.availableQuota} 个号。` : '当前日期暂无可预约排班。' }}</p>
              <div class="doctor-meta">
                <span>基础号别：{{ doctor.registName || '-' }}</span>
                <span>参考费用：¥{{ Number(doctor.displayPrice || 0).toFixed(2) }}</span>
              </div>
            </div>
            <button :class="doctor.hasSchedule ? 'btn-primary small' : 'btn-disabled small'" :disabled="!doctor.hasSchedule" @click="goRegister(doctor.schedules[0])">
              {{ doctor.hasSchedule ? '预约挂号' : '等待排班' }}
            </button>
          </article>
        </div>
        <div v-else class="state-card">暂无医生信息。</div>
      </GlassCard>
    </template>
  </div>
</template>

<style scoped>
.department-detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.breadcrumb {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 13px;
  cursor: pointer;
}

.breadcrumb:hover {
  color: var(--color-primary);
}

.state-card {
  display: grid;
  justify-items: center;
  gap: var(--space-4);
  padding: var(--space-6);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  color: var(--color-text-muted);
}

.error-state strong {
  color: var(--color-danger);
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-6);
  padding: var(--space-6);
  overflow: hidden;
  background:
    radial-gradient(circle at 88% 18%, rgba(31, 140, 255, 0.2), transparent 32%),
    linear-gradient(135deg, #f0f8ff 0%, #ffffff 48%, #eefaf7 100%);
}

.hero-copy {
  display: grid;
  gap: var(--space-4);
}

.hero-eyebrow {
  width: fit-content;
  padding: var(--space-1) var(--space-3);
  border-radius: 999px;
  background: rgba(31, 140, 255, 0.12);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 800;
}

.hero-copy h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 38px;
  letter-spacing: -0.04em;
}

.hero-copy p,
.section-title p,
.empty-schedule p,
.doctor-card p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.hero-tags,
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}

.hero-panel {
  align-self: stretch;
  display: grid;
  align-content: center;
  gap: var(--space-3);
  padding: var(--space-5);
  border: 1px solid rgba(31, 140, 255, 0.18);
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 24px 60px rgba(31, 140, 255, 0.12);
}

.panel-label {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.hero-panel strong {
  color: var(--color-primary);
  font-size: 28px;
}

.btn-primary,
.btn-secondary,
.btn-disabled {
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-5);
  font-weight: 800;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.btn-primary {
  border: 0;
  background: var(--color-primary);
  color: #fff;
}

.btn-secondary {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text);
}

.btn-disabled {
  border: 1px solid var(--color-border);
  background: rgba(95, 114, 136, 0.08);
  color: var(--color-text-muted);
  cursor: not-allowed;
}

.btn-primary:hover,
.btn-secondary:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.small {
  padding: var(--space-2) var(--space-4);
  white-space: nowrap;
}

.date-card,
.info-card,
.schedule-card,
.doctor-section {
  padding: var(--space-5);
}

.date-card__header,
.schedule-title,
.doctor-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.section-title {
  display: grid;
  gap: var(--space-2);
}

.section-title strong {
  color: var(--color-text);
  font-size: 18px;
}

.date-strip {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: var(--space-3);
}

.date-item {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
}

.date-item.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.date-item.is-empty:not(.is-active) {
  opacity: 0.68;
}

.date-item span,
.date-item em {
  color: var(--color-text-muted);
  font-size: 12px;
  font-style: normal;
}

.date-item strong {
  font-size: 17px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: var(--space-5);
  align-items: start;
}

.left-column {
  display: grid;
  gap: var(--space-5);
}

.symptom-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.symptom-tags span {
  padding: var(--space-2) var(--space-4);
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 800;
}

.advice-list {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.advice-item {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  background: rgba(95, 114, 136, 0.06);
}

.advice-item span {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 900;
}

.advice-item p {
  margin: 0;
  color: var(--color-text);
  line-height: 1.7;
}

.schedule-card {
  position: sticky;
  top: var(--space-5);
}

.slot-list {
  display: grid;
  gap: var(--space-4);
}

.slot-group {
  display: grid;
  gap: var(--space-3);
}

.slot-heading {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 900;
}

.schedule-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: #fff;
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
}

.schedule-option:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.schedule-option div:first-child,
.schedule-side {
  display: grid;
  gap: 2px;
}

.schedule-option span,
.schedule-side span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.schedule-side {
  justify-items: end;
}

.schedule-side strong {
  color: var(--color-primary);
  font-size: 17px;
}

.empty-schedule {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(95, 114, 136, 0.06);
}

.doctor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.doctor-card {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) auto;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: #fff;
}

.doctor-card.has-schedule {
  border-color: rgba(31, 140, 255, 0.34);
  box-shadow: 0 14px 32px rgba(31, 140, 255, 0.08);
}

.doctor-avatar {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--color-primary), #20b486);
  color: #fff;
  font-weight: 900;
}

.doctor-main {
  display: grid;
  gap: var(--space-2);
}

.doctor-name-row,
.doctor-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.doctor-name-row strong {
  font-size: 17px;
}

.doctor-meta span {
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .department-detail-page {
    width: 100%;
    margin: 0;
  }

  .hero-card,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .schedule-card {
    position: static;
  }
}

@media (max-width: 760px) {
  .date-strip,
  .doctor-grid {
    grid-template-columns: 1fr;
  }

  .doctor-card {
    grid-template-columns: 48px minmax(0, 1fr);
  }

  .doctor-card button {
    grid-column: 1 / -1;
  }

  .hero-copy h1 {
    font-size: 30px;
  }
}
</style>

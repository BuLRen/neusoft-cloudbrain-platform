<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'
import GlassCard from '@/shared/components/GlassCard.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import { Warning } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

// 功能入口
const featureEntries = [
  { key: 'registration', icon: '📝', title: '预约挂号', desc: 'AI导诊、预问诊、挂号', path: '/patient/registration' },
  { key: 'records', icon: '📄', title: '就诊记录', desc: '查看病历和处方', path: '/patient/records' },
  { key: 'prescription', icon: '💊', title: '我的处方', desc: '处方和用药管理', path: '/patient/prescription' },
  { key: 'followup', icon: '📅', title: '随访管理', desc: '用药反馈、康复评估', path: '/patient/followup' },
  { key: 'profile', icon: '👤', title: '个人中心', desc: '信息管理', path: '/patient/profile' },
]

// 今日待办数据
const todayTasks = ref<{
  pendingCharges: number
  pendingMedicines: number
  upcomingAppointments: number
}>({
  pendingCharges: 0,
  pendingMedicines: 0,
  upcomingAppointments: 0,
})

const loading = ref(false)

// 当前患者信息
const currentPatient = computed(() => authStore.currentPatient)

// 加载今日待办数据
async function loadTodayTasks() {
  if (!authStore.currentPatientId) return

  loading.value = true
  try {
    // 获取患者的挂号列表
    const registrations = await registrationApi.registrationsByPatient(authStore.currentPatientId)
    const today = new Date().toDateString()
    // 统计今日待就诊挂号数
    const pendingRegisters = registrations.filter((r: any) => {
      const regDate = new Date(r.visitDate).toDateString()
      return r.visitState === 1 && regDate === today
    })
    todayTasks.value.upcomingAppointments = pendingRegisters.length
  } catch (e) {
    console.warn('加载今日待办失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTodayTasks()
})

function navigateTo(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="patient-overview">
    <!-- 快捷功能入口 -->
    <GlassCard class="entry-card">
      <template #header>
        <div class="card-header">
          <span class="card-icon">🚀</span>
          <span>快捷功能</span>
        </div>
      </template>
      <div class="entry-grid">
        <button
          v-for="entry in featureEntries"
          :key="entry.key"
          class="entry-item"
          @click="navigateTo(entry.path)"
        >
          <span class="entry-icon">{{ entry.icon }}</span>
          <span class="entry-title">{{ entry.title }}</span>
          <span class="entry-desc">{{ entry.desc }}</span>
        </button>
      </div>
    </GlassCard>

    <!-- 今日待办卡片 -->
    <GlassCard class="todo-card">
      <template #header>
        <div class="card-header">
          <span class="card-icon">📋</span>
          <span>今日待办</span>
        </div>
      </template>
      <div class="todo-content">
        <div class="todo-item">
          <div class="todo-icon-wrap">
            <span class="todo-icon">📅</span>
          </div>
          <div class="todo-info">
            <span class="todo-count">{{ todayTasks.upcomingAppointments }}</span>
            <span class="todo-label">待就诊</span>
          </div>
        </div>
        <div class="todo-item">
          <div class="todo-icon-wrap">
            <span class="todo-icon">💰</span>
          </div>
          <div class="todo-info">
            <span class="todo-count">{{ todayTasks.pendingCharges }}</span>
            <span class="todo-label">待缴费</span>
          </div>
        </div>
        <div class="todo-item">
          <div class="todo-icon-wrap">
            <span class="todo-icon">💊</span>
          </div>
          <div class="todo-info">
            <span class="todo-count">{{ todayTasks.pendingMedicines }}</span>
            <span class="todo-label">待取药</span>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- 提示 -->
    <GlassCard class="tips-card">
      <div class="tips-content">
        <span class="tips-icon">💡</span>
        <div class="tips-text">
          <strong>提示</strong>
          <p>点击"预约挂号"开始就诊流程，AI将引导您完成预问诊、导诊和挂号步骤。</p>
          <p v-if="currentPatient?.allergyHistory" class="allergy-warning">
            <el-icon><Warning /></el-icon>
            当前患者过敏史：{{ currentPatient.allergyHistory }}
          </p>
        </div>
      </div>
    </GlassCard>
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

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.card-icon {
  font-size: 18px;
}

/* 功能入口 */
.entry-card {
  padding: var(--space-5);
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-4);
}

.entry-item {
  display: grid;
  place-items: center;
  gap: var(--space-2);
  padding: var(--space-5) var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.entry-item:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  transform: translateY(-2px);
}

.entry-icon {
  font-size: 28px;
}

.entry-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.entry-desc {
  font-size: 11px;
  color: var(--color-text-muted);
  text-align: center;
}

/* 今日待办卡片 */
.todo-card {
  padding: var(--space-5);
}

.todo-content {
  display: flex;
  gap: var(--space-6);
  justify-content: center;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
  min-width: 160px;
}

.todo-item:hover {
  background: var(--color-primary-soft);
  transform: translateY(-2px);
}

.todo-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.todo-icon {
  font-size: 22px;
}

.todo-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.todo-count {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1;
}

.todo-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.todo-footer {
  margin-top: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--color-border);
  text-align: center;
}

.todo-hint {
  font-size: 12px;
  color: var(--color-text-soft);
}

/* 提示卡片 */
.tips-card {
  padding: var(--space-5);
}

.tips-content {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.tips-icon {
  font-size: 24px;
}

.tips-text {
  display: grid;
  gap: var(--space-2);
}

.tips-text strong {
  font-size: 14px;
}

.tips-text p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.allergy-warning {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-danger) !important;
  font-size: 12px !important;
}

/* 响应式 */
@media (max-width: 1024px) {
  .entry-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 640px) {
  .entry-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
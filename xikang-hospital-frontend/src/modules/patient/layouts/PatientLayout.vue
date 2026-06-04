<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'
import GlassCard from '@/shared/components/GlassCard.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { ArrowDown, Check } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const patientName = computed(() => {
  return authStore.realName || '患者'
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

// 当前选中的患者信息
const currentPatient = computed(() => authStore.currentPatient)

// 切换患者
function switchPatient(patientId: number) {
  authStore.switchPatient(patientId)
}

// 快捷功能菜单
const quickActions = [
  { key: 'prescription', label: '我的处方', icon: '💊', path: '/patient/prescription' },
  { key: 'registration', label: '我的挂号', icon: '📋', path: '/patient/registration' },
  { key: 'profile', label: '个人中心', icon: '👤', path: '/patient/profile' },
]

function navigateTo(path: string) {
  router.push(path)
}

const currentPageTitle = computed(() => {
  const titleMap: Record<string, string> = {
    overview: '就诊概览',
    triage: 'AI导诊',
    registration: '我的挂号',
    previsit: 'AI预问诊',
    records: '就诊记录',
    followup: '随访管理',
    profile: '个人中心',
    prescription: '我的处方',
  }
  const path = route.path.split('/').pop() || 'overview'
  return titleMap[path] || '患者端'
})
</script>

<template>
  <div class="patient-layout">
    <!-- 顶部用户信息栏 -->
    <GlassCard class="patient-header">
      <div class="patient-header__content">
        <div class="patient-header__left">
          <div class="patient-avatar">
            <span>{{ currentPatient?.realName?.[0] || patientName[0] }}</span>
          </div>
          <div class="patient-info">
            <div class="patient-info__top">
              <h2 class="patient-greeting">{{ greeting }}，{{ currentPatient?.realName || patientName }}</h2>
              <el-tag v-if="currentPatient?.relation" size="small" type="info">
                {{ currentPatient.relation }}
              </el-tag>
            </div>
            <div v-if="currentPatient?.allergyHistory" class="patient-allergy">
              <span class="allergy-label">过敏史：</span>
              <span class="allergy-value">{{ currentPatient.allergyHistory }}</span>
            </div>
          </div>
        </div>
        <div class="patient-header__right">
          <!-- 患者切换 - 放在显眼位置 -->
          <el-dropdown v-if="authStore.patients.length > 0" @command="switchPatient" trigger="click">
            <el-button type="primary" size="default" class="switch-patient-btn">
              <span class="switch-icon">👥</span>
              <span>{{ authStore.patients.length > 1 ? '切换患者' : '就诊人' }}</span>
              <span class="current-patient-name">({{ currentPatient?.realName }})</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="patient in authStore.patients"
                  :key="patient.patientId"
                  :command="patient.patientId"
                >
                  <div class="patient-item" :class="{ 'is-active': patient.patientId === authStore.currentPatientId }">
                    <div class="patient-item-info">
                      <span class="patient-item-name">{{ patient.realName }}</span>
                      <span class="patient-item-relation">{{ patient.relation }}</span>
                    </div>
                    <el-icon v-if="patient.patientId === authStore.currentPatientId" class="check-icon"><Check /></el-icon>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <div class="quick-actions">
            <button
              v-for="action in quickActions"
              :key="action.key"
              class="quick-action-btn"
              @click="navigateTo(action.path)"
            >
              <span class="quick-action-icon">{{ action.icon }}</span>
              <span class="quick-action-label">{{ action.label }}</span>
            </button>
          </div>
          <el-button class="logout-btn" text @click="authStore.logout(); router.push('/login')">
            退出
          </el-button>
        </div>
      </div>
    </GlassCard>

    <!-- 页面标题 -->
    <PageHeader :title="currentPageTitle" description="患者端工作台">
      <template #actions>
        <el-button text @click="router.push('/patient/overview')">
          返回首页
        </el-button>
      </template>
    </PageHeader>

    <!-- 内容区域 -->
    <div class="patient-content">
      <RouterView />
    </div>
  </div>
</template>

<style scoped>
.patient-layout {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-8);
  min-height: 100%;
  width: 100%;
}

/* 顶部用户信息栏 */
.patient-header {
  padding: var(--space-4) var(--space-5);
}

.patient-header__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.patient-header__left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.patient-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: 600;
}

.patient-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.patient-info__top {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.patient-greeting {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.patient-allergy {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: 12px;
}

.allergy-label {
  color: var(--color-text-muted);
}

.allergy-value {
  color: var(--color-danger);
}

.patient-id {
  font-size: 12px;
}

.patient-header__right {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

/* 切换患者按钮 */
.switch-patient-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.switch-icon {
  font-size: 16px;
}

.current-patient-name {
  color: var(--color-primary);
  font-weight: 600;
}

/* 下拉菜单中的患者项 */
.patient-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--space-3);
  padding: var(--space-1) 0;
}

.patient-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.patient-item-name {
  font-weight: 600;
}

.patient-item-relation {
  font-size: 12px;
  color: var(--color-text-muted);
}

.patient-item.is-active .patient-item-name {
  color: var(--color-primary);
}

.check-icon {
  color: var(--color-primary);
}

.patient-relation {
  color: var(--color-text-muted);
  font-size: 12px;
  margin-left: var(--space-1);
}

.check-icon {
  margin-left: var(--space-2);
  color: var(--color-primary);
}

.quick-actions {
  display: flex;
  gap: var(--space-2);
}

.quick-action-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.quick-action-btn:hover {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}

.quick-action-icon {
  font-size: 16px;
}

.quick-action-label {
  font-size: 13px;
  color: var(--color-text);
}

.logout-btn {
  color: var(--color-text-muted);
}

/* 内容区域 */
.patient-content {
  flex: 1;
  width: 100%;
}

/* 响应式 */
@media (max-width: 768px) {
  .patient-header__content {
    flex-direction: column;
    align-items: flex-start;
  }

  .quick-actions {
    flex-wrap: wrap;
  }
}
</style>
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'
import AdminDashboard from '@/modules/admin/pages/AdminDashboard.vue'
import PhysicianDashboard from '@/modules/physician/pages/PhysicianDashboard.vue'
import MedtechDashboard from '@/modules/medtech/pages/MedtechDashboard.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'

const router = useRouter()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.role === 'admin')
const isPhysician = computed(() => authStore.role === 'physician')
const isFollowup = computed(() => authStore.role === 'followup')
const modules = [
  { title: '诊疗流程', owner: '人员A', path: '/physician/queue', description: '医生接诊、病历、申请、确诊、开方。' },
  { title: '入口与支撑流程', owner: '人员B', path: '/registration', description: '导诊、挂号、收费、执行、发药、随访。' },
  { title: 'AI 组件区', owner: '共同', path: '/ai', description: 'AI 结果卡片和页面嵌入能力预留。' },
]
</script>

<template>
  <AdminDashboard v-if="isAdmin" />
  <PhysicianDashboard v-else-if="isPhysician" />
  <MedtechDashboard v-else-if="isMedtech" />
  <div v-else-if="isFollowup" class="dashboard u-page-grid">
    <PageHeader
      title="随访工作台"
      description="您已登录为随访人员，请进入随访系统管理在管患者。"
      eyebrow="随访系统"
    />
    <GlassCard class="dashboard__card" @click="router.push('/follow-up/outcome')">
      <StatusTag tone="primary">随访</StatusTag>
      <h2>进入随访系统</h2>
      <p>工作台、疗效评估、医患沟通与随访记录。</p>
    </GlassCard>
  </div>
  <div v-else class="dashboard u-page-grid">
    <PageHeader
      title="前端框架仪表盘"
      description="当前阶段只提供框架、菜单、权限、视觉系统和占位路由。具体业务页面由后续小组成员在对应模块中开发。"
      eyebrow="Xikang Cloud Hospital"
    />

    <section class="dashboard__hero">
      <GlassCard v-for="item in modules" :key="item.title" class="dashboard__card" @click="router.push(item.path)">
        <StatusTag :tone="item.owner === '共同' ? 'ai' : 'primary'">{{ item.owner }}</StatusTag>
        <h2>{{ item.title }}</h2>
        <p>{{ item.description }}</p>
      </GlassCard>
    </section>

    <GlassCard class="dashboard__note">
      <h2>开发约定</h2>
      <p>页面开发时保持“一个页面一个组件”，共享能力放在 `shared`，路由、权限、布局和全局样式放在 `app`。业务页面不需要重复设计圆角、卡片、标题和状态样式。</p>
    </GlassCard>
  </div>
</template>

<style scoped>
.dashboard__hero {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.dashboard__card {
  min-height: 168px;
  padding: var(--space-5);
  cursor: pointer;
  transition: transform var(--duration-base) var(--ease-standard), box-shadow var(--duration-base) var(--ease-standard);
}

.dashboard__card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.dashboard__card h2 {
  margin-block-start: var(--space-4);
  font-size: 22px;
  letter-spacing: -0.04em;
}

.dashboard__card p,
.dashboard__note p {
  margin-block-start: var(--space-3);
  color: var(--color-text-muted);
  line-height: 1.8;
}

.dashboard__note {
  padding: var(--space-5);
}
</style>

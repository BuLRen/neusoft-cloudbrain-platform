<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElTabPane, ElTabs } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import OperationsMonitoring from '@/modules/admin/pages/OperationsMonitoring.vue'
import StatisticsReports from '@/modules/admin/pages/StatisticsReports.vue'

const route = useRoute()
const router = useRouter()

type OperationsTab = 'monitoring' | 'reports'

const activeTab = computed<OperationsTab>({
  get() {
    return route.query.tab === 'reports' ? 'reports' : 'monitoring'
  },
  set(tab) {
    void router.replace({ path: route.path, query: { tab } })
  },
})
</script>

<template>
  <div class="operations-center u-page-grid">
    <PageHeader
      title="运营中心"
      description="跨模块异常监控与经营统计报表，支撑管理员日常治理决策。"
      eyebrow="管理员"
    />

    <GlassCard class="operations-center__card admin-shell-card admin-panel-surface">
      <ElTabs v-model="activeTab">
        <ElTabPane label="运营监控" name="monitoring">
          <OperationsMonitoring embedded />
        </ElTabPane>
        <ElTabPane label="统计报表" name="reports">
          <StatisticsReports embedded />
        </ElTabPane>
      </ElTabs>
    </GlassCard>
  </div>
</template>

<style scoped>
.operations-center {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.operations-center__card {
  padding: var(--space-5);
}
</style>

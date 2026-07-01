<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElTabPane, ElTabs } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import PhysicianManagement from '@/modules/admin/pages/PhysicianManagement.vue'
import AdminMedtechEmployeePage from '@/modules/admin/pages/AdminMedtechEmployeePage.vue'
import AdminFollowUpEmployeePage from '@/modules/admin/pages/AdminFollowUpEmployeePage.vue'

const route = useRoute()
const router = useRouter()

type PersonnelTab = 'physicians' | 'medtech' | 'followup'

const activeTab = computed<PersonnelTab>({
  get() {
    if (route.query.tab === 'medtech') return 'medtech'
    if (route.query.tab === 'followup') return 'followup'
    return 'physicians'
  },
  set(tab) {
    void router.replace({ path: route.path, query: { tab } })
  },
})
</script>

<template>
  <div class="personnel-page u-page-grid">
    <PageHeader
      title="人员管理"
      description="统一维护诊疗医生、医技人员与随访人员档案，并管理其登录账号。"
      eyebrow="管理员"
    />

    <GlassCard class="personnel-page__card admin-shell-card admin-panel-surface">
      <ElTabs v-model="activeTab">
        <ElTabPane label="诊疗医生" name="physicians">
          <PhysicianManagement embedded />
        </ElTabPane>
        <ElTabPane label="医技人员" name="medtech">
          <AdminMedtechEmployeePage embedded />
        </ElTabPane>
        <ElTabPane label="随访人员" name="followup">
          <AdminFollowUpEmployeePage embedded />
        </ElTabPane>
      </ElTabs>
    </GlassCard>
  </div>
</template>

<style scoped>
.personnel-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.personnel-page__card {
  padding: var(--space-5);
}
</style>

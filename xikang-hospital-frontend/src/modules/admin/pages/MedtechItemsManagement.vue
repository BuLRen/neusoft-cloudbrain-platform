<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElTabPane, ElTabs } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import AdminCheckEquipmentPage from '@/modules/admin/pages/AdminCheckEquipmentPage.vue'
import AdminResultFormPage from '@/modules/admin/pages/AdminResultFormPage.vue'

const route = useRoute()
const router = useRouter()

type MedtechItemsTab = 'catalog' | 'result-form'

const activeTab = computed<MedtechItemsTab>({
  get() {
    return route.query.tab === 'result-form' ? 'result-form' : 'catalog'
  },
  set(tab) {
    const query: Record<string, string> = { tab }
    if (tab === 'result-form' && route.query.techId) {
      query.techId = String(route.query.techId)
    }
    void router.replace({ path: route.path, query })
  },
})
</script>

<template>
  <div class="medtech-items-page u-page-grid">
    <PageHeader
      title="医技项目"
      description="维护检查/检验/处置项目目录，并配置检查结果表单字段模板。"
      eyebrow="管理员"
    />

    <GlassCard class="medtech-items-page__card admin-shell-card admin-panel-surface">
      <ElTabs v-model="activeTab">
        <ElTabPane label="项目目录" name="catalog">
          <AdminCheckEquipmentPage embedded />
        </ElTabPane>
        <ElTabPane label="结果表单" name="result-form">
          <AdminResultFormPage embedded />
        </ElTabPane>
      </ElTabs>
    </GlassCard>
  </div>
</template>

<style scoped>
.medtech-items-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.medtech-items-page__card {
  padding: var(--space-5);
}
</style>

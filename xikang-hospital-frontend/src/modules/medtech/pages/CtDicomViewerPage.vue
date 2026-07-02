<script setup lang="ts">
import { ref } from 'vue'
import { ElTag } from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import CtViewerPanel from '@/modules/medtech/ct-viewer/components/CtViewerPanel.vue'

const panelRef = ref<InstanceType<typeof CtViewerPanel>>()
</script>

<template>
  <div class="ct-viewer-page">
    <PageHeader
      title="CT 影像演示工具"
      description="用于体验 DICOM/NRRD 上传、三视图阅片与 3D 体渲染。本页面不关联检查申请单；正式业务请从「医技申请 → 执行检查」进入全屏 CT 执行页。"
      eyebrow="医技管理 · 演示工具"
    >
      <template #actions>
        <ElTag type="info" effect="plain">演示 / 沙箱</ElTag>
      </template>
    </PageHeader>

    <CtViewerPanel ref="panelRef" />
  </div>
</template>

<style scoped>
.ct-viewer-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
</style>

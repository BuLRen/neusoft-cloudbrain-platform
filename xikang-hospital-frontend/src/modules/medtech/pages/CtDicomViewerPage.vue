<script setup lang="ts">
import { ref } from 'vue'
import CtViewerPanel from '@/modules/medtech/ct-viewer/components/CtViewerPanel.vue'
import '@/modules/medtech/ct-viewer/styles/ct-viewer-theme.css'

const panelRef = ref<InstanceType<typeof CtViewerPanel>>()
</script>

<template>
  <div class="ct-viewer-page ct-imaging-theme">
    <header class="ct-viewer-page__header">
      <div>
        <p class="ct-viewer-page__eyebrow">医技管理 · 演示工具</p>
        <h1 class="ct-viewer-page__title">CT 影像演示工具</h1>
        <p class="ct-viewer-page__desc">
          体验 DICOM/NRRD 上传、三视图阅片与 3D 体渲染。正式业务请从「医技申请 → 执行检查」进入全屏 CT 执行页。
        </p>
      </div>
      <span class="ct-viewer-page__badge">演示 / 沙箱</span>
    </header>

    <CtViewerPanel ref="panelRef" />
  </div>
</template>

<style scoped>
.ct-viewer-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: calc(100vh - var(--header-height) - var(--shell-gap) * 2);
  padding: var(--space-4);
  background: var(--ct-bg);
  color: var(--ct-text);
  border-radius: var(--ct-radius-lg);
}

.ct-viewer-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface);
}

.ct-viewer-page__eyebrow {
  margin: 0 0 4px;
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ct-text-dim);
}

.ct-viewer-page__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.ct-viewer-page__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ct-text-muted);
  max-width: 640px;
}

.ct-viewer-page__badge {
  flex-shrink: 0;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--ct-accent);
  background: var(--ct-accent-soft);
  border: 1px solid rgba(42, 157, 143, 0.3);
}
</style>


<script setup lang="ts">
import GlassCard from './GlassCard.vue'
import EmptyState from './EmptyState.vue'
</script>

<template>
  <div class="u-page-grid">
    <GlassCard>
      <EmptyState
        title="页面组件待替换"
        description="请在对应 modules 目录创建单页组件，并在路由配置中替换当前占位组件。"
      />
    </GlassCard>
  </div>
</template>

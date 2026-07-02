<script setup lang="ts">
</script>

<template>
  <div class="ct-exam-layout">
    <RouterView />
  </div>
</template>

<style scoped>
.ct-exam-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg);
}
</style>

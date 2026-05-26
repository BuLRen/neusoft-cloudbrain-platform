
<script setup lang="ts">
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
</script>

<template>
  <div class="app-shell">
    <AppSidebar />
    <main class="app-shell__main">
      <AppHeader />
      <section class="app-shell__content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  gap: var(--shell-gap);
  min-height: 100vh;
  padding: var(--shell-gap);
}

.app-shell__main {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: var(--shell-gap);
  min-width: 0;
}

.app-shell__content {
  min-width: 0;
}
</style>

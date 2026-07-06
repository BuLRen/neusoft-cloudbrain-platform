
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
import PhysicianPatientSelectDialog from '@/modules/physician/components/PhysicianPatientSelectDialog.vue'
import CriticalValueAlertDialog from '@/shared/components/CriticalValueAlertDialog.vue'
import { useCriticalValueStream } from '@/shared/composables/useCriticalValueStream'

const route = useRoute()
const authStore = useAuthStore()
const isFullscreen = computed(() => Boolean(route.meta.fullscreen))
const criticalValueEnabled = computed(
  () => authStore.role === 'physician' && Boolean(authStore.employeeId),
)

const { activeAlert, dismissCurrent, stopAlarm } = useCriticalValueStream(() => criticalValueEnabled.value)

function handleCriticalResolved() {
  stopAlarm()
  dismissCurrent()
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--fullscreen': isFullscreen }">
    <template v-if="isFullscreen">
      <RouterView />
    </template>
    <template v-else>
      <AppSidebar />
      <main class="app-shell__main">
        <AppHeader />
        <section class="app-shell__content">
          <RouterView />
        </section>
      </main>
    </template>
    <PhysicianPatientSelectDialog />
    <CriticalValueAlertDialog :alert="activeAlert" @resolved="handleCriticalResolved" />
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

.app-shell--fullscreen {
  display: block;
  min-height: 100dvh;
  padding: 0;
}

.app-shell__main {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: var(--shell-gap);
  min-width: 0;
  padding-inline: var(--space-4);
}

.app-shell__content {
  min-width: 0;
  padding-block-end: var(--space-4);
}
</style>

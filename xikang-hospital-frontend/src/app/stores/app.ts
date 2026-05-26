
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

type ThemeMode = 'light' | 'dark'
type DensityMode = 'comfortable' | 'compact'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const theme = ref<ThemeMode>('light')
  const density = ref<DensityMode>('comfortable')
  const themeLabel = computed(() => (theme.value === 'light' ? '浅色' : '深色'))

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    document.documentElement.dataset.theme = theme.value
  }

  function setDensity(nextDensity: DensityMode) {
    density.value = nextDensity
    document.documentElement.dataset.density = nextDensity
  }

  return { sidebarCollapsed, theme, density, themeLabel, toggleSidebar, toggleTheme, setDensity }
})

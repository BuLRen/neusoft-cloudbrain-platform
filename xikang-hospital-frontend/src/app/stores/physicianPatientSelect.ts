import { ref } from 'vue'
import { defineStore } from 'pinia'

export const usePhysicianPatientSelectStore = defineStore('physicianPatientSelect', () => {
  const visible = ref(false)
  const targetPath = ref('')

  function open(path: string) {
    targetPath.value = path
    visible.value = true
  }

  function close() {
    visible.value = false
    targetPath.value = ''
  }

  return { visible, targetPath, open, close }
})

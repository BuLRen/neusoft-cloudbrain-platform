<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import GlucoseRevisitAdviceBlock from '@/modules/medtech/follow-up/components/GlucoseRevisitAdviceBlock.vue'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { GlucoseAdvice } from '@/shared/types/medtechFollowUp'

const props = withDefaults(
  defineProps<{
    registerId?: number
    patientId?: number
    showGlucoseAdvice?: boolean
  }>(),
  {
    showGlucoseAdvice: false,
  },
)

const emit = defineEmits<{
  goRegistration: []
}>()

const loading = ref(false)
const advice = ref<GlucoseAdvice | null>(null)

async function loadAdvice() {
  if (!props.showGlucoseAdvice || (!props.registerId && !props.patientId)) {
    advice.value = null
    return
  }
  loading.value = true
  try {
    advice.value = await medtechFollowUpApi.getPatientGlucoseAdvice({
      patientId: props.patientId,
      registerId: props.registerId,
    })
  } catch {
    advice.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.registerId, props.patientId, props.showGlucoseAdvice],
  () => {
    void loadAdvice()
  },
)

onMounted(() => {
  void loadAdvice()
})

defineExpose({ reload: loadAdvice })
</script>

<template>
  <GlucoseRevisitAdviceBlock
    v-if="showGlucoseAdvice"
    :advice="advice"
    :loading="loading"
    compact
    mode="patient"
    show-registration-link
    @go-registration="emit('goRegistration')"
  />
</template>

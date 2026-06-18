import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEncounterStore } from '@/app/stores/encounter'
import { physicianRoute } from '../constants/visitState'

const PHYSICIAN_STEP_PATHS = [
  '/physician/record',
  '/physician/orders',
  '/physician/results',
  '/physician/diagnosis',
  '/physician/prescription',
]

export function isPhysicianStepPath(path: string) {
  return PHYSICIAN_STEP_PATHS.includes(path)
}

export function usePhysicianEncounterRoute() {
  const route = useRoute()
  const router = useRouter()
  const encounterStore = useEncounterStore()

  async function resolveEncounterFromRoute() {
    if (!isPhysicianStepPath(route.path)) return

    const raw = route.query.registerId
    const queryId = typeof raw === 'string' ? Number(raw) : Array.isArray(raw) ? Number(raw[0]) : NaN
    const validQueryId = Number.isFinite(queryId) && queryId > 0 ? queryId : null

    if (validQueryId) {
      if (encounterStore.registerId !== validQueryId) {
        await encounterStore.switchEncounter(validQueryId)
      }
      return
    }

    if (encounterStore.registerId) {
      await router.replace({
        path: route.path,
        query: { ...route.query, registerId: String(encounterStore.registerId) },
      })
      return
    }

    await router.replace('/physician/queue')
  }

  function navigateWithRegisterId(path: string) {
    const id = encounterStore.registerId
    if (!id) {
      void router.push('/physician/queue')
      return
    }
    void router.push(physicianRoute(path, id))
  }

  watch(
    () => route.query.registerId,
    () => {
      void resolveEncounterFromRoute()
    },
  )

  onMounted(() => {
    void resolveEncounterFromRoute()
  })

  return { navigateWithRegisterId, resolveEncounterFromRoute }
}

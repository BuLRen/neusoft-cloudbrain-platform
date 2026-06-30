import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEncounterStore } from '@/app/stores/encounter'
import { isPhysicianStepPath, physicianRoute } from '../constants/visitState'

export { isPhysicianStepPath }

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

    await router.replace({
      path: route.path,
      query: { ...route.query, selectFor: route.path },
    })
  }

  function navigateWithRegisterId(path: string) {
    const id = encounterStore.registerId
    if (!id) {
      void router.push({ path, query: { selectFor: path } })
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

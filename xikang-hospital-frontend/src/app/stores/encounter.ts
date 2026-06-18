import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { physicianApi, type PhysicianPatient } from '@/shared/api/modules/physician'

export interface EncounterPatientSummary {
  realName: string
  caseNumber: string
  gender?: string
  age?: number
}

export interface EncounterAiConsultSummary {
  aiSummary?: string
  chiefComplaint?: string
}

export const useEncounterStore = defineStore('encounter', () => {
  const registerId = ref<number | null>(null)
  const patientSummary = ref<EncounterPatientSummary | null>(null)
  const aiConsultSummary = ref<EncounterAiConsultSummary | null>(null)
  const hasEncounter = computed(() => Boolean(registerId.value))

  function setEncounter(payload: {
    registerId: number
    patientSummary: EncounterPatientSummary
    aiConsultSummary?: EncounterAiConsultSummary | null
  }) {
    registerId.value = payload.registerId
    patientSummary.value = payload.patientSummary
    aiConsultSummary.value = payload.aiConsultSummary || null
  }

  function clearEncounter() {
    registerId.value = null
    patientSummary.value = null
    aiConsultSummary.value = null
  }

  function applyPatient(patient: PhysicianPatient) {
    setEncounter({
      registerId: patient.registerId,
      patientSummary: {
        realName: patient.realName,
        caseNumber: patient.caseNumber,
        gender: patient.gender,
        age: patient.age,
      },
      aiConsultSummary: patient.aiConsultSummary
        ? {
            aiSummary: patient.aiConsultSummary.aiSummary,
            chiefComplaint: patient.aiConsultSummary.chiefComplaint,
          }
        : null,
    })
  }

  async function switchEncounter(nextRegisterId: number) {
    const patient = await physicianApi.patient(nextRegisterId)
    applyPatient(patient)
  }

  return {
    registerId,
    patientSummary,
    aiConsultSummary,
    hasEncounter,
    setEncounter,
    applyPatient,
    switchEncounter,
    clearEncounter,
  }
})


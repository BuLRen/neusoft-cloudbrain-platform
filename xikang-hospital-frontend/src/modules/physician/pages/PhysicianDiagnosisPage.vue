<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElCard, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import { physicianApi, type Disease, type W4Output } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const diseases = ref<Disease[]>([])
const w4Output = ref<W4Output | null>(null)

const diagnosisForm = reactive({
  diagnosis: '',
  cure: '',
  careful: '',
  diseaseIds: [] as number[],
})

async function loadDiseases() {
  diseases.value = await physicianApi.diseases()
}

async function runW4() {
  if (!registerId.value) return
  loading.value = true
  try {
    w4Output.value = await physicianApi.aiW4(registerId.value)
    if (w4Output.value?.primaryDiagnosis?.diseaseName) {
      diagnosisForm.diagnosis = w4Output.value.primaryDiagnosis.diseaseName || ''
    }
    ElMessage.success('W4 诊断建议已生成')
  } finally {
    loading.value = false
  }
}

async function submitDiagnosis() {
  if (!registerId.value) return
  const payload = {
    registerId: registerId.value,
    diagnosis: diagnosisForm.diagnosis,
    cure: diagnosisForm.cure,
    careful: diagnosisForm.careful,
    diseaseIds: diagnosisForm.diseaseIds,
  }
  await physicianApi.submitDiagnosis(payload)
  ElMessage.success('确诊已保存')
}

onMounted(() => {
  void loadDiseases()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    :step="5"
    :total-steps="6"
    title="门诊确诊"
    description="第五步：录入确诊信息，可运行 W4 获取 AI 辅助诊断建议。"
    prev-path="/physician/results"
    next-path="/physician/prescription"
  >
    <div class="diagnosis-toolbar">
      <ElButton :loading="loading" @click="runW4">运行 W4（AI 诊断建议）</ElButton>
      <ElButton type="primary" :loading="loading" @click="submitDiagnosis">保存确诊</ElButton>
    </div>

    <ElForm label-position="top" class="diagnosis-form">
      <ElFormItem label="确诊病名">
        <ElInput v-model="diagnosisForm.diagnosis" placeholder="例如：急性上呼吸道感染" />
      </ElFormItem>
      <ElFormItem label="治疗方向">
        <ElInput v-model="diagnosisForm.cure" type="textarea" :rows="2" />
      </ElFormItem>
      <ElFormItem label="注意事项">
        <ElInput v-model="diagnosisForm.careful" type="textarea" :rows="2" />
      </ElFormItem>
      <ElFormItem label="疾病编码">
        <ElSelect v-model="diagnosisForm.diseaseIds" multiple filterable placeholder="选择疾病">
          <ElOption v-for="item in diseases" :key="item.id" :label="`${item.diseaseName} ${item.diseaseIcd || ''}`" :value="item.id" />
        </ElSelect>
      </ElFormItem>
    </ElForm>

    <h3 style="margin-top: var(--space-4)">W4 输出</h3>
    <div v-if="w4Output" class="w4-grid">
      <ElCard class="mini-card">
        <strong>主诊断</strong>
        <p>{{ w4Output.primaryDiagnosis?.diseaseName || '-' }}</p>
        <p v-if="w4Output.primaryDiagnosis?.diagnosisBasis">依据：{{ w4Output.primaryDiagnosis.diagnosisBasis }}</p>
      </ElCard>
      <ElCard v-for="(item, idx) in w4Output.differentialDiagnoses || []" :key="`ddx-${idx}`" class="mini-card">
        <strong>鉴别诊断</strong>
        <p>{{ item.diseaseName || '-' }}</p>
        <p v-if="item.diagnosisBasis">依据：{{ item.diagnosisBasis }}</p>
      </ElCard>
      <ElCard v-if="w4Output.clinicalAdvice" class="mini-card">
        <strong>临床建议</strong>
        <p>{{ w4Output.clinicalAdvice }}</p>
      </ElCard>
    </div>
    <div v-else>
      <p style="color: var(--color-text-muted)">暂无 W4 输出，可运行 W4。</p>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.diagnosis-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.diagnosis-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.w4-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.mini-card p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.8;
}
</style>


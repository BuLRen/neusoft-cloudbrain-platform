<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElCard, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import { physicianApi, type Disease, type W3Status, type W4Output } from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const loading = ref(false)
const diseases = ref<Disease[]>([])
const w3Status = ref<W3Status | null>(null)
const w4Output = ref<W4Output | null>(null)

const diagnosisForm = reactive({
  diagnosis: '',
  cure: '',
  careful: '',
  diseaseIds: [] as number[],
})

const w3Completed = computed(() => Boolean(w3Status.value?.completed))

async function loadDiseases() {
  diseases.value = await physicianApi.diseases()
}

async function loadW3Status() {
  if (!registerId.value) {
    w3Status.value = null
    return
  }
  try {
    w3Status.value = await physicianApi.w3Status(registerId.value)
  } catch {
    w3Status.value = null
  }
}

async function runW4() {
  if (!registerId.value) return
  if (!w3Completed.value) {
    ElMessage.warning('建议先在「查看结果」完成 W3 结果解读，再运行 W4 以获得更准确的诊断建议')
  }
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

watch(registerId, () => {
  void loadW3Status()
})

onMounted(() => {
  void loadDiseases()
  void loadW3Status()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="门诊确诊"
    description="录入确诊信息并运行 W4 获取疾病诊断建议。W4 会综合病历与 W3 结果解读，最终诊断仍由医生确认。"
    prev-path="/physician/results"
    next-path="/physician/prescription"
  >
    <ElAlert
      v-if="!w3Completed"
      class="diagnosis-alert"
      type="warning"
      :closable="false"
      show-icon
      title="尚未完成 W3 结果解读"
      description="请先在「查看结果」运行 W3，或等待医技提交结果后自动触发。完成 W3 后再运行 W4，诊断建议会更准确。"
    />

    <ElCard v-else-if="w3Status?.overallAnalysis" class="w3-summary-card" shadow="never">
      <strong class="w3-summary-card__title">W3 结果解读摘要</strong>
      <p class="w3-summary-card__text">{{ w3Status.overallAnalysis }}</p>
      <p class="w3-summary-card__hint">以上为结果解读，非最终诊断。请结合临床判断运行 W4。</p>
    </ElCard>

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

    <h3 class="w4-section-title">W4 诊断建议</h3>
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
      <p class="w4-empty">暂无 W4 输出，可运行 W4 获取疾病诊断建议。</p>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.diagnosis-alert {
  margin-block-end: var(--space-4);
}

.w3-summary-card {
  margin-block-end: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 251, 255, 0.88));
}

.w3-summary-card__title {
  display: block;
  font-size: 15px;
}

.w3-summary-card__text,
.w3-summary-card__hint {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  line-height: 1.8;
}

.w3-summary-card__hint {
  font-size: 13px;
  color: var(--color-ai);
}

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

.w4-section-title {
  margin-top: var(--space-4);
}

.w4-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.w4-empty {
  color: var(--color-text-muted);
}

.mini-card p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.8;
}
</style>

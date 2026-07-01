<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElInput, ElMessage } from 'element-plus'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'

const props = defineProps<{
  registerId?: number
  patientId?: number
}>()

const emit = defineEmits<{
  submitted: []
}>()

const metricValue = ref('')
const note = ref('')
const loading = ref(false)

async function submit() {
  const value = Number(metricValue.value)
  if (!props.registerId) {
    ElMessage.warning('请先选择就诊记录')
    return
  }
  if (!value || value <= 0 || value > 33) {
    ElMessage.warning('请输入 0~33 之间的血糖值（mmol/L）')
    return
  }
  loading.value = true
  try {
    await medtechFollowUpApi.submitPatientObservation(
      {
        registerId: props.registerId,
        metricValue: value,
        note: note.value.trim() || undefined,
      },
      { patientId: props.patientId },
    )
    ElMessage.success('血糖已记录，正在更新预测')
    metricValue.value = ''
    note.value = ''
    emit('submitted')
  } catch {
    ElMessage.error('录入失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="glucose-entry-form">
    <h4>录入居家血糖</h4>
    <div class="form-row">
      <label>血糖值 (mmol/L)</label>
      <ElInput v-model="metricValue" type="number" placeholder="例如 6.8" />
    </div>
    <div class="form-row">
      <label>备注（可选）</label>
      <ElInput v-model="note" placeholder="餐后2小时 / 空腹等" />
    </div>
    <ElButton type="primary" :loading="loading" @click="submit">保存并刷新预测</ElButton>
  </div>
</template>

<style scoped>
.glucose-entry-form {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.glucose-entry-form h4 {
  margin: 0;
}

.form-row {
  display: grid;
  gap: var(--space-2);
}

.form-row label {
  font-size: 13px;
  color: var(--color-text-muted);
}
</style>

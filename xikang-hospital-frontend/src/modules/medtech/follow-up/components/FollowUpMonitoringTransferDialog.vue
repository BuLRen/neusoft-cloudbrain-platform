<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { FollowUpDashboardPatient } from '@/shared/types/medtechFollowUp'

const visible = defineModel<boolean>({ default: false })

const props = defineProps<{
  patient: FollowUpDashboardPatient | null
}>()

const emit = defineEmits<{
  submitted: []
}>()

const submitting = ref(false)
const form = reactive({
  reason: '',
})

watch(visible, (open) => {
  if (open) {
    form.reason = ''
  }
})

async function submit() {
  if (!props.patient) return
  const reason = form.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写调换原因')
    return
  }
  submitting.value = true
  try {
    await medtechFollowUpApi.submitMonitoringTransferRequest({
      registerId: props.patient.registerId,
      reason,
    })
    visible.value = false
    emit('submitted')
  } catch {
    // unified error
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <ElDialog v-model="visible" title="申请调换监视" width="440px">
    <p v-if="patient" class="monitoring-transfer-dialog__hint">
      患者：{{ patient.realName ?? '未知' }}（{{ patient.caseNumber ?? patient.registerId }}）
    </p>
    <ElForm label-position="top">
      <ElFormItem label="调换原因" required>
        <ElInput
          v-model="form.reason"
          type="textarea"
          :rows="4"
          placeholder="请说明为何需要调换负责医生（将由管理员审批）"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="submit">提交申请</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.monitoring-transfer-dialog__hint {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>

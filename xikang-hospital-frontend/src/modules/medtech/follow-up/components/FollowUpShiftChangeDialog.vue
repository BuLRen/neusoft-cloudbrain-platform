<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElButton, ElDatePicker, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import type { FollowUpStaffShift } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  shift: FollowUpStaffShift | null
}>()

const visible = defineModel<boolean>({ default: false })

const emit = defineEmits<{
  submitted: []
}>()

const submitting = ref(false)
const form = reactive({
  requestedWorkDate: '',
  reason: '',
})

watch(
  () => props.shift,
  (shift) => {
    form.requestedWorkDate = shift?.workDate ?? ''
    form.reason = ''
  },
)

async function submit() {
  if (!props.shift?.id) return
  if (!form.requestedWorkDate || !form.reason.trim()) {
    ElMessage.warning('请填写调班日期与原因')
    return
  }
  submitting.value = true
  try {
    await medtechFollowUpApi.submitShiftChangeRequest({
      originalShiftId: props.shift.id,
      requestedWorkDate: form.requestedWorkDate,
      reason: form.reason.trim(),
    })
    emit('submitted')
  } catch {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <ElDialog v-model="visible" title="申请调班" width="440px">
    <ElForm label-width="88px">
      <ElFormItem label="原班次">
        <span>{{ shift?.workDate ?? '—' }}</span>
      </ElFormItem>
      <ElFormItem label="调至日期" required>
        <ElDatePicker
          v-model="form.requestedWorkDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择新日期"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="原因" required>
        <ElInput v-model="form.reason" type="textarea" :rows="3" placeholder="请说明调班原因" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="submit">提交申请</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { scheduleApi } from '@/shared/api/modules/schedule'
import type { DoctorSchedule, LeaveRequest } from '@/shared/types/schedule'

const props = defineProps<{
  modelValue: boolean
  /** 触发请假的排班（来自详情抽屉） */
  schedule: DoctorSchedule | null
  /** 当前登录医生 ID（employeeId） */
  physicianId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submitted', leave: LeaveRequest): void
}>()

const open = ref(props.modelValue)
watch(() => props.modelValue, (v) => { open.value = v })
watch(open, (v) => emit('update:modelValue', v))

const LEAVE_TYPES = ['事假', '病假', '公假', '其他'] as const

const formRef = ref<FormInstance | null>(null)
const form = reactive({
  leaveType: '病假' as (typeof LEAVE_TYPES)[number],
  reason: '',
  rawText: '',
})
const submitting = ref(false)

// 打开时重置
watch(() => props.modelValue, (v) => {
  if (v) {
    form.leaveType = '病假'
    form.reason = ''
    form.rawText = ''
    formRef.value?.clearValidate?.()
  }
})

const rules: FormRules = {
  leaveType: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  reason: [{ required: true, message: '请填写请假原因', trigger: 'blur' }],
}

async function handleSubmit() {
  if (!props.schedule || !props.physicianId) {
    ElMessage.warning('排班信息缺失，无法提交')
    return
  }
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const leave = await scheduleApi.createLeave({
      physicianId: props.physicianId,
      leaveDate: props.schedule.workDate,
      timeSlot: props.schedule.timeSlot,
      leaveType: form.leaveType,
      reason: form.reason,
      rawText: form.rawText || undefined,
    })
    ElMessage.success('请假申请已提交，等待管理员审批')
    emit('submitted', leave)
    open.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <ElDialog
    v-model="open"
    title="申请请假"
    width="440px"
    align-center
    class="leave-dialog"
    :close-on-click-modal="false"
  >
    <!-- 班次信息（只读预览） -->
    <div v-if="schedule" class="leave-banner">
      <div class="leave-banner__date">
        <span class="leave-banner__day">{{ new Date(schedule.workDate + 'T00:00:00').getDate() }}</span>
        <div class="leave-banner__meta">
          <strong>{{ schedule.workDate }}</strong>
          <small>{{ schedule.timeSlot }} · {{ schedule.departmentName || '门诊' }}</small>
        </div>
      </div>
      <div class="leave-banner__quota">
        影响已挂号 <strong>{{ schedule.usedQuota }}</strong> 人
      </div>
    </div>

    <ElForm
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="leave-form"
    >
      <ElFormItem label="请假类型" prop="leaveType">
        <div class="leave-type-group">
          <button
            v-for="t in LEAVE_TYPES"
            :key="t"
            type="button"
            class="leave-type-chip"
            :class="{ 'is-active': form.leaveType === t }"
            @click="form.leaveType = t"
          >
            {{ t }}
          </button>
        </div>
      </ElFormItem>

      <ElFormItem label="请假原因" prop="reason">
        <ElInput
          v-model="form.reason"
          type="textarea"
          :rows="3"
          placeholder="请简要说明请假原因，例如：身体不适需要就医"
        />
      </ElFormItem>

      <ElFormItem label="补充说明（可选）" class="leave-form__optional">
        <ElInput
          v-model="form.rawText"
          type="textarea"
          :rows="2"
          placeholder="自然语言补充，便于 AI 更准确生成替班方案"
        />
        <div class="leave-form__hint">
          管理员审批通过后，系统将调用 AI 自动生成替班调整方案
        </div>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="open = false">取消</ElButton>
      <ElButton
        type="primary"
        :loading="submitting"
        class="leave-submit"
        @click="handleSubmit"
      >
        提交申请
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.leave-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 20px;
  background: var(--sched-surface-alt);
  border: 1px solid var(--sched-line);
  border-radius: 10px;
  font-family: var(--sched-font-body);
}

.leave-banner__date {
  display: flex;
  align-items: center;
  gap: 12px;
}

.leave-banner__day {
  font-family: var(--sched-font-display);
  font-size: 32px;
  font-weight: 600;
  color: var(--sched-ink);
  line-height: 1;
}

.leave-banner__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.leave-banner__meta strong {
  font-family: var(--sched-font-mono);
  font-size: 12px;
  font-weight: 500;
  color: var(--sched-ink-soft);
}

.leave-banner__meta small {
  font-size: 11px;
  color: var(--sched-ink-mute);
}

.leave-banner__quota {
  font-size: 12px;
  color: var(--sched-ink-soft);
}

.leave-banner__quota strong {
  font-family: var(--sched-font-mono);
  font-size: 16px;
  color: var(--sched-today);
  font-weight: 700;
}

/* 类型芯片选择器 */
.leave-type-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.leave-type-chip {
  padding: 8px 16px;
  border: 1px solid var(--sched-line);
  border-radius: 999px;
  background: var(--sched-surface);
  color: var(--sched-ink-soft);
  font-family: var(--sched-font-body);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 140ms cubic-bezier(0.2, 0, 0, 1);
}

.leave-type-chip:hover {
  border-color: var(--sched-line-strong);
  color: var(--sched-ink);
}

.leave-type-chip.is-active {
  background: var(--sched-primary);
  border-color: var(--sched-primary);
  color: #FFFFFF;
}

.leave-form :deep(.el-textarea__inner) {
  font-family: var(--sched-font-body);
  border-radius: 8px;
  border-color: var(--sched-line);
}

.leave-form :deep(.el-textarea__inner:focus) {
  border-color: var(--sched-ink);
  box-shadow: 0 0 0 2px rgba(70, 111, 160, 0.08);
}

.leave-form__optional :deep(.el-form-item__label) {
  color: var(--sched-ink-mute);
}

.leave-form__hint {
  margin-top: 6px;
  font-size: 11px;
  color: var(--sched-ink-mute);
  line-height: 1.5;
}

.leave-submit {
  background: var(--sched-primary);
  border-color: var(--sched-primary);
}

.leave-submit:hover:not(:disabled) {
  background: var(--color-primary-strong, #006ce6);
  border-color: var(--color-primary-strong, #006ce6);
}
</style>

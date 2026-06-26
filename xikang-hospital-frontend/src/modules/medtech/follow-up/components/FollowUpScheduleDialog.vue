<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElOption, ElSelect } from 'element-plus'
import type { FollowUpDashboardPatient } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  visible: boolean
  scheduleDate: string
  patients: FollowUpDashboardPatient[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: { registerId?: number; scheduleDate: string; itemType: string; title: string }]
}>()

const itemType = ref<'interview' | 'custom'>('interview')
const registerId = ref<number | undefined>()
const title = ref('')

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

watch(
  () => props.visible,
  (open) => {
    if (!open) return
    itemType.value = 'interview'
    registerId.value = props.patients[0]?.registerId
    title.value = ''
  },
)

function handleSubmit() {
  const payload = {
    registerId: itemType.value === 'interview' ? registerId.value : undefined,
    scheduleDate: props.scheduleDate,
    itemType: itemType.value === 'interview' ? 'interview' : 'custom',
    title: title.value.trim() || (itemType.value === 'custom' ? '自定义事项' : ''),
  }
  emit('submit', payload)
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="`添加日程 · ${scheduleDate}`"
    width="480px"
    :lock-scroll="false"
    modal-class="outcome-dialog-overlay"
  >
    <ElForm label-position="top">
      <ElFormItem label="事项类型">
        <ElSelect v-model="itemType" style="width: 100%">
          <ElOption label="患者访谈" value="interview" />
          <ElOption label="自定义事项" value="custom" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="itemType === 'interview'" label="选择患者">
        <ElSelect v-model="registerId" style="width: 100%" placeholder="请选择患者">
          <ElOption
            v-for="patient in patients"
            :key="patient.registerId"
            :label="`${patient.realName}（${patient.caseNumber ?? patient.registerId}）`"
            :value="patient.registerId"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="标题（可选）">
        <ElInput v-model="title" placeholder="留空将自动生成标题" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="dialogVisible = false">取消</ElButton>
      <ElButton type="primary" @click="handleSubmit">保存</ElButton>
    </template>
  </ElDialog>
</template>

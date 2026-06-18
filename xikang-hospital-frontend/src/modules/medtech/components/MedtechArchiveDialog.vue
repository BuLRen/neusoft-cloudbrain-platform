<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import { medtechApi, type MedtechApplication } from '@/shared/api/modules/medtech'
import { ARCHIVE_REASONS } from '../constants/archiveReasons'

const props = defineProps<{
  visible: boolean
  application: MedtechApplication | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  archived: []
}>()

const reason = ref<string>('')
const remark = ref('')
const submitting = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const title = computed(() => {
  const name = props.application?.techName || '申请'
  return `归档 · ${name}`
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      reason.value = ''
      remark.value = ''
    }
  },
)

async function submitArchive() {
  const app = props.application
  if (!app?.id || !reason.value) {
    ElMessage.warning('请选择归档原因')
    return
  }
  submitting.value = true
  try {
    const body = { reason: reason.value, remark: remark.value.trim() || undefined }
    if (app.techType === 'check') {
      await medtechApi.archiveCheck(app.id, body)
    } else if (app.techType === 'inspection') {
      await medtechApi.archiveInspection(app.id, body)
    } else {
      await medtechApi.archiveDisposal(app.id, body)
    }
    ElMessage.success('已归档')
    dialogVisible.value = false
    emit('archived')
  } catch {
    ElMessage.error('归档失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <ElDialog v-model="dialogVisible" :title="title" width="480px" align-center destroy-on-close>
    <ElForm label-position="top">
      <ElFormItem label="归档原因" required>
        <ElSelect v-model="reason" placeholder="请选择原因" style="width: 100%">
          <ElOption v-for="item in ARCHIVE_REASONS" :key="item" :label="item" :value="item" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="补充说明">
        <ElInput v-model="remark" type="textarea" :rows="3" placeholder="可选" maxlength="200" show-word-limit />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="dialogVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="submitting" :disabled="!reason" @click="submitArchive">确认归档</ElButton>
    </template>
  </ElDialog>
</template>

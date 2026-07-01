<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus'
import { medtechApi, type DisposalReport } from '@/shared/api/modules/medtech'
import MedtechStepLayout from '../layouts/MedtechStepLayout.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const report = ref<DisposalReport | null>(null)
const resultText = ref('')
const remark = ref('')
const started = ref(false)

const id = computed(() => Number(route.query.id || 0))
const canSubmit = computed(() => started.value && !!resultText.value.trim() && !loading.value && report.value?.paid !== false)

async function loadPage() {
  if (!id.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    report.value = await medtechApi.disposalReport(id.value)
    resultText.value = report.value.disposalResult ?? ''
    remark.value = report.value.disposalRemark ?? ''

    if (report.value.paid === false) {
      errorMessage.value = '患者尚未支付处置费，请提醒患者先完成缴费后再执行'
      started.value = false
      return
    }

    if (report.value.disposalState === '待处置') {
      await medtechApi.startDisposal(id.value)
      report.value = { ...report.value, disposalState: '处置中', statusText: '处置中' }
    }
    started.value = report.value.disposalState === '处置中'
  } catch (err) {
    report.value = null
    const msg = err instanceof Error ? err.message : ''
    errorMessage.value = msg || '处置申请加载失败，请返回列表重试'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!id.value || !canSubmit.value) return
  loading.value = true
  try {
    await medtechApi.submitDisposalResult(id.value, {
      disposalResult: resultText.value.trim(),
      disposalRemark: remark.value.trim() || undefined,
    })
    ElMessage.success('处置结果已提交')
    router.push('/medtech/check-queue')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPage()
})
</script>

<template>
  <MedtechStepLayout
    :step="2"
    :total-steps="2"
    title="处置执行"
    description="确认申请信息并录入处置执行结果。"
    prev-path="/medtech/check-queue"
  >
    <ElEmpty v-if="!id" description="请从医技申请列表选择一条处置记录" />

    <template v-else>
      <ElAlert
        v-if="errorMessage"
        type="error"
        :title="errorMessage"
        show-icon
        :closable="false"
        class="section-alert"
      />

      <section v-if="report" v-loading="loading" class="detail-section">
        <h3 class="section-title">申请信息</h3>
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem label="病历号">{{ report.caseNumber || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="患者">{{ report.patientName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="处置项目">{{ report.techName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">{{ report.statusText || report.disposalState || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="处置部位">{{ report.position || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="项目编码">{{ report.techCode || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="目的要求" :span="2">{{ report.info || '-' }}</ElDescriptionsItem>
        </ElDescriptions>
      </section>

      <section v-if="started" class="form-section">
        <h3 class="section-title">结果录入</h3>
        <ElForm label-position="top">
          <ElFormItem label="处置结果" required>
            <ElInput
              v-model="resultText"
              type="textarea"
              :rows="5"
              placeholder="记录处置执行情况，例如：雾化吸入 15 分钟，患者耐受良好"
            />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput v-model="remark" type="textarea" :rows="2" placeholder="可选" />
          </ElFormItem>
        </ElForm>
      </section>

      <div class="actions">
        <ElButton @click="router.push('/medtech/check-queue')">返回列表</ElButton>
        <ElButton type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">提交处置结果</ElButton>
      </div>
    </template>
  </MedtechStepLayout>
</template>

<style scoped>
.section-title {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-md);
  font-weight: 600;
}

.detail-section,
.form-section {
  margin-block-end: var(--space-5);
}

.section-alert {
  margin-block-end: var(--space-4);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-block-start: var(--space-4);
  padding-block-start: var(--space-4);
  border-block-start: 1px solid var(--color-border);
}
</style>

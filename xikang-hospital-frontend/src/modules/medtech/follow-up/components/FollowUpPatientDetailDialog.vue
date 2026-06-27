<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElMessage,
  ElTag,
} from 'element-plus'
import { medtechFollowUpApi } from '@/shared/api/modules/medtechFollowUp'
import { formatBeijingDateTime } from '@/shared/utils/beijingDate'
import type { FollowUpPatientDetail } from '@/shared/types/medtechFollowUp'

const props = defineProps<{
  visible: boolean
  registerId?: number
  fallbackName?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const loading = ref(false)
const detail = ref<FollowUpPatientDetail | null>(null)

watch(
  () => [props.visible, props.registerId] as const,
  ([visible, registerId]) => {
    if (visible && registerId) {
      void loadDetail(registerId)
    }
  },
)

async function loadDetail(registerId: number) {
  loading.value = true
  detail.value = null
  try {
    detail.value = await medtechFollowUpApi.getPatientDetail(registerId)
  } catch {
    emit('update:visible', false)
    ElMessage.error('加载患者信息失败')
  } finally {
    loading.value = false
  }
}

function displayValue(value?: string | number | null) {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function maskPhone(phone?: string) {
  if (!phone || phone.length < 11) return displayValue(phone)
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

function maskIdCard(idCard?: string) {
  if (!idCard || idCard.length < 10) return displayValue(idCard)
  return `${idCard.slice(0, 3)}********${idCard.slice(-4)}`
}

function visitStateLabel(state?: number) {
  if (state === 1) return '已挂号'
  if (state === 2) return '医生接诊'
  if (state === 3) return '看诊结束'
  if (state === 4) return '已退号'
  return '—'
}

function handleClosed() {
  detail.value = null
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="`${detail?.realName ?? fallbackName ?? '患者'} · 详细信息`"
    width="760px"
    :lock-scroll="false"
    modal-class="outcome-dialog-overlay"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @closed="handleClosed"
  >
    <div v-loading="loading" class="patient-detail-dialog">
      <template v-if="detail">
        <h4 class="patient-detail-dialog__section">基本信息</h4>
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem label="姓名">{{ displayValue(detail.realName) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="病历号">{{ displayValue(detail.caseNumber) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="性别">{{ displayValue(detail.gender) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="出生日期">{{ displayValue(detail.birthdate) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="年龄">
            {{ detail.age != null ? `${detail.age}${detail.ageType ?? ''}` : '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="看诊状态">{{ visitStateLabel(detail.visitState) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="诊断" :span="2">{{ displayValue(detail.diagnosis) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="初步诊断" :span="2">{{ displayValue(detail.preliminaryDiagnosis) }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="detail.diseases?.length" label="关联疾病" :span="2">
            <ElTag
              v-for="disease in detail.diseases"
              :key="disease.diseaseId"
              type="info"
              effect="plain"
              class="patient-detail-dialog__tag"
            >
              {{ disease.diseaseName }}
            </ElTag>
          </ElDescriptionsItem>
        </ElDescriptions>

        <h4 class="patient-detail-dialog__section">联系方式</h4>
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem label="手机号">{{ maskPhone(detail.phone) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="邮箱">{{ displayValue(detail.email) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="身份证号">{{ maskIdCard(detail.idCard ?? detail.cardNumber) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="就诊卡号">{{ displayValue(detail.cardNumber) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="家庭住址" :span="2">
            {{ displayValue(detail.contactAddress ?? detail.homeAddress) }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <h4 class="patient-detail-dialog__section">就诊信息</h4>
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem label="就诊科室">{{ displayValue(detail.departmentName) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="接诊医生">{{ displayValue(detail.physicianName) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="号别">{{ displayValue(detail.registLevelName) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="结算类别">{{ displayValue(detail.settleCategoryName) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="就诊时间">{{ formatBeijingDateTime(detail.visitDate) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="午别">{{ displayValue(detail.noon) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="挂号方式">{{ displayValue(detail.registMethod) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="挂号费">
            {{ detail.registMoney != null ? `¥ ${detail.registMoney}` : '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="是否预约">{{ displayValue(detail.isBook) }}</ElDescriptionsItem>
        </ElDescriptions>

        <h4 class="patient-detail-dialog__section">病历摘要</h4>
        <ElDescriptions :column="1" border size="small">
          <ElDescriptionsItem label="主诉">{{ displayValue(detail.chiefComplaint) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="现病史">{{ displayValue(detail.presentIllness) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="既往史">{{ displayValue(detail.pastHistory) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="过敏史">{{ displayValue(detail.allergy) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="体格检查">{{ displayValue(detail.physique) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="治疗建议">{{ displayValue(detail.treatmentProposal) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="注意事项">{{ displayValue(detail.precautions) }}</ElDescriptionsItem>
        </ElDescriptions>
      </template>
      <ElEmpty v-else-if="!loading" description="暂无患者信息" />
    </div>
  </ElDialog>
</template>

<style scoped>
.patient-detail-dialog {
  min-height: 120px;
}

.patient-detail-dialog__section {
  margin: var(--space-4) 0 var(--space-2);
  font-size: 14px;
  font-weight: 650;
}

.patient-detail-dialog__section:first-child {
  margin-block-start: 0;
}

.patient-detail-dialog__tag {
  margin-inline-end: var(--space-2);
  margin-block-end: var(--space-1);
}
</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElMessage,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import LabReportPrintSheet from '@/shared/components/LabReportPrintSheet.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { aiApi } from '@/shared/api/modules/ai'
type RequestKind = 'check' | 'inspection' | 'disposal'
import {
  physicianApi,
  type Disease,
  type Drug,
  type MedicalRecord,
  type MedicalTechnology,
  type PhysicianPatient,
  type StructuredRecord,
  type W2Output,
  type W3Output,
  type W4Output,
  type InspectionResult,
} from '@/shared/api/modules/physician'
import { useLabReportExport } from '@/shared/composables/useLabReportExport'
import { buildLabReportContextFromPhysician } from '@/shared/types/labReportPdf'
import { hasExportableLabReportPayload, resolveStructuredOutputFromPayload } from '@/shared/types/simulatedCheckResult'

type TechType = MedicalTechnology['techType']

interface RequestDraft {
  medicalTechnologyId?: number
  info: string
  position: string
  remark: string
}

interface BasketItem extends RequestDraft {
  techName: string
  techType: TechType
}

const TECH_TYPE_LABEL: Record<TechType, string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

interface PrescriptionDraft {
  drugId?: number
  drugUsage: string
  drugNumber: number
}

const loading = ref(false)
const patients = ref<PhysicianPatient[]>([])
const selectedPatient = ref<PhysicianPatient | null>(null)
const keyword = ref('')
const activeTab = ref('record')
const stats = reactive({ totalVisited: 0, totalWaiting: 0 })

const recordForm = reactive({
  id: undefined as number | undefined,
  readme: '',
  present: '',
  presentTreat: '',
  history: '',
  allergy: '',
  physique: '',
  proposal: '',
  diseaseIds: [] as number[],
})

const diagnosisForm = reactive({
  diagnosis: '',
  cure: '',
  careful: '',
  diseaseIds: [] as number[],
})

const technologyKeyword = ref('')
const technologies = ref<MedicalTechnology[]>([])
const requestDraft = reactive<RequestDraft>({ info: '', position: '', remark: '' })
const requestBasket = ref<BasketItem[]>([])

const diseases = ref<Disease[]>([])
const checkResults = ref<Awaited<ReturnType<typeof physicianApi.checkResults>>>([])
const inspectionResults = ref<Awaited<ReturnType<typeof physicianApi.inspectionResults>>>([])
const examSuggestions = ref<Record<string, unknown>[]>([])
const diagnosisSuggestions = ref<Record<string, unknown>[]>([])
const w1LongText = ref('')
const w1InputMode = ref<'pre_consultation' | 'long_text' | 'doctor_form'>('pre_consultation')
const structuredRecord = ref<StructuredRecord | null>(null)
const w2Output = ref<W2Output | null>(null)
const w3Output = ref<W3Output | null>(null)
const w4Output = ref<W4Output | null>(null)
const printSheetRef = ref<InstanceType<typeof LabReportPrintSheet> | null>(null)

const { exportContext, exporting, exportPdf } = useLabReportExport()
const aiPipelineLoading = ref(false)
const confirmedDiagnosisForRx = ref('')

const drugKeyword = ref('')
const drugs = ref<Drug[]>([])
const prescriptionDraft = reactive<PrescriptionDraft>({ drugUsage: '', drugNumber: 1 })
const prescriptionBasket = ref<Array<PrescriptionDraft & { drugName: string; drugPrice: number }>>([])
const prescriptions = ref<Awaited<ReturnType<typeof physicianApi.prescriptions>>>([])

const selectedRegisterId = computed(() => selectedPatient.value?.registerId)

async function selectPatient(patient: PhysicianPatient) {
  selectedPatient.value = patient
  // 实时拉取最新 AI 预问诊（避免进入时正好在做）
  try {
    const session = await aiApi.previsitSession(patient.registerId)
    if (session && session.exists && session.state === 'completed' && session.summary) {
      selectedPatient.value = {
        ...patient,
        hasAiConsultation: true,
        aiConsultSummary: {
          chiefComplaint: session.summary.chiefComplaint,
          symptomDuration: session.summary.symptomDuration,
          historySummary: session.summary.historySummary,
          allergySummary: session.summary.allergySummary,
          medicationSummary: session.summary.medicationSummary,
          aiSummary: session.summary.aiSummary,
          suggestedExam: session.summary.suggestedExam,
        },
      }
    } else if (session && !session.exists) {
      selectedPatient.value = { ...patient, hasAiConsultation: false, aiConsultSummary: undefined }
    }
  } catch {
    /* ignore */
  }
}
const selectedDiseaseNames = computed(() => diseases.value.filter((item) => diagnosisForm.diseaseIds.includes(item.id)).map((item) => item.diseaseName).join('、'))

async function loadPatients() {
  loading.value = true
  try {
    const [patientPage, patientStats] = await Promise.all([
      physicianApi.patients({ keyword: keyword.value, page: 1, size: 20 }),
      physicianApi.patientStats(),
    ])
    patients.value = patientPage.records
    stats.totalVisited = patientStats.totalVisited || 0
    stats.totalWaiting = patientStats.totalWaiting || 0
    if (!selectedPatient.value && patients.value.length > 0) {
      selectedPatient.value = patients.value[0]
    }
  } finally {
    loading.value = false
  }
}

async function loadPatientContext() {
  if (!selectedRegisterId.value) {
    return
  }
  const registerId = selectedRegisterId.value
  const [record, diseaseOptions, checks, inspections, examAi, diagnosisAi, existingPrescriptions] = await Promise.all([
    physicianApi.medicalRecord(registerId),
    physicianApi.diseases(),
    physicianApi.checkResults(registerId),
    physicianApi.inspectionResults(registerId),
    physicianApi.examSuggestions(registerId),
    physicianApi.diagnosisSuggestions(registerId),
    physicianApi.prescriptions(registerId),
  ])
  diseases.value = diseaseOptions
  applyMedicalRecord(record)
  checkResults.value = checks
  inspectionResults.value = inspections
  examSuggestions.value = examAi || []
  diagnosisSuggestions.value = diagnosisAi || []
  prescriptions.value = existingPrescriptions
}

function applyMedicalRecord(record: MedicalRecord | null) {
  recordForm.id = record?.id
  recordForm.readme = record?.readme || selectedPatient.value?.aiConsultSummary?.chiefComplaint || ''
  recordForm.present = record?.present || selectedPatient.value?.aiConsultSummary?.aiSummary || ''
  recordForm.presentTreat = record?.presentTreat || ''
  recordForm.history = record?.history || selectedPatient.value?.aiConsultSummary?.historySummary || ''
  recordForm.allergy = record?.allergy || selectedPatient.value?.aiConsultSummary?.allergySummary || ''
  recordForm.physique = record?.physique || ''
  recordForm.proposal = record?.proposal || selectedPatient.value?.aiConsultSummary?.suggestedExam || ''
  recordForm.diseaseIds = record?.diseases?.map((item) => item.id) || []
  diagnosisForm.diagnosis = record?.diagnosis || ''
  diagnosisForm.cure = record?.cure || ''
  diagnosisForm.careful = record?.careful || ''
  diagnosisForm.diseaseIds = record?.diseases?.map((item) => item.id) || []
  confirmedDiagnosisForRx.value = record?.diagnosis || diagnosisForm.diagnosis
}

async function runW1() {
  if (!selectedRegisterId.value) return
  aiPipelineLoading.value = true
  try {
    const payload: Record<string, unknown> = {
      registerId: selectedRegisterId.value,
      inputMode: w1InputMode.value,
      patientInfoFromRegister: {
        realName: selectedPatient.value?.realName,
        gender: selectedPatient.value?.gender,
        age: selectedPatient.value?.age,
        caseNumber: selectedPatient.value?.caseNumber,
      },
    }
    if (w1InputMode.value === 'long_text') {
      payload.longText = w1LongText.value
    } else if (w1InputMode.value === 'doctor_form') {
      payload.doctorForm = { ...recordForm }
    }
    structuredRecord.value = await physicianApi.aiW1(payload)
    applyStructuredToRecordForm(structuredRecord.value)
    ElMessage.success('W1 病历字段已结构化')
  } finally {
    aiPipelineLoading.value = false
  }
}

function applyStructuredToRecordForm(record: StructuredRecord) {
  recordForm.readme = record.chiefComplaint || recordForm.readme
  recordForm.present = record.presentIllness || recordForm.present
  recordForm.presentTreat = record.presentTreat || recordForm.presentTreat
  recordForm.history = record.history || recordForm.history
  recordForm.allergy = record.allergy || recordForm.allergy
  recordForm.physique = record.physique || recordForm.physique
  recordForm.proposal = record.preliminaryImpression || recordForm.proposal
}

async function runW2() {
  if (!selectedRegisterId.value) return
  aiPipelineLoading.value = true
  try {
    w2Output.value = await physicianApi.aiW2(selectedRegisterId.value)
    await loadPatientContext()
    ElMessage.success('W2 检查推荐已生成')
  } finally {
    aiPipelineLoading.value = false
  }
}

async function runW2b() {
  if (!selectedRegisterId.value) return
  aiPipelineLoading.value = true
  try {
    await physicianApi.aiW2b(selectedRegisterId.value, true)
    await loadPatientContext()
    ElMessage.success('W2b 模拟检查结果已生成')
  } finally {
    aiPipelineLoading.value = false
  }
}

async function runW3() {
  if (!selectedRegisterId.value) return
  aiPipelineLoading.value = true
  try {
    w3Output.value = await physicianApi.aiW3(selectedRegisterId.value)
    ElMessage.success('W3 结果分析已完成')
  } finally {
    aiPipelineLoading.value = false
  }
}

async function runW4() {
  if (!selectedRegisterId.value) return
  aiPipelineLoading.value = true
  try {
    w4Output.value = await physicianApi.aiW4(selectedRegisterId.value)
    if (w4Output.value?.primaryDiagnosis?.diseaseName) {
      diagnosisForm.diagnosis = w4Output.value.primaryDiagnosis.diseaseName
      confirmedDiagnosisForRx.value = w4Output.value.primaryDiagnosis.diseaseName
    }
    await loadPatientContext()
    ElMessage.success('W4 诊断建议已生成')
  } finally {
    aiPipelineLoading.value = false
  }
}

async function runFullPipeline() {
  if (!selectedRegisterId.value) return
  aiPipelineLoading.value = true
  try {
    const result = await physicianApi.aiPipelineRun({
      registerId: selectedRegisterId.value,
      inputMode: w1InputMode.value,
      longText: w1InputMode.value === 'long_text' ? w1LongText.value : undefined,
      autoCreateRequests: true,
    })
    structuredRecord.value = result.w1
    w2Output.value = result.w2
    w3Output.value = result.w3
    w4Output.value = result.w4
    applyStructuredToRecordForm(result.w1)
    if (result.w4?.primaryDiagnosis?.diseaseName) {
      diagnosisForm.diagnosis = result.w4.primaryDiagnosis.diseaseName
      confirmedDiagnosisForRx.value = result.w4.primaryDiagnosis.diseaseName
    }
    await loadPatientContext()
    ElMessage.success('无人医院 AI 流水线执行完成')
  } finally {
    aiPipelineLoading.value = false
  }
}

async function saveMedicalRecord() {
  if (!selectedRegisterId.value || !recordForm.readme.trim()) {
    ElMessage.warning('请先选择患者并填写主诉')
    return
  }
  const payload = { ...recordForm, registerId: selectedRegisterId.value }
  if (recordForm.id) {
    await physicianApi.updateMedicalRecord(recordForm.id, payload)
  } else {
    const result = await physicianApi.createMedicalRecord(payload)
    recordForm.id = result.id
  }
  ElMessage.success('病历已保存')
  await loadPatientContext()
}

function technologyOptionLabel(item: MedicalTechnology) {
  return `${item.techName} / ${TECH_TYPE_LABEL[item.techType]} / ${item.techPrice}元`
}

async function searchTechnologies() {
  technologies.value = await physicianApi.medicalTechnologies(undefined, technologyKeyword.value || undefined)
}

function addTechnologyToBasket() {
  const tech = technologies.value.find((item) => item.id === requestDraft.medicalTechnologyId)
  if (!tech) {
    ElMessage.warning('请选择医技项目')
    return
  }
  requestBasket.value.push({
    ...requestDraft,
    medicalTechnologyId: tech.id,
    techName: tech.techName,
    techType: tech.techType,
  })
  requestDraft.medicalTechnologyId = undefined
  requestDraft.info = ''
  requestDraft.position = ''
  requestDraft.remark = ''
}

function toRequestItems(items: BasketItem[], techType: TechType) {
  return items.map((item) => {
    const base = { medicalTechnologyId: item.medicalTechnologyId }
    if (techType === 'check') {
      return { ...base, checkInfo: item.info, checkPosition: item.position, checkRemark: item.remark }
    }
    if (techType === 'inspection') {
      return { ...base, inspectionInfo: item.info, inspectionPosition: item.position, inspectionRemark: item.remark }
    }
    return { ...base, disposalInfo: item.info, disposalPosition: item.position, disposalRemark: item.remark }
  })
}

async function submitTechnologyRequest() {
  if (!selectedRegisterId.value || requestBasket.value.length === 0) {
    ElMessage.warning('请先选择患者并添加申请项目')
    return
  }
  const registerId = selectedRegisterId.value
  const byType = (type: TechType) => requestBasket.value.filter((item) => item.techType === type)

  const checkItems = byType('check')
  const inspectionItems = byType('inspection')
  const disposalItems = byType('disposal')

  if (checkItems.length) {
    await physicianApi.createCheckRequest({ registerId, items: toRequestItems(checkItems, 'check') })
  }
  if (inspectionItems.length) {
    await physicianApi.createInspectionRequest({ registerId, items: toRequestItems(inspectionItems, 'inspection') })
  }
  if (disposalItems.length) {
    await physicianApi.createDisposalRequest({ registerId, items: toRequestItems(disposalItems, 'disposal') })
  }

  requestBasket.value = []
  ElMessage.success('申请已提交')
  await loadPatientContext()
}

async function saveDiagnosis() {
  if (!selectedRegisterId.value || !recordForm.id || !diagnosisForm.diagnosis.trim() || !diagnosisForm.cure.trim()) {
    ElMessage.warning('请先保存病历，并填写诊断和处理意见')
    return
  }
  await physicianApi.submitDiagnosis({
    registerId: selectedRegisterId.value,
    medicalRecordId: recordForm.id,
    diagnosis: diagnosisForm.diagnosis,
    cure: diagnosisForm.cure,
    careful: diagnosisForm.careful,
    diseaseIds: diagnosisForm.diseaseIds,
  })
  ElMessage.success('确诊结果已保存')
  await loadPatientContext()
}

async function searchDrugs() {
  drugs.value = await physicianApi.drugs(drugKeyword.value)
}

function addDrugToBasket() {
  const drug = drugs.value.find((item) => item.id === prescriptionDraft.drugId)
  if (!drug || !prescriptionDraft.drugUsage.trim()) {
    ElMessage.warning('请选择药品并填写用法用量')
    return
  }
  prescriptionBasket.value.push({
    drugId: drug.id,
    drugName: drug.drugName,
    drugPrice: drug.drugPrice,
    drugUsage: prescriptionDraft.drugUsage,
    drugNumber: prescriptionDraft.drugNumber,
  })
  prescriptionDraft.drugId = undefined
  prescriptionDraft.drugUsage = ''
  prescriptionDraft.drugNumber = 1
}

async function submitPrescription() {
  if (!selectedRegisterId.value || prescriptionBasket.value.length === 0) {
    ElMessage.warning('请先添加处方药品')
    return
  }
  if (!confirmedDiagnosisForRx.value.trim() && !diagnosisForm.diagnosis.trim()) {
    ElMessage.warning('请先完成确诊（W4 或手动填写诊断）再开方')
    return
  }
  await physicianApi.createPrescription({
    registerId: selectedRegisterId.value,
    confirmedDiagnosis: confirmedDiagnosisForRx.value || diagnosisForm.diagnosis,
    items: prescriptionBasket.value.map((item) => ({ drugId: item.drugId, drugUsage: item.drugUsage, drugNumber: String(item.drugNumber) })),
  })
  prescriptionBasket.value = []
  ElMessage.success('处方已开立')
  await loadPatientContext()
}

function adoptDiagnosisSuggestion(item: Record<string, unknown>) {
  diagnosisForm.diagnosis = String(item.diseaseName || '')
  diagnosisForm.cure = String(item.treatmentDirection || '')
  const diseaseId = Number(item.diseaseId)
  if (diseaseId && !diagnosisForm.diseaseIds.includes(diseaseId)) {
    diagnosisForm.diseaseIds.push(diseaseId)
  }
}

function canExportInspectionPdf(row: InspectionResult): boolean {
  return hasExportableLabReportPayload(row.inspectionResult)
}

async function handleExportInspectionPdf(row: InspectionResult) {
  const structuredOutput = resolveStructuredOutputFromPayload(row.inspectionResult)
  if (!structuredOutput) {
    ElMessage.warning('暂无结构化检验明细，无法导出 PDF')
    return
  }
  await exportPdf(
    buildLabReportContextFromPhysician(row, structuredOutput, {
      realName: selectedPatient.value?.realName ?? '',
      caseNumber: selectedPatient.value?.caseNumber ?? '',
      gender: selectedPatient.value?.gender,
      age: selectedPatient.value?.age,
    }),
    printSheetRef,
  )
}

watch(selectedRegisterId, () => {
  void loadPatientContext()
})

onMounted(async () => {
  await Promise.all([loadPatients(), searchTechnologies(), searchDrugs()])
})
</script>

<template>
  <div class="physician-workspace u-page-grid">
    <PageHeader
      title="医生工作站"
      description="无人医院场景：W1 结构化 → W2 推荐检查 → W2b/CNN 模拟结果 → W3 分析 → W4 诊断 → 开方（仅携带确诊病名，审方由人员B负责）。"
      eyebrow="Role A"
    >
      <template #actions>
        <ElButton type="primary" @click="loadPatients">刷新患者</ElButton>
      </template>
    </PageHeader>

    <section class="workspace-grid">
      <GlassCard class="patient-panel">
        <div class="panel-heading">
          <div>
            <h2>待诊患者</h2>
            <p>待诊 {{ stats.totalWaiting }} 人，已完成 {{ stats.totalVisited }} 人</p>
          </div>
        </div>
        <ElInput v-model="keyword" placeholder="搜索病历号或姓名" clearable @keyup.enter="loadPatients">
          <template #append>
            <ElButton @click="loadPatients">查询</ElButton>
          </template>
        </ElInput>
        <div class="patient-list">
          <ElAlert v-if="loading" type="info" :closable="false" title="正在加载患者列表" />
          <button
            v-for="patient in patients"
            :key="patient.registerId"
            class="patient-item"
            :class="{ 'is-active': patient.registerId === selectedRegisterId }"
            type="button"
            @click="selectPatient(patient)"
          >
            <strong>{{ patient.realName }}</strong>
            <span>{{ patient.caseNumber }}</span>
            <StatusTag :tone="patient.visitState === 1 ? 'warning' : 'primary'">
              {{ patient.visitState === 1 ? '待接诊' : '接诊中' }}
            </StatusTag>
          </button>
          <ElEmpty v-if="!loading && patients.length === 0" description="暂无待诊患者" />
        </div>
      </GlassCard>

      <main class="work-panel">
        <GlassCard v-if="selectedPatient" class="patient-summary">
          <ElDescriptions :column="4" border>
            <ElDescriptionsItem label="患者">{{ selectedPatient.realName }}</ElDescriptionsItem>
            <ElDescriptionsItem label="病历号">{{ selectedPatient.caseNumber }}</ElDescriptionsItem>
            <ElDescriptionsItem label="性别">{{ selectedPatient.gender || '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="年龄">{{ selectedPatient.age || '-' }}</ElDescriptionsItem>
          </ElDescriptions>
          <div
            v-if="selectedPatient.aiConsultSummary"
            class="ai-consult-card"
          >
            <div class="ai-consult-header">
              <span class="ai-consult-icon">🤖</span>
              <strong>AI 预问诊摘要</strong>
              <ElTag v-if="selectedPatient.aiConsultSummary.chiefComplaint" type="success" size="small">已完成</ElTag>
            </div>
            <div class="ai-consult-grid">
              <div class="ai-consult-item">
                <label>主诉</label>
                <p>{{ selectedPatient.aiConsultSummary.chiefComplaint || '—' }}</p>
              </div>
              <div class="ai-consult-item">
                <label>症状时长</label>
                <p>{{ selectedPatient.aiConsultSummary.symptomDuration || '—' }}</p>
              </div>
              <div class="ai-consult-item full">
                <label>现病史</label>
                <p>{{ selectedPatient.aiConsultSummary.aiSummary || '—' }}</p>
              </div>
              <div class="ai-consult-item">
                <label>既往史</label>
                <p>{{ selectedPatient.aiConsultSummary.historySummary || '—' }}</p>
              </div>
              <div class="ai-consult-item">
                <label>过敏史</label>
                <p>{{ selectedPatient.aiConsultSummary.allergySummary || '—' }}</p>
              </div>
              <div v-if="selectedPatient.aiConsultSummary.medicationSummary" class="ai-consult-item full">
                <label>用药史</label>
                <p>{{ selectedPatient.aiConsultSummary.medicationSummary }}</p>
              </div>
              <div v-if="selectedPatient.aiConsultSummary.suggestedExam" class="ai-consult-item full">
                <label>建议检查</label>
                <p>{{ selectedPatient.aiConsultSummary.suggestedExam }}</p>
              </div>
            </div>
          </div>
          <ElAlert
            v-else-if="selectedPatient.hasAiConsultation === false"
            class="ai-summary"
            type="info"
            show-icon
            :closable="false"
            title="患者未完成 AI 预问诊"
            description="患者未在就诊前进行 AI 预问诊，请按常规流程接诊。"
          />
        </GlassCard>

        <GlassCard v-if="selectedPatient" class="flow-card">
          <div class="pipeline-bar">
            <ElButton type="primary" :loading="aiPipelineLoading" @click="runFullPipeline">一键运行 AI 流水线</ElButton>
            <ElButton :loading="aiPipelineLoading" @click="runW1">W1 结构化</ElButton>
            <ElButton :loading="aiPipelineLoading" @click="runW2">W2 推荐检查</ElButton>
            <ElButton :loading="aiPipelineLoading" @click="runW2b">W2b 模拟结果</ElButton>
            <ElButton :loading="aiPipelineLoading" @click="runW3">W3 结果分析</ElButton>
            <ElButton :loading="aiPipelineLoading" @click="runW4">W4 诊断</ElButton>
          </div>
          <ElTabs v-model="activeTab">
            <ElTabPane label="病历首页" name="record">
              <div class="w1-panel">
                <ElSelect v-model="w1InputMode" style="max-width: 220px">
                  <ElOption label="使用预问诊（B）" value="pre_consultation" />
                  <ElOption label="患者长文本/语音转写" value="long_text" />
                  <ElOption label="医生按字段填写" value="doctor_form" />
                </ElSelect>
                <ElInput
                  v-if="w1InputMode === 'long_text'"
                  v-model="w1LongText"
                  type="textarea"
                  :rows="3"
                  placeholder="粘贴患者口述或语音转写长文本……"
                />
              </div>
              <ElForm label-position="top" class="form-grid">
                <ElFormItem label="主诉">
                  <ElInput v-model="recordForm.readme" />
                </ElFormItem>
                <ElFormItem label="现病史">
                  <ElInput v-model="recordForm.present" type="textarea" :rows="3" />
                </ElFormItem>
                <ElFormItem label="现病治疗情况">
                  <ElInput v-model="recordForm.presentTreat" type="textarea" :rows="2" />
                </ElFormItem>
                <ElFormItem label="既往史">
                  <ElInput v-model="recordForm.history" type="textarea" :rows="2" />
                </ElFormItem>
                <ElFormItem label="过敏史">
                  <ElInput v-model="recordForm.allergy" />
                </ElFormItem>
                <ElFormItem label="体格检查">
                  <ElInput v-model="recordForm.physique" type="textarea" :rows="2" />
                </ElFormItem>
                <ElFormItem label="检查/检验建议">
                  <ElInput v-model="recordForm.proposal" type="textarea" :rows="2" />
                </ElFormItem>
                <ElFormItem label="初步诊断">
                  <ElSelect v-model="recordForm.diseaseIds" multiple filterable placeholder="选择疾病">
                    <ElOption v-for="item in diseases" :key="item.id" :label="`${item.diseaseName} ${item.diseaseIcd || ''}`" :value="item.id" />
                  </ElSelect>
                </ElFormItem>
              </ElForm>
              <div class="actions">
                <ElButton type="primary" @click="saveMedicalRecord">保存病历</ElButton>
                <ElButton @click="recordForm.present = selectedPatient?.aiConsultSummary?.aiSummary || recordForm.present">填入 AI 摘要</ElButton>
              </div>
            </ElTabPane>

            <ElTabPane label="检查/检验/处置申请" name="requests">
              <div class="split-grid">
                <section>
                  <div class="inline-tools">
                    <ElInput v-model="technologyKeyword" placeholder="搜索项目（名称或编码）" @keyup.enter="searchTechnologies" />
                    <ElButton @click="searchTechnologies">搜索</ElButton>
                  </div>
                  <ElForm label-position="top">
                    <ElFormItem label="项目">
                      <ElSelect v-model="requestDraft.medicalTechnologyId" filterable placeholder="选择医技项目">
                        <ElOption v-for="item in technologies" :key="item.id" :label="technologyOptionLabel(item)" :value="item.id" />
                      </ElSelect>
                    </ElFormItem>
                    <ElFormItem label="目的要求">
                      <ElInput v-model="requestDraft.info" />
                    </ElFormItem>
                    <ElFormItem label="部位/标本">
                      <ElInput v-model="requestDraft.position" />
                    </ElFormItem>
                    <ElFormItem label="备注">
                      <ElInput v-model="requestDraft.remark" />
                    </ElFormItem>
                  </ElForm>
                  <div class="actions">
                    <ElButton @click="addTechnologyToBasket">加入申请篮</ElButton>
                    <ElButton type="primary" @click="submitTechnologyRequest">提交申请</ElButton>
                  </div>
                </section>
                <section>
                  <h3>W2 初步判断</h3>
                  <ElAlert
                    v-if="w2Output?.preliminaryAssessment"
                    type="info"
                    :closable="false"
                    :title="w2Output.preliminaryAssessment"
                  />
                  <h3>AI 推荐</h3>
                  <ElEmpty v-if="examSuggestions.length === 0 && !w2Output?.recommendedExaminations?.length" description="暂无 AI 推荐，可运行 W2" />
                  <ElCard
                    v-for="item in w2Output?.recommendedExaminations || []"
                    :key="`w2-${item.techId}`"
                    class="mini-card"
                  >
                    <strong>{{ item.techName }}</strong>
                    <p>{{ item.reason }}</p>
                  </ElCard>
                  <ElCard v-for="item in examSuggestions" :key="String(item.id)" class="mini-card">
                    <strong>{{ item.techName }}</strong>
                    <p>{{ item.suggestReason }}</p>
                  </ElCard>
                  <h3>申请篮</h3>
                  <ElTable :data="requestBasket">
                    <ElTableColumn prop="techName" label="项目" />
                    <ElTableColumn label="类型" width="80">
                      <template #default="{ row }">{{ TECH_TYPE_LABEL[row.techType as TechType] }}</template>
                    </ElTableColumn>
                    <ElTableColumn prop="position" label="部位" />
                    <ElTableColumn prop="info" label="目的" />
                  </ElTable>
                </section>
              </div>
            </ElTabPane>

            <ElTabPane label="结果查看" name="results">
              <ElAlert
                v-if="w3Output?.overallAnalysis"
                class="ai-summary"
                type="success"
                show-icon
                :closable="false"
                title="W3 综合初步分析（非最终诊断）"
                :description="w3Output.overallAnalysis"
              />
              <div v-if="w3Output?.examSummaries?.length" class="w3-summaries">
                <ElCard v-for="(item, idx) in w3Output.examSummaries" :key="idx" class="mini-card">
                  <strong>{{ item.techName }}</strong>
                  <p>{{ item.interpretation }}</p>
                  <ElTag size="small">{{ item.riskLevel }}</ElTag>
                </ElCard>
              </div>
              <h3>检查结果</h3>
              <ElTable :data="checkResults">
                <ElTableColumn prop="techName" label="项目" />
                <ElTableColumn prop="checkState" label="状态" />
                <ElTableColumn prop="checkResult" label="结果" />
                <ElTableColumn label="AI 分析">
                  <template #default="{ row }">
                    <ElTag v-if="row.aiAnalysis" type="success">{{ row.aiAnalysis.riskLevel || '已分析' }}</ElTag>
                    <span v-else>未生成</span>
                  </template>
                </ElTableColumn>
              </ElTable>
              <h3>检验结果</h3>
              <ElTable :data="inspectionResults">
                <ElTableColumn prop="techName" label="项目" />
                <ElTableColumn prop="inspectionState" label="状态" />
                <ElTableColumn prop="inspectionResult" label="结果" />
                <ElTableColumn label="AI 分析">
                  <template #default="{ row }">
                    <ElTag v-if="row.aiAnalysis" type="success">{{ row.aiAnalysis.riskLevel || '已分析' }}</ElTag>
                    <span v-else>未生成</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="110">
                  <template #default="{ row }">
                    <ElButton
                      link
                      type="primary"
                      size="small"
                      :disabled="!canExportInspectionPdf(row)"
                      :loading="exporting"
                      @click="handleExportInspectionPdf(row)"
                    >
                      导出 PDF
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </ElTabPane>

            <ElTabPane label="门诊确诊" name="diagnosis">
              <div class="split-grid">
                <ElForm label-position="top">
                  <ElFormItem label="确诊疾病">
                    <ElSelect v-model="diagnosisForm.diseaseIds" multiple filterable placeholder="选择确诊疾病">
                      <ElOption v-for="item in diseases" :key="item.id" :label="`${item.diseaseName} ${item.diseaseIcd || ''}`" :value="item.id" />
                    </ElSelect>
                  </ElFormItem>
                  <ElFormItem label="诊断结果">
                    <ElInput v-model="diagnosisForm.diagnosis" :placeholder="selectedDiseaseNames || '填写诊断结果'" />
                  </ElFormItem>
                  <ElFormItem label="处理意见">
                    <ElInput v-model="diagnosisForm.cure" type="textarea" :rows="3" />
                  </ElFormItem>
                  <ElFormItem label="注意事项">
                    <ElInput v-model="diagnosisForm.careful" type="textarea" :rows="2" />
                  </ElFormItem>
                  <ElButton type="primary" @click="saveDiagnosis">保存确诊</ElButton>
                </ElForm>
                <section>
                  <ElAlert
                    v-if="w4Output?.primaryDiagnosis"
                    type="warning"
                    :closable="false"
                    show-icon
                    :title="`${w4Output.primaryDiagnosis.diseaseName}（${w4Output.primaryDiagnosis.probability ?? '-'}%）`"
                    :description="w4Output.primaryDiagnosis.diagnosisBasis"
                  />
                  <h3>AI 诊断推荐</h3>
                  <ElEmpty v-if="diagnosisSuggestions.length === 0" description="暂无 AI 诊断推荐，可运行 W4" />
                  <ElCard v-for="item in diagnosisSuggestions" :key="String(item.id)" class="mini-card">
                    <strong>{{ item.diseaseName }}（{{ item.probability || '-' }}%）</strong>
                    <p>{{ item.diagnosisBasis }}</p>
                    <ElButton size="small" @click="adoptDiagnosisSuggestion(item)">采纳</ElButton>
                  </ElCard>
                </section>
              </div>
            </ElTabPane>

            <ElTabPane label="开立处方" name="prescription">
              <div class="split-grid">
                <section>
                  <div class="inline-tools">
                    <ElInput v-model="drugKeyword" placeholder="搜索药品" @keyup.enter="searchDrugs" />
                    <ElButton @click="searchDrugs">搜索</ElButton>
                  </div>
                  <ElForm label-position="top">
                    <ElFormItem label="药品">
                      <ElSelect v-model="prescriptionDraft.drugId" filterable placeholder="选择药品">
                        <ElOption v-for="item in drugs" :key="item.id" :label="`${item.drugName} / ${item.drugPrice}元`" :value="item.id" />
                      </ElSelect>
                    </ElFormItem>
                    <ElFormItem label="用法用量">
                      <ElInput v-model="prescriptionDraft.drugUsage" placeholder="如：口服，一次1片，一日2次" />
                    </ElFormItem>
                    <ElFormItem label="数量">
                      <ElInputNumber v-model="prescriptionDraft.drugNumber" :min="1" />
                    </ElFormItem>
                  </ElForm>
                  <div class="actions">
                    <ElButton @click="addDrugToBasket">加入处方篮</ElButton>
                    <ElButton type="primary" @click="submitPrescription">开立处方</ElButton>
                  </div>
                </section>
                <section>
                  <h3>处方篮</h3>
                  <ElTable :data="prescriptionBasket">
                    <ElTableColumn prop="drugName" label="药品" />
                    <ElTableColumn prop="drugUsage" label="用法" />
                    <ElTableColumn prop="drugNumber" label="数量" />
                  </ElTable>
                  <ElFormItem label="确诊病名（提交给药房/B侧）">
                    <ElInput v-model="confirmedDiagnosisForRx" placeholder="来自 W4 或确诊 Tab" />
                  </ElFormItem>
                  <h3>已开处方</h3>
                  <ElTable :data="prescriptions">
                    <ElTableColumn prop="drugName" label="药品" />
                    <ElTableColumn prop="drugUsage" label="用法" />
                    <ElTableColumn prop="drugState" label="状态" />
                  </ElTable>
                </section>
              </div>
            </ElTabPane>
          </ElTabs>
        </GlassCard>

        <GlassCard v-else class="empty-card">
          <ElEmpty description="请选择一个待诊患者开始接诊" />
        </GlassCard>
      </main>
    </section>
  </div>

  <div class="lab-report-print-host" aria-hidden="true">
    <LabReportPrintSheet ref="printSheetRef" :context="exportContext" />
  </div>
</template>

<style scoped>
.workspace-grid {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: var(--space-4);
}

.patient-panel,
.patient-summary,
.flow-card,
.empty-card {
  padding: var(--space-5);
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.panel-heading h2,
.flow-card h3 {
  font-size: 18px;
}

.panel-heading p,
.mini-card p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.6;
}

.patient-list {
  display: grid;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.patient-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.patient-item span {
  color: var(--color-text-muted);
}

.patient-item .status-tag {
  grid-column: 1 / -1;
  width: fit-content;
}

.patient-item.is-active {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.work-panel {
  min-width: 0;
}

.patient-summary {
  margin-block-end: var(--space-4);
}

.ai-summary {
  margin-block-start: var(--space-4);
}

.ai-consult-card {
  margin-block-start: var(--space-4);
  padding: var(--space-4);
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border: 1px solid #7dd3fc;
  border-radius: var(--radius-md);
}

.ai-consult-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  font-size: 15px;
  color: #0369a1;
}

.ai-consult-icon {
  font-size: 20px;
}

.ai-consult-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}

.ai-consult-item {
  background: white;
  border: 1px solid #e0f2fe;
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
}

.ai-consult-item.full {
  grid-column: 1 / -1;
}

.ai-consult-item label {
  display: block;
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: 2px;
}

.ai-consult-item p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text);
  white-space: pre-wrap;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--space-4);
}

.form-grid :deep(.el-form-item:nth-child(2)),
.form-grid :deep(.el-form-item:nth-child(3)),
.form-grid :deep(.el-form-item:nth-child(4)),
.form-grid :deep(.el-form-item:nth-child(6)),
.form-grid :deep(.el-form-item:nth-child(7)) {
  grid-column: 1 / -1;
}

.split-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.8fr);
  gap: var(--space-5);
}

.inline-tools {
  display: flex;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.mini-card {
  margin-block: var(--space-3);
}

.pipeline-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-block-end: var(--space-4);
}

.w1-panel {
  display: grid;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.w3-summaries {
  margin-block-end: var(--space-4);
}

.flow-card :deep(.el-tabs__content) {
  padding-block-start: var(--space-4);
}

.lab-report-print-host {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  visibility: hidden;
}

@media (max-width: 1080px) {
  .workspace-grid,
  .split-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

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
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { physicianApi, type Disease, type Drug, type MedicalRecord, type MedicalTechnology, type PhysicianPatient } from '@/shared/api/modules/physician'

type RequestKind = 'check' | 'inspection' | 'disposal'

interface RequestDraft {
  medicalTechnologyId?: number
  info: string
  position: string
  remark: string
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

const requestKind = ref<RequestKind>('check')
const technologyKeyword = ref('')
const technologies = ref<MedicalTechnology[]>([])
const requestDraft = reactive<RequestDraft>({ info: '', position: '', remark: '' })
const requestBasket = ref<Array<RequestDraft & { techName: string }>>([])

const diseases = ref<Disease[]>([])
const checkResults = ref<Awaited<ReturnType<typeof physicianApi.checkResults>>>([])
const inspectionResults = ref<Awaited<ReturnType<typeof physicianApi.inspectionResults>>>([])
const examSuggestions = ref<Record<string, unknown>[]>([])
const diagnosisSuggestions = ref<Record<string, unknown>[]>([])

const drugKeyword = ref('')
const drugs = ref<Drug[]>([])
const prescriptionDraft = reactive<PrescriptionDraft>({ drugUsage: '', drugNumber: 1 })
const prescriptionBasket = ref<Array<PrescriptionDraft & { drugName: string; drugPrice: number }>>([])
const prescriptions = ref<Awaited<ReturnType<typeof physicianApi.prescriptions>>>([])
const aiPrescriptionReview = ref<Record<string, unknown> | null>(null)

const selectedRegisterId = computed(() => selectedPatient.value?.registerId)
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

async function searchTechnologies() {
  technologies.value = await physicianApi.medicalTechnologies(requestKind.value, technologyKeyword.value)
}

function addTechnologyToBasket() {
  const tech = technologies.value.find((item) => item.id === requestDraft.medicalTechnologyId)
  if (!tech) {
    ElMessage.warning('请选择医技项目')
    return
  }
  requestBasket.value.push({ ...requestDraft, medicalTechnologyId: tech.id, techName: tech.techName })
  requestDraft.medicalTechnologyId = undefined
  requestDraft.info = ''
  requestDraft.position = ''
  requestDraft.remark = ''
}

async function submitTechnologyRequest() {
  if (!selectedRegisterId.value || requestBasket.value.length === 0) {
    ElMessage.warning('请先选择患者并添加申请项目')
    return
  }
  const items = requestBasket.value.map((item) => {
    if (requestKind.value === 'check') {
      return { medicalTechnologyId: item.medicalTechnologyId, checkInfo: item.info, checkPosition: item.position, checkRemark: item.remark }
    }
    if (requestKind.value === 'inspection') {
      return { medicalTechnologyId: item.medicalTechnologyId, inspectionInfo: item.info, inspectionPosition: item.position, inspectionRemark: item.remark }
    }
    return { medicalTechnologyId: item.medicalTechnologyId, disposalInfo: item.info, disposalPosition: item.position, disposalRemark: item.remark }
  })
  const payload = { registerId: selectedRegisterId.value, items }
  if (requestKind.value === 'check') {
    await physicianApi.createCheckRequest(payload)
  } else if (requestKind.value === 'inspection') {
    await physicianApi.createInspectionRequest(payload)
  } else {
    await physicianApi.createDisposalRequest(payload)
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
  const result = await physicianApi.createPrescription({
    registerId: selectedRegisterId.value,
    items: prescriptionBasket.value.map((item) => ({ drugId: item.drugId, drugUsage: item.drugUsage, drugNumber: String(item.drugNumber) })),
  })
  aiPrescriptionReview.value = result.aiReviewResult || null
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

watch(selectedRegisterId, () => {
  void loadPatientContext()
})

watch(requestKind, () => {
  requestBasket.value = []
  void searchTechnologies()
})

onMounted(async () => {
  await Promise.all([loadPatients(), searchTechnologies(), searchDrugs()])
})
</script>

<template>
  <div class="physician-workspace u-page-grid">
    <PageHeader
      title="医生工作站"
      description="围绕接诊、病历、检查检验申请、结果查看、确诊和开方的角色A主流程。AI 内容作为辅助信息展示，不阻塞传统诊疗。"
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
            @click="selectedPatient = patient"
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
          <ElAlert
            v-if="selectedPatient.aiConsultSummary"
            class="ai-summary"
            type="success"
            show-icon
            :closable="false"
            title="AI 预问诊摘要"
            :description="selectedPatient.aiConsultSummary.aiSummary || selectedPatient.aiConsultSummary.chiefComplaint"
          />
        </GlassCard>

        <GlassCard v-if="selectedPatient" class="flow-card">
          <ElTabs v-model="activeTab">
            <ElTabPane label="病历首页" name="record">
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
                    <ElSelect v-model="requestKind">
                      <ElOption label="检查" value="check" />
                      <ElOption label="检验" value="inspection" />
                      <ElOption label="处置" value="disposal" />
                    </ElSelect>
                    <ElInput v-model="technologyKeyword" placeholder="搜索项目" @keyup.enter="searchTechnologies" />
                    <ElButton @click="searchTechnologies">搜索</ElButton>
                  </div>
                  <ElForm label-position="top">
                    <ElFormItem label="项目">
                      <ElSelect v-model="requestDraft.medicalTechnologyId" filterable placeholder="选择医技项目">
                        <ElOption v-for="item in technologies" :key="item.id" :label="`${item.techName} / ${item.techPrice}元`" :value="item.id" />
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
                  <h3>AI 推荐</h3>
                  <ElEmpty v-if="examSuggestions.length === 0" description="暂无 AI 推荐，可先手动开立申请" />
                  <ElCard v-for="item in examSuggestions" :key="String(item.id)" class="mini-card">
                    <strong>{{ item.techName }}</strong>
                    <p>{{ item.suggestReason }}</p>
                  </ElCard>
                  <h3>申请篮</h3>
                  <ElTable :data="requestBasket">
                    <ElTableColumn prop="techName" label="项目" />
                    <ElTableColumn prop="position" label="部位" />
                    <ElTableColumn prop="info" label="目的" />
                  </ElTable>
                </section>
              </div>
            </ElTabPane>

            <ElTabPane label="结果查看" name="results">
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
                  <h3>AI 诊断推荐</h3>
                  <ElEmpty v-if="diagnosisSuggestions.length === 0" description="暂无 AI 诊断推荐" />
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
                  <ElAlert
                    v-if="aiPrescriptionReview"
                    class="ai-summary"
                    type="warning"
                    :closable="false"
                    show-icon
                    title="AI 处方审核"
                    :description="String(aiPrescriptionReview.riskDetails || aiPrescriptionReview.reviewResult || '处方通过基础审核')"
                  />
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

.flow-card :deep(.el-tabs__content) {
  padding-block-start: var(--space-4);
}

@media (max-width: 1080px) {
  .workspace-grid,
  .split-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>

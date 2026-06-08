<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElEmpty,
  ElIcon,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import {
  Cpu,
  DocumentAdd,
  InfoFilled,
  MagicStick,
  Odometer,
  Picture,
  Position,
  Search,
  ShoppingCart,
  WarningFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import {
  physicianApi,
  type MedicalRecord,
  type MedicalTechnology,
  type W2Output,
  type W2RecommendedExamination,
} from '@/shared/api/modules/physician'
import { useEncounterStore } from '@/app/stores/encounter'
import GlassCard from '@/shared/components/GlassCard.vue'
import ClinicalContextPanel from '../components/ClinicalContextPanel.vue'
import PhysicianStepLayout from '../layouts/PhysicianStepLayout.vue'

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

const encounterStore = useEncounterStore()
const registerId = computed(() => encounterStore.registerId)

const technologyKeyword = ref('')
const technologies = ref<MedicalTechnology[]>([])
const requestDraft = reactive<RequestDraft>({ info: '', position: '', remark: '' })
const requestBasket = ref<BasketItem[]>([])

const aiLoading = ref(false)
const w2Output = ref<W2Output | null>(null)

const contextLoading = ref(false)
const medicalRecord = ref<MedicalRecord | null>(null)

const hasW2Assessment = computed(
  () => Boolean(w2Output.value?.preliminaryAssessment || w2Output.value?.notRecommendedNote),
)

function recommendationIcon(item: W2RecommendedExamination): Component {
  const name = item.techName || ''
  if (/血|化验|检验|蛋白|常规/.test(name)) return Odometer
  if (/CT|MRI|X|超声|影像|片/.test(name)) return Picture
  return Odometer
}

async function loadClinicalContext() {
  if (!registerId.value) {
    medicalRecord.value = null
    return
  }
  contextLoading.value = true
  try {
    medicalRecord.value = await physicianApi.medicalRecord(registerId.value)
  } finally {
    contextLoading.value = false
  }
}

function technologyOptionLabel(item: MedicalTechnology) {
  return `${item.techName} / ${TECH_TYPE_LABEL[item.techType]} / ${item.techPrice}元`
}

async function searchTechnologies() {
  technologies.value = await physicianApi.medicalTechnologies(undefined, technologyKeyword.value || undefined)
}

function addTechnologyToBasket() {
  const technology = technologies.value.find((item) => item.id === requestDraft.medicalTechnologyId)
  if (!technology) return
  requestBasket.value.push({
    medicalTechnologyId: technology.id,
    techName: technology.techName,
    techType: technology.techType,
    info: requestDraft.info,
    position: requestDraft.position,
    remark: requestDraft.remark,
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
  if (!registerId.value) return
  if (requestBasket.value.length === 0) return

  const register = registerId.value
  const byType = (type: TechType) => requestBasket.value.filter((item) => item.techType === type)

  const checkItems = byType('check')
  const inspectionItems = byType('inspection')
  const disposalItems = byType('disposal')

  if (checkItems.length) {
    await physicianApi.createCheckRequest({ registerId: register, items: toRequestItems(checkItems, 'check') })
  }
  if (inspectionItems.length) {
    await physicianApi.createInspectionRequest({ registerId: register, items: toRequestItems(inspectionItems, 'inspection') })
  }
  if (disposalItems.length) {
    await physicianApi.createDisposalRequest({ registerId: register, items: toRequestItems(disposalItems, 'disposal') })
  }

  requestBasket.value = []
  ElMessage.success('申请已提交')
}

function addW2RecommendationToBasket(item: W2RecommendedExamination) {
  if (!item.techId) return
  if (requestBasket.value.some((b) => b.medicalTechnologyId === item.techId)) {
    ElMessage.warning('该项目已在申请篮中')
    return
  }
  const techType = item.techType as TechType
  if (techType !== 'check' && techType !== 'inspection' && techType !== 'disposal') {
    ElMessage.warning('无法识别项目类型，请手工选择')
    return
  }
  requestBasket.value.push({
    medicalTechnologyId: item.techId,
    techName: item.techName,
    techType,
    info: item.purpose?.trim() || item.reason?.trim() || '',
    position: item.position?.trim() || '',
    remark: item.remark?.trim() || '',
  })
  ElMessage.success(`已加入申请篮：${item.techName}`)
}

async function runW2() {
  if (!registerId.value) return
  aiLoading.value = true
  try {
    w2Output.value = await physicianApi.aiW2(registerId.value)
    ElMessage.success('W2 检查推荐已生成')
  } finally {
    aiLoading.value = false
  }
}

watch(registerId, () => {
  void loadClinicalContext()
})

onMounted(() => {
  void searchTechnologies()
  void loadClinicalContext()
})
</script>

<template>
  <PhysicianStepLayout
    group-label="门诊诊疗"
    title="开验检查单"
    prev-path="/physician/record"
    next-path="/physician/results"
  >
    <ClinicalContextPanel :record="medicalRecord" :loading="contextLoading" />

    <div class="orders-page">
      <div class="orders-page__grid">
        <GlassCard class="orders-page__card orders-page__card--form">
          <header class="orders-page__card-head">
            <div class="orders-page__card-logo" aria-hidden="true">
              <ElIcon :size="24"><DocumentAdd /></ElIcon>
            </div>
            <div>
              <h2 class="orders-page__card-title">医技申请</h2>
              <p class="orders-page__card-subtitle">查询项目并填写申请信息</p>
            </div>
          </header>

          <section class="orders-section">
            <h3 class="orders-section__title">项目查询</h3>
            <div class="orders-search">
              <ElInput
                v-model="technologyKeyword"
                placeholder="搜索项目（名称或编码）"
                class="orders-search__input"
                clearable
                @keyup.enter="searchTechnologies"
              >
                <template #prefix>
                  <ElIcon class="orders-search__icon"><Search /></ElIcon>
                </template>
              </ElInput>
              <ElButton class="orders-search__btn" @click="searchTechnologies">搜索</ElButton>
            </div>
          </section>

          <section class="orders-section orders-section--form">
            <h3 class="orders-section__title">申请信息</h3>

            <div class="orders-form">
              <div class="orders-form__row">
                <label class="orders-form__label" for="orders-tech-select">
                  项目<span class="orders-form__required">*</span>
                </label>
                <div class="orders-form__control">
                  <ElSelect
                    id="orders-tech-select"
                    v-model="requestDraft.medicalTechnologyId"
                    filterable
                    placeholder="选择医技项目"
                    class="orders-form__select"
                  >
                    <ElOption
                      v-for="item in technologies"
                      :key="item.id"
                      :label="technologyOptionLabel(item)"
                      :value="item.id"
                    />
                  </ElSelect>
                </div>
              </div>

              <div class="orders-form__row">
                <label class="orders-form__label" for="orders-info">
                  目的要求
                  <ElIcon class="orders-form__hint" :size="14"><InfoFilled /></ElIcon>
                </label>
                <div class="orders-form__control">
                  <ElInput
                    id="orders-info"
                    v-model="requestDraft.info"
                    placeholder="请输入目的要求"
                    class="orders-form__input"
                  />
                </div>
              </div>

              <div class="orders-form__row">
                <label class="orders-form__label" for="orders-position">
                  部位/标本
                  <ElIcon class="orders-form__hint" :size="14"><InfoFilled /></ElIcon>
                </label>
                <div class="orders-form__control">
                  <ElInput
                    id="orders-position"
                    v-model="requestDraft.position"
                    placeholder="请输入部位或标本"
                    class="orders-form__input"
                  />
                </div>
              </div>

              <div class="orders-form__row">
                <label class="orders-form__label" for="orders-remark">
                  备注
                  <ElIcon class="orders-form__hint" :size="14"><InfoFilled /></ElIcon>
                </label>
                <div class="orders-form__control">
                  <ElInput
                    id="orders-remark"
                    v-model="requestDraft.remark"
                    placeholder="请输入备注信息（选填）"
                    class="orders-form__input"
                  />
                </div>
              </div>
            </div>

            <div class="orders-form__actions">
              <ElButton class="orders-btn orders-btn--outline" @click="addTechnologyToBasket">
                <ElIcon class="orders-btn__icon"><ShoppingCart /></ElIcon>
                加入申请篮
              </ElButton>
              <ElButton type="primary" class="orders-btn orders-btn--submit" @click="submitTechnologyRequest">
                <ElIcon class="orders-btn__icon"><Position /></ElIcon>
                提交申请
              </ElButton>
            </div>
          </section>

          <section v-if="requestBasket.length" class="orders-section orders-section--basket">
            <h3 class="orders-section__title">申请篮</h3>
            <ElTable :data="requestBasket" class="orders-basket-table" stripe>
              <ElTableColumn prop="techName" label="项目" min-width="120" />
              <ElTableColumn label="类型" width="80">
                <template #default="{ row }">{{ TECH_TYPE_LABEL[row.techType as TechType] }}</template>
              </ElTableColumn>
              <ElTableColumn prop="position" label="部位/标本" min-width="100" />
              <ElTableColumn prop="info" label="目的" min-width="120" />
            </ElTable>
          </section>
        </GlassCard>

        <GlassCard class="orders-page__card orders-page__card--ai">
          <header class="orders-ai__header">
            <div class="orders-ai__intro">
              <div class="orders-ai__logo" aria-hidden="true">
                <ElIcon :size="22"><MagicStick /></ElIcon>
              </div>
              <h2 class="orders-ai__title">AI 辅助</h2>
            </div>
            <ElButton
              class="orders-ai__run-btn"
              :loading="aiLoading"
              @click="runW2"
            >
              <ElIcon class="orders-btn__icon"><MagicStick /></ElIcon>
              运行 W2（AI 推荐）
            </ElButton>
          </header>

          <section class="orders-section">
            <h3 class="orders-section__title">W2 初步判断</h3>
            <div v-if="hasW2Assessment" class="orders-ai-assessments">
              <div
                v-if="w2Output?.preliminaryAssessment"
                class="orders-ai-box orders-ai-box--info"
              >
                <ElIcon class="orders-ai-box__icon" :size="20"><Cpu /></ElIcon>
                <p class="orders-ai-box__text">{{ w2Output.preliminaryAssessment }}</p>
              </div>
              <div
                v-if="w2Output?.notRecommendedNote"
                class="orders-ai-box orders-ai-box--warning"
              >
                <ElIcon class="orders-ai-box__icon" :size="20"><WarningFilled /></ElIcon>
                <p class="orders-ai-box__text">{{ w2Output.notRecommendedNote }}</p>
              </div>
            </div>
            <p v-else class="orders-ai-placeholder">运行 W2 后，AI 将基于病历给出初步判断与注意事项</p>
          </section>

          <section class="orders-section">
            <h3 class="orders-section__title">AI 推荐</h3>
            <ElEmpty
              v-if="!w2Output?.recommendedExaminations?.length"
              description="暂无 AI 推荐，可运行 W2"
              class="orders-ai-empty"
            />
            <ul v-else class="orders-rec-list">
              <li
                v-for="item in w2Output?.recommendedExaminations || []"
                :key="`w2-${item.techId}`"
                class="orders-rec-card"
              >
                <div class="orders-rec-card__head">
                  <div class="orders-rec-card__icon-wrap" aria-hidden="true">
                    <ElIcon :size="22"><component :is="recommendationIcon(item)" /></ElIcon>
                  </div>
                  <div class="orders-rec-card__meta">
                    <div class="orders-rec-card__title-row">
                      <strong class="orders-rec-card__name">{{ item.techName }}</strong>
                      <ElTag size="small" class="orders-rec-card__priority">P{{ item.priority }}</ElTag>
                    </div>
                    <p class="orders-rec-card__reason">{{ item.reason }}</p>
                  </div>
                </div>
                <ElButton
                  type="primary"
                  class="orders-rec-card__add"
                  @click="addW2RecommendationToBasket(item)"
                >
                  + 加入申请篮
                </ElButton>
              </li>
            </ul>
          </section>

          <section v-if="w2Output?.unmatchedSuggestions?.length" class="orders-section">
            <h3 class="orders-section__title">需人工核对</h3>
            <div class="orders-ai-assessments">
              <div
                v-for="(item, index) in w2Output.unmatchedSuggestions"
                :key="`unmatched-${index}`"
                class="orders-ai-box orders-ai-box--warning"
              >
                <ElIcon class="orders-ai-box__icon" :size="20"><WarningFilled /></ElIcon>
                <p class="orders-ai-box__text">{{ item.name }}：{{ item.reason }}</p>
              </div>
            </div>
          </section>
        </GlassCard>
      </div>
    </div>
  </PhysicianStepLayout>
</template>

<style scoped>
.orders-page__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: var(--space-6);
  align-items: start;
}

.orders-page__card {
  padding: var(--space-5);
}

.orders-page__card-head {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-block-end: var(--space-5);
  padding-block-end: var(--space-5);
  border-block-end: 1px solid var(--color-border);
}

.orders-page__card-logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  inline-size: 48px;
  block-size: 48px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-success) 22%, var(--color-primary-soft));
  color: var(--color-success);
}

.orders-page__card-title {
  margin: 0;
  font-size: var(--font-size-lg, 1.125rem);
  font-weight: 700;
  line-height: 1.3;
  color: var(--color-text);
}

.orders-page__card-subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-sm, 0.875rem);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.orders-section + .orders-section {
  margin-block-start: var(--space-5);
  padding-block-start: var(--space-5);
  border-block-start: 1px solid var(--color-border);
}

.orders-section__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-base, 1rem);
  font-weight: 650;
  color: var(--color-text);
}

.orders-section__title::before {
  content: '';
  flex-shrink: 0;
  inline-size: 3px;
  block-size: 16px;
  border-radius: 2px;
  background: var(--color-success);
}

.orders-search {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.orders-search__input {
  flex: 1;
  min-width: 0;
}

.orders-search__input :deep(.el-input__wrapper) {
  padding-inline: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-control);
  box-shadow: none;
  border: 1px solid var(--color-border-strong);
}

.orders-search__input :deep(.el-input__wrapper:hover),
.orders-search__input :deep(.el-input__wrapper.is-focus) {
  border-color: color-mix(in srgb, var(--color-success) 45%, var(--color-border-strong));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-success) 12%, transparent);
}

.orders-search__icon {
  color: var(--color-text-soft);
}

.orders-search__btn {
  flex-shrink: 0;
  padding-inline: var(--space-5);
  border-radius: var(--radius-md);
  border: 1px solid color-mix(in srgb, var(--color-success) 55%, var(--color-border));
  color: var(--color-success);
  background: var(--color-surface-strong);
  box-shadow: none;
}

.orders-search__btn:hover {
  border-color: var(--color-success);
  color: var(--color-success);
  background: color-mix(in srgb, var(--color-success) 8%, var(--color-surface-strong));
}

.orders-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.orders-form__row {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr);
  gap: var(--space-4);
  align-items: center;
}

.orders-form__label {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-sm, 0.875rem);
  font-weight: 600;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.orders-form__required {
  color: var(--color-danger);
  margin-inline-start: 2px;
}

.orders-form__hint {
  color: var(--color-text-soft);
}

.orders-form__select,
.orders-form__input {
  width: 100%;
}

.orders-form__select :deep(.el-select__wrapper),
.orders-form__input :deep(.el-input__wrapper) {
  padding-inline: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-control);
  box-shadow: none;
  border: 1px solid var(--color-border-strong);
  min-height: 40px;
}

.orders-form__select :deep(.el-select__wrapper:hover),
.orders-form__select :deep(.el-select__wrapper.is-focused),
.orders-form__input :deep(.el-input__wrapper:hover),
.orders-form__input :deep(.el-input__wrapper.is-focus) {
  border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-border-strong));
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.orders-form__actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-block-start: var(--space-5);
  padding-block-start: var(--space-4);
}

.orders-btn {
  padding-inline: var(--space-5);
  border-radius: var(--radius-md);
  box-shadow: none;
}

.orders-btn__icon {
  margin-inline-end: var(--space-1);
}

.orders-btn--outline {
  border: 1px solid color-mix(in srgb, var(--color-success) 55%, var(--color-border));
  color: var(--color-success);
  background: var(--color-surface-strong);
}

.orders-btn--outline:hover {
  border-color: var(--color-success);
  color: var(--color-success);
  background: color-mix(in srgb, var(--color-success) 8%, var(--color-surface-strong));
}

.orders-btn--submit {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.orders-btn--submit:hover {
  background: var(--color-primary-strong);
  border-color: var(--color-primary-strong);
}

.orders-basket-table {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.orders-basket-table :deep(.el-table__header th) {
  background: var(--color-table-header);
  font-weight: 600;
  color: var(--color-text-muted);
}

.orders-ai__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-block-end: var(--space-2);
  padding-block-end: var(--space-5);
  border-block-end: 1px solid var(--color-border);
}

.orders-ai__intro {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.orders-ai__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  inline-size: 40px;
  block-size: 40px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-ai) 14%, var(--color-primary-soft));
  color: var(--color-ai);
}

.orders-ai__title {
  margin: 0;
  font-size: var(--font-size-lg, 1.125rem);
  font-weight: 700;
  color: var(--color-text);
}

.orders-ai__run-btn {
  border-radius: var(--radius-md);
  border: 1px solid color-mix(in srgb, var(--color-success) 55%, var(--color-border));
  color: var(--color-success);
  background: var(--color-surface-strong);
  box-shadow: none;
}

.orders-ai__run-btn:hover {
  border-color: var(--color-success);
  color: var(--color-success);
  background: color-mix(in srgb, var(--color-success) 8%, var(--color-surface-strong));
}

.orders-ai-assessments {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.orders-ai-box {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  line-height: 1.75;
}

.orders-ai-box__icon {
  flex-shrink: 0;
  margin-block-start: 2px;
}

.orders-ai-box__text {
  margin: 0;
  font-size: var(--font-size-sm, 0.875rem);
}

.orders-ai-box--info {
  border: 1px solid color-mix(in srgb, var(--color-primary) 22%, var(--color-border));
  background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface-strong));
  color: var(--color-text);
}

.orders-ai-box--info .orders-ai-box__icon {
  color: var(--color-primary);
}

.orders-ai-box--warning {
  border: 1px solid color-mix(in srgb, var(--color-warning) 28%, var(--color-border));
  background: color-mix(in srgb, var(--color-warning) 10%, var(--color-surface-strong));
  color: var(--color-text);
}

.orders-ai-box--warning .orders-ai-box__icon {
  color: var(--color-warning);
}

.orders-ai-placeholder {
  margin: 0;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  border: 1px dashed var(--color-border);
  font-size: var(--font-size-sm, 0.875rem);
  color: var(--color-text-muted);
  line-height: 1.65;
  text-align: center;
}

.orders-ai-empty {
  padding-block: var(--space-4);
}

.orders-rec-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.orders-rec-card {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-strong);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.orders-rec-card:hover {
  border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-border));
  box-shadow: var(--shadow-sm);
}

.orders-rec-card__head {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.orders-rec-card__icon-wrap {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  inline-size: 44px;
  block-size: 44px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface));
  color: var(--color-primary);
}

.orders-rec-card__meta {
  flex: 1;
  min-width: 0;
}

.orders-rec-card__title-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.orders-rec-card__name {
  font-size: var(--font-size-base, 1rem);
  font-weight: 650;
  color: var(--color-text);
}

.orders-rec-card__priority {
  border: 0;
  background: color-mix(in srgb, var(--color-success) 16%, var(--color-surface));
  color: var(--color-success);
  font-weight: 700;
}

.orders-rec-card__reason {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm, 0.875rem);
  line-height: 1.65;
  color: var(--color-text-muted);
}

.orders-rec-card__add {
  width: 100%;
  margin-block-start: var(--space-4);
  border-radius: var(--radius-md);
  box-shadow: none;
}

@media (max-width: 960px) {
  .orders-page__grid {
    grid-template-columns: 1fr;
  }

  .orders-form__row {
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }

  .orders-form__label {
    margin-block-end: 0;
  }
}
</style>

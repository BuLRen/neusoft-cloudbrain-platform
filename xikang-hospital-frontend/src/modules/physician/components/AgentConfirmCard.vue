<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ElButton, ElCollapse, ElCollapseItem, ElIcon, ElInput } from 'element-plus'
import { CircleCheck, Close, DocumentChecked } from '@element-plus/icons-vue'
import type { AgentConfirmAction, AgentActionStatus } from '@/shared/types/copilot'
import { MEDICAL_RECORD_FIELD_LABELS } from '@/shared/types/copilot'

const props = defineProps<{
  action: AgentConfirmAction
  status: AgentActionStatus
}>()

const emit = defineEmits<{
  confirm: [payload: Record<string, unknown>]
  dismiss: []
}>()

const editablePayload = reactive<Record<string, unknown>>({})

watch(
  () => props.action.payload,
  (payload) => {
    Object.keys(editablePayload).forEach((key) => delete editablePayload[key])
    const cloned = payload ? JSON.parse(JSON.stringify(payload)) as Record<string, unknown> : {}
    Object.assign(editablePayload, cloned)
  },
  { immediate: true, deep: true },
)

const isMedicalRecord = computed(() => props.action.type === 'commit_medical_record')
const isPreliminary = computed(() => props.action.type === 'commit_preliminary_diagnosis')
const isOrder = computed(() =>
  ['commit_check_requests', 'commit_inspection_requests', 'commit_disposal_requests'].includes(props.action.type),
)
const isDiagnosis = computed(() => props.action.type === 'commit_diagnosis')
const isPrescription = computed(() => props.action.type === 'commit_prescription')
const isArchive = computed(() => props.action.type === 'commit_archive_visit')

const recordFields = computed(() => {
  const keys = ['readme', 'present', 'presentTreat', 'history', 'allergy', 'physique', 'proposal']
  if (props.action.diff && Object.keys(props.action.diff).length) {
    return Object.keys(props.action.diff)
  }
  return keys.filter((key) => editablePayload[key] !== undefined && editablePayload[key] !== null)
})

const recordDiff = computed(() => props.action.diff ?? {})
const recordDiffFields = computed(() => recordFields.value.filter((key) => recordDiff.value[key]))

const orderItems = computed(() => {
  const items = editablePayload.items
  return Array.isArray(items) ? items : []
})

const prescriptionItems = computed(() => {
  const items = editablePayload.items
  return Array.isArray(items) ? items : []
})

function fieldLabel(key: string) {
  return MEDICAL_RECORD_FIELD_LABELS[key] ?? key
}

function updateField(key: string, value: string) {
  editablePayload[key] = value
}

function confirm() {
  emit('confirm', { ...editablePayload })
}
</script>

<template>
  <div class="agent-confirm-card" :class="`is-${status}`">
    <div class="agent-confirm-card__head">
      <span class="agent-confirm-card__icon" aria-hidden="true">
        <ElIcon :size="16"><DocumentChecked /></ElIcon>
      </span>
      <div class="agent-confirm-card__title">
        <strong>{{ action.label }}</strong>
        <p v-if="action.description">{{ action.description }}</p>
      </div>
    </div>

    <p v-if="action.reason" class="agent-confirm-card__reason">{{ action.reason }}</p>

    <div v-if="status === 'pending' || status === 'loading'" class="agent-confirm-card__body">
      <template v-if="isMedicalRecord">
        <div v-if="recordDiffFields.length" class="agent-confirm-card__diff-table">
          <div class="agent-confirm-card__diff-row agent-confirm-card__diff-row--head">
            <span>项目</span>
            <span>修改前</span>
            <span>修改后</span>
          </div>
          <div v-for="key in recordDiffFields" :key="key" class="agent-confirm-card__diff-row">
            <span>{{ fieldLabel(key) }}</span>
            <span>{{ recordDiff[key].before || '空（无）' }}</span>
            <span>{{ recordDiff[key].after || String(editablePayload[key] ?? '') || '空（无）' }}</span>
          </div>
        </div>
        <p v-if="recordDiffFields.length" class="agent-confirm-card__hint">
          请确认变更内容，也可以在下方输入框微调后再提交。
        </p>
        <div v-for="key in recordFields" :key="key" class="agent-confirm-card__field">
          <label>{{ fieldLabel(key) }}</label>
          <p v-if="recordDiff[key] && !recordDiffFields.length" class="agent-confirm-card__diff-before">
            原：{{ recordDiff[key].before || '（空）' }}
          </p>
          <ElInput
            :model-value="String(editablePayload[key] ?? '')"
            type="textarea"
            :rows="2"
            @update:model-value="(v) => updateField(key, v)"
          />
        </div>
        <p v-if="!recordFields.length" class="agent-confirm-card__hint">无可编辑病历字段，请检查 payload</p>
      </template>

      <template v-else-if="isPreliminary">
        <div class="agent-confirm-card__field">
          <label>{{ fieldLabel('preliminaryDiagnosis') }}</label>
          <ElInput
            :model-value="String(editablePayload.preliminaryDiagnosis ?? '')"
            type="textarea"
            :rows="3"
            @update:model-value="(v) => updateField('preliminaryDiagnosis', v)"
          />
        </div>
      </template>

      <template v-else-if="isOrder">
        <ElCollapse>
          <ElCollapseItem :title="`申请项目（${orderItems.length} 项）`" name="items">
            <ul class="agent-confirm-card__list">
              <li v-for="(item, idx) in orderItems" :key="idx">
                {{ (item as Record<string, unknown>).technologyName || (item as Record<string, unknown>).techName || `项目 #${idx + 1}` }}
              </li>
            </ul>
          </ElCollapseItem>
        </ElCollapse>
      </template>

      <template v-else-if="isDiagnosis">
        <div class="agent-confirm-card__field">
          <label>{{ fieldLabel('diagnosis') }}</label>
          <ElInput
            :model-value="String(editablePayload.diagnosis ?? '')"
            @update:model-value="(v) => updateField('diagnosis', v)"
          />
        </div>
        <div class="agent-confirm-card__field">
          <label>{{ fieldLabel('cure') }}</label>
          <ElInput
            :model-value="String(editablePayload.cure ?? '')"
            type="textarea"
            :rows="2"
            @update:model-value="(v) => updateField('cure', v)"
          />
        </div>
        <div class="agent-confirm-card__field">
          <label>{{ fieldLabel('careful') }}</label>
          <ElInput
            :model-value="String(editablePayload.careful ?? '')"
            type="textarea"
            :rows="2"
            @update:model-value="(v) => updateField('careful', v)"
          />
        </div>
      </template>

      <template v-else-if="isPrescription">
        <div class="agent-confirm-card__field">
          <label>{{ fieldLabel('confirmedDiagnosis') }}</label>
          <ElInput
            :model-value="String(editablePayload.confirmedDiagnosis ?? '')"
            @update:model-value="(v) => updateField('confirmedDiagnosis', v)"
          />
        </div>
        <ElCollapse>
          <ElCollapseItem :title="`处方药品（${prescriptionItems.length} 种）`" name="rx">
            <ul class="agent-confirm-card__list">
              <li v-for="(item, idx) in prescriptionItems" :key="idx">
                药品 ID {{ (item as Record<string, unknown>).drugId }} ·
                {{ (item as Record<string, unknown>).drugUsage }} ·
                ×{{ (item as Record<string, unknown>).drugNumber }}
              </li>
            </ul>
          </ElCollapseItem>
        </ElCollapse>
      </template>

      <template v-else-if="isArchive">
        <p class="agent-confirm-card__hint">确认后将归档本次就诊病历并发布给患者查看。</p>
      </template>
    </div>

    <div v-if="status === 'pending' || status === 'loading'" class="agent-confirm-card__actions">
      <ElButton type="primary" size="small" :loading="status === 'loading'" @click="confirm">
        确认提交
      </ElButton>
      <ElButton size="small" :disabled="status === 'loading'" @click="emit('dismiss')">
        取消
      </ElButton>
    </div>

    <div v-else-if="status === 'done'" class="agent-confirm-card__done">
      <ElIcon><CircleCheck /></ElIcon>
      <span>已提交</span>
    </div>

    <div v-else class="agent-confirm-card__dismissed">
      <ElIcon><Close /></ElIcon>
      <span>已取消</span>
    </div>
  </div>
</template>

<style scoped>
.agent-confirm-card {
  margin-block-start: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px var(--color-border);
}

.agent-confirm-card.is-done {
  background: #ecfdf5;
  box-shadow: inset 0 0 0 1px #6ee7b7;
}

.agent-confirm-card.is-dismissed {
  opacity: 0.65;
}

.agent-confirm-card__head {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.agent-confirm-card__icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: #7c3aed;
  background: #fff;
  box-shadow: inset 0 0 0 1px #ddd6fe;
  flex-shrink: 0;
}

.agent-confirm-card__title strong {
  display: block;
  font-size: 14px;
  color: #5b21b6;
}

.agent-confirm-card__title p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.agent-confirm-card__reason {
  margin: var(--space-2) 0 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: #fff;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.agent-confirm-card__body {
  margin-block-start: var(--space-3);
}

.agent-confirm-card__field {
  margin-block-end: var(--space-3);
}

.agent-confirm-card__field label {
  display: block;
  margin-block-end: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.agent-confirm-card__diff-before {
  margin: 0 0 4px;
  font-size: 12px;
  color: #b45309;
  line-height: 1.5;
}

.agent-confirm-card__hint {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.agent-confirm-card__list {
  margin: 0;
  padding-inline-start: 1.2em;
  font-size: 13px;
  line-height: 1.7;
}

.agent-confirm-card__actions {
  display: flex;
  gap: var(--space-2);
  margin-block-start: var(--space-3);
}

.agent-confirm-card__done,
.agent-confirm-card__dismissed {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-block-start: var(--space-3);
  font-size: 12px;
  color: var(--color-text-muted);
}

.agent-confirm-card__done {
  color: #059669;
}

.agent-confirm-card {
  margin-block-start: 14px;
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f2fff8 0%, #ffffff 100%);
  box-shadow:
    0 10px 28px rgba(20, 169, 120, 0.08),
    inset 0 0 0 1px rgba(35, 188, 134, 0.22);
}

.agent-confirm-card.is-done {
  background: #f0fff8;
  box-shadow: inset 0 0 0 1px rgba(35, 188, 134, 0.28);
}

.agent-confirm-card.is-dismissed {
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.24);
}

.agent-confirm-card__icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  color: #079669;
  background: #ecfff7;
  box-shadow: inset 0 0 0 1px rgba(35, 188, 134, 0.24);
}

.agent-confirm-card__title strong {
  color: #08785a;
}

.agent-confirm-card__title p,
.agent-confirm-card__reason,
.agent-confirm-card__hint {
  color: #5f7288;
}

.agent-confirm-card__reason {
  background: rgba(255, 255, 255, 0.82);
  box-shadow: inset 0 0 0 1px rgba(203, 232, 219, 0.72);
}

.agent-confirm-card__diff-table {
  display: grid;
  margin-block-end: 10px;
  overflow: hidden;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(172, 201, 222, 0.62);
}

.agent-confirm-card__diff-row {
  display: grid;
  grid-template-columns: minmax(70px, 0.7fr) minmax(0, 1.2fr) minmax(0, 1.2fr);
  min-width: 0;
}

.agent-confirm-card__diff-row + .agent-confirm-card__diff-row {
  border-top: 1px solid rgba(172, 201, 222, 0.45);
}

.agent-confirm-card__diff-row span {
  min-width: 0;
  padding: 9px 10px;
  border-inline-start: 1px solid rgba(172, 201, 222, 0.45);
  color: #38516a;
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.agent-confirm-card__diff-row span:first-child {
  border-inline-start: none;
  font-weight: 650;
  color: #24445f;
}

.agent-confirm-card__diff-row--head {
  background: #f5f9fd;
}

.agent-confirm-card__diff-row--head span {
  color: #607890;
  font-weight: 700;
}

.agent-confirm-card__field {
  padding: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1px rgba(218, 234, 244, 0.72);
}

.agent-confirm-card__field + .agent-confirm-card__field {
  margin-block-start: 10px;
}

.agent-confirm-card__field label {
  color: #57708a;
}

.agent-confirm-card__actions {
  justify-content: flex-start;
  padding-top: 2px;
}

.agent-confirm-card__actions .el-button,
.agent-confirm-card__done,
.agent-confirm-card__dismissed {
  border-radius: 12px;
}

.agent-confirm-card__done,
.agent-confirm-card__dismissed {
  width: fit-content;
  padding: 7px 10px;
  background: rgba(255, 255, 255, 0.78);
}

.agent-confirm-card__done {
  color: #079669;
  box-shadow: inset 0 0 0 1px rgba(35, 188, 134, 0.22);
}

@media (max-width: 720px) {
  .agent-confirm-card__diff-row {
    grid-template-columns: 1fr;
  }

  .agent-confirm-card__diff-row span {
    border-inline-start: none;
    border-top: 1px solid rgba(172, 201, 222, 0.32);
  }

  .agent-confirm-card__diff-row span:first-child {
    border-top: none;
    background: #f8fbff;
  }
}
</style>

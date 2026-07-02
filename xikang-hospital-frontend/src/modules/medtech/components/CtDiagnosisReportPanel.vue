<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDivider, ElMessage, ElTag } from 'element-plus'
import DynamicResultForm from '@/shared/components/DynamicResultForm.vue'
import { resultFormApi } from '@/shared/api/modules/resultForm'
import { medtechApi } from '@/shared/api/modules/medtech'
import type { ResultFormSchema } from '@/shared/types/resultForm'
import type { CtAnalyzeResult } from '@/shared/api/modules/ctViewer'

const props = defineProps<{
  checkRequestId: number
  canEdit?: boolean
  analysisResult?: CtAnalyzeResult | null
  /** 只读模式：直接传入已解析的表单 schema，跳过 medtech API */
  readonlySchema?: ResultFormSchema | null
  embedded?: boolean
  hasImaging?: boolean
}>()

const emit = defineEmits<{
  submitted: []
}>()

const loading = ref(false)
const drafting = ref(false)
const schema = ref<ResultFormSchema | null>(null)
const formValues = ref<Record<string, unknown>>({})
const formRef = ref<InstanceType<typeof DynamicResultForm>>()
const loadError = ref('')

const canDraft = computed(() => Boolean(props.canEdit && props.checkRequestId && !loading.value && !drafting.value))
const canSubmit = computed(() => Boolean(
  props.canEdit && schema.value && !loading.value && !drafting.value && props.hasImaging !== false,
))

const qcSeverityLabel: Record<string, string> = {
  clean: '无伪影',
  mild: '轻微',
  moderate: '中等',
  severe: '严重',
}

const qcSummary = computed(() => {
  const result = props.analysisResult
  if (!result) return null
  return {
    hasArtifact: result.has_artifact,
    severity: qcSeverityLabel[result.severity] ?? result.severity,
    metalPercent: Math.round((result.artifact_types?.metal ?? 0) * 1000) / 10,
  }
})

function applySchema(next: ResultFormSchema | null) {
  if (!next) return
  schema.value = next
  formValues.value = { ...(next.existingValues ?? {}) }
  loadError.value = ''
}

async function loadSchema() {
  if (!props.checkRequestId) return
  if (!props.canEdit) {
    if (props.readonlySchema) {
      applySchema(props.readonlySchema)
      return
    }
    // 父组件异步加载 schema 中，暂不报错
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const resolved = await resultFormApi.resolveCheckForm({ checkRequestId: props.checkRequestId })
    applySchema(resolved)
  } catch (err) {
    schema.value = null
    loadError.value = err instanceof Error ? err.message : '报告表单加载失败'
  } finally {
    loading.value = false
  }
}

async function handleAiDraft() {
  if (!props.checkRequestId || !canDraft.value) return
  drafting.value = true
  try {
    const result = await medtechApi.ctInferCheck(props.checkRequestId)
    if (result.simulatedValues) {
      formValues.value = { ...formValues.value, ...result.simulatedValues }
    }
    ElMessage.success('AI 报告草稿已生成，请核对后提交')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : 'AI 起草失败，请稍后重试')
  } finally {
    drafting.value = false
  }
}

async function handleSubmit() {
  if (!props.checkRequestId || !schema.value) return
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    await medtechApi.submitCheckResult(props.checkRequestId, { values: formValues.value })
    ElMessage.success('诊断报告已提交')
    emit('submitted')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '提交失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.checkRequestId, props.readonlySchema, props.canEdit] as const,
  () => {
    void loadSchema()
  },
)

onMounted(() => {
  void loadSchema()
})
</script>

<template>
  <aside class="ct-report-panel" :class="{ 'ct-report-panel--embedded': embedded }">
    <header v-if="!embedded" class="ct-report-panel__header">
      <h2 class="ct-report-panel__title">诊断报告</h2>
      <p class="ct-report-panel__hint">
        {{ canEdit ? '填写所见、印象与结论，提交后诊疗医生可查看。' : '医技技师填写的影像诊断报告。' }}
      </p>
    </header>

    <ElAlert
      v-if="canEdit && hasImaging === false"
      type="warning"
      title="请先上传并绑定 CT 影像后再提交诊断报告"
      show-icon
      :closable="false"
      class="ct-report-panel__alert"
    />

    <ElAlert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="ct-report-panel__alert"
    />

    <section v-if="qcSummary" class="ct-report-panel__qc">
      <h3 class="ct-report-panel__section-title">影像质控参考</h3>
      <div class="ct-report-panel__qc-row">
        <span>伪影检测</span>
        <ElTag :type="qcSummary.hasArtifact ? 'warning' : 'success'" size="small">
          {{ qcSummary.hasArtifact ? '检测到伪影' : '未见明显伪影' }}
        </ElTag>
      </div>
      <div class="ct-report-panel__qc-row">
        <span>严重程度</span>
        <span>{{ qcSummary.severity }}</span>
      </div>
      <div v-if="qcSummary.hasArtifact" class="ct-report-panel__qc-row">
        <span>金属伪影概率</span>
        <span>{{ qcSummary.metalPercent }}%</span>
      </div>
      <p class="ct-report-panel__qc-note">质控结果仅供辅助判读，诊断结论以医师阅片为准。</p>
    </section>

    <ElDivider v-if="qcSummary" class="ct-report-panel__divider" />

    <section v-loading="loading || drafting || (!canEdit && !schema)" class="ct-report-panel__form">
      <template v-if="schema">
        <DynamicResultForm
          ref="formRef"
          v-model="formValues"
          :fields="schema.fields"
          :base-field-count="schema.baseFieldCount"
          :readonly="!canEdit"
        />
      </template>
    </section>

    <footer v-if="canEdit" class="ct-report-panel__actions">
      <ElButton :loading="drafting" :disabled="!canDraft" @click="handleAiDraft">
        AI 起草报告
      </ElButton>
      <ElButton type="primary" :loading="loading" :disabled="!canSubmit" @click="handleSubmit">
        提交报告
      </ElButton>
    </footer>
  </aside>
</template>

<style scoped>
.ct-report-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ct-surface);
  border-inline-start: 1px solid var(--ct-border);
}

.ct-report-panel--embedded {
  border-inline-start: none;
  background: transparent;
}

.ct-report-panel__header {
  flex-shrink: 0;
  padding: 12px 14px 8px;
  border-block-end: 1px solid var(--ct-border);
}

.ct-report-panel__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ct-text);
}

.ct-report-panel__hint {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--ct-text-dim);
  line-height: 1.4;
}

.ct-report-panel__alert {
  margin: 10px 12px 0;
}

.ct-report-panel__qc {
  flex-shrink: 0;
  padding: 10px 14px 0;
}

.ct-report-panel__section-title {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ct-text-muted);
}

.ct-report-panel__qc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ct-text-muted);
  margin-block-end: 6px;
}

.ct-report-panel__qc-note {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--ct-text-dim);
  line-height: 1.4;
}

.ct-report-panel__divider {
  margin: 10px 14px;
}

.ct-report-panel__form {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 14px 12px;
}

.ct-report-panel__actions {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 14px 12px;
  border-block-start: 1px solid var(--ct-border);
  background: var(--ct-bg-soft);
}
</style>

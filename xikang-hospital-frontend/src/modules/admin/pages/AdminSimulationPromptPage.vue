<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSpace,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  simulationConfigApi,
  type SimulationConfigDetail,
  type SimulationConfigPayload,
  type SimulationDiseaseMapping,
  type SimulationPromptSections,
} from '@/shared/api/modules/simulationConfig'
import { formatBeijingDateTime } from '@/shared/utils/beijingDate'

const props = withDefaults(
  defineProps<{
    embedded?: boolean
    /** 使用加大窗口（仍保留遮罩与居中），embedded 下默认开启 */
    dialogExpanded?: boolean
  }>(),
  {
    embedded: false,
    dialogExpanded: undefined,
  },
)

const isDialogExpanded = computed(() => {
  if (props.dialogExpanded !== undefined) return props.dialogExpanded
  return props.embedded
})

const dialogWidth = computed(() => (isDialogExpanded.value ? 'min(1120px, 94vw)' : '920px'))
const promptTextareaRows = computed(() => (isDialogExpanded.value ? 20 : 16))
const userPromptTextareaRows = computed(() => (isDialogExpanded.value ? 16 : 12))

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rows = ref<SimulationConfigDetail[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const activeFormTab = ref('basic')
const outputSchemaText = ref('')

const form = reactive<SimulationConfigPayload>({
  configKey: '',
  techCode: '',
  checkName: '',
  matchKeywords: '',
  enabled: true,
  simulationMode: 'lab_items',
  promptSections: emptyPromptSections(),
  diseaseMappings: [],
  defaults: {
    notice: '本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。',
    normalConclusion: '',
  },
})

const formRef = ref<InstanceType<typeof ElForm>>()

const systemPromptText = ref('')
const userPromptText = ref('')

const SYSTEM_MARKER_ITEM = '【检查项目清单】'
const SYSTEM_MARKER_REF = '【参考范围】'
const SYSTEM_MARKER_OUTPUT = '【输出格式】'
const USER_MARKER_NORMAL = '【正常模式规则】'
const USER_MARKER_ABNORMAL = '【异常模式规则】'

const formRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  checkName: [{ required: true, message: '请输入检查名称', trigger: 'blur' }],
}

function emptyPromptSections(): SimulationPromptSections {
  return {
    role: '',
    scope: '',
    itemCatalog: '',
    referenceRanges: '',
    normalRules: '',
    abnormalRules: '',
    outputFormat: '',
  }
}

function sectionsToSystemText(sections: SimulationPromptSections) {
  return [
    sections.role.trim(),
    sections.scope.trim(),
    `${SYSTEM_MARKER_ITEM}\n${sections.itemCatalog.trim()}`,
    `${SYSTEM_MARKER_REF}\n${sections.referenceRanges.trim()}`,
    `${SYSTEM_MARKER_OUTPUT}\n${sections.outputFormat.trim()}`,
  ]
    .filter((part) => part.replace(/\n/g, '').trim())
    .join('\n\n')
}

function sectionsToUserText(sections: SimulationPromptSections) {
  return [
    `${USER_MARKER_NORMAL}\n${sections.normalRules.trim()}`,
    `${USER_MARKER_ABNORMAL}\n${sections.abnormalRules.trim()}`,
  ]
    .filter((part) => part.replace(/\n/g, '').trim())
    .join('\n\n')
}

function systemTextToSections(text: string): SimulationPromptSections {
  const sections = emptyPromptSections()
  const trimmed = text.trim()
  if (!trimmed) return sections

  const itemIdx = trimmed.indexOf(SYSTEM_MARKER_ITEM)
  const refIdx = trimmed.indexOf(SYSTEM_MARKER_REF)
  const outIdx = trimmed.indexOf(SYSTEM_MARKER_OUTPUT)

  if (itemIdx === -1 || refIdx === -1 || outIdx === -1) {
    sections.role = trimmed
    return sections
  }

  const beforeItems = trimmed.slice(0, itemIdx).trim()
  const roleScopeParts = beforeItems.split(/\n\n+/).map((part) => part.trim()).filter(Boolean)
  sections.role = roleScopeParts[0] || ''
  sections.scope = roleScopeParts.slice(1).join('\n\n')

  sections.itemCatalog = trimmed.slice(itemIdx + SYSTEM_MARKER_ITEM.length, refIdx).trim()
  sections.referenceRanges = trimmed.slice(refIdx + SYSTEM_MARKER_REF.length, outIdx).trim()
  sections.outputFormat = trimmed.slice(outIdx + SYSTEM_MARKER_OUTPUT.length).trim()
  return sections
}

function userTextToSections(text: string): Pick<SimulationPromptSections, 'normalRules' | 'abnormalRules'> {
  const trimmed = text.trim()
  if (!trimmed) {
    return { normalRules: '', abnormalRules: '' }
  }

  const normalIdx = trimmed.indexOf(USER_MARKER_NORMAL)
  const abnormalIdx = trimmed.indexOf(USER_MARKER_ABNORMAL)

  if (normalIdx === -1 && abnormalIdx === -1) {
    return { normalRules: trimmed, abnormalRules: '' }
  }

  if (abnormalIdx === -1) {
    return {
      normalRules: trimmed.slice(normalIdx + USER_MARKER_NORMAL.length).trim(),
      abnormalRules: '',
    }
  }

  if (normalIdx === -1 || normalIdx > abnormalIdx) {
    return {
      normalRules: '',
      abnormalRules: trimmed.slice(abnormalIdx + USER_MARKER_ABNORMAL.length).trim(),
    }
  }

  return {
    normalRules: trimmed.slice(normalIdx + USER_MARKER_NORMAL.length, abnormalIdx).trim(),
    abnormalRules: trimmed.slice(abnormalIdx + USER_MARKER_ABNORMAL.length).trim(),
  }
}

function syncPromptTextsFromSections() {
  systemPromptText.value = sectionsToSystemText(form.promptSections)
  userPromptText.value = sectionsToUserText(form.promptSections)
}

function applyPromptTextsToSections() {
  const systemSections = systemTextToSections(systemPromptText.value)
  const userSections = userTextToSections(userPromptText.value)
  form.promptSections = {
    ...form.promptSections,
    ...systemSections,
    ...userSections,
  }
}

function validatePromptTexts(): string | null {
  applyPromptTextsToSections()
  const { promptSections } = form

  if (!systemPromptText.value.trim()) return '请填写 System 提示词'
  if (!userPromptText.value.trim()) return '请填写 User 提示词'

  const requiredSystemFields: Array<[keyof SimulationPromptSections, string]> = [
    ['role', '角色定义'],
    ['scope', '模拟范围'],
    ['itemCatalog', '指标清单'],
    ['referenceRanges', '参考范围'],
    ['outputFormat', '输出格式'],
  ]
  for (const [key, label] of requiredSystemFields) {
    if (!promptSections[key].trim()) {
      if (!systemPromptText.value.includes(SYSTEM_MARKER_ITEM)) {
        return `System 提示词需包含 ${SYSTEM_MARKER_ITEM}、${SYSTEM_MARKER_REF}、${SYSTEM_MARKER_OUTPUT} 分段标记，便于保存时自动拆分`
      }
      return `System 提示词缺少：${label}`
    }
  }

  if (!promptSections.normalRules.trim()) {
    return `User 提示词需包含 ${USER_MARKER_NORMAL} 段落`
  }
  if (!promptSections.abnormalRules.trim()) {
    return `User 提示词需包含 ${USER_MARKER_ABNORMAL} 段落`
  }

  return null
}

async function copyPromptText(text: string, label: string) {
  if (!text.trim()) {
    ElMessage.warning(`${label} 为空，无法复制`)
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label} 已复制到剪贴板`)
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

function emptyDiseaseMapping(): SimulationDiseaseMapping {
  return { keywords: '', hint: '', priority: 1 }
}

function formatKeywords(value: SimulationDiseaseMapping['keywords']) {
  return Array.isArray(value) ? value.join(', ') : value
}

function parseKeywords(value: string) {
  return value
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function loadRows() {
  loading.value = true
  try {
    rows.value = await simulationConfigApi.list(keyword.value.trim() || undefined)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.configKey = ''
  form.techCode = ''
  form.checkName = ''
  form.matchKeywords = ''
  form.enabled = true
  form.simulationMode = 'lab_items'
  form.promptSections = emptyPromptSections()
  form.diseaseMappings = []
  form.defaults = {
    notice: '本结果为AI模拟生成，仅用于项目测试，不代表真实医学检查结果。',
    normalConclusion: '',
  }
  outputSchemaText.value = JSON.stringify(
    { type: 'object', required: ['checkName', 'simulatedForDiseases', 'resultItems', 'conclusion', 'notice'] },
    null,
    2,
  )
  syncPromptTextsFromSections()
  activeFormTab.value = 'basic'
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: SimulationConfigDetail) {
  loading.value = true
  try {
    const detail = await simulationConfigApi.get(row.id)
    editingId.value = detail.id
    form.configKey = detail.configKey
    form.techCode = detail.techCode || ''
    form.checkName = detail.checkName
    form.matchKeywords = detail.matchKeywords || ''
    form.enabled = detail.enabled
    form.simulationMode = detail.simulationMode || 'lab_items'
    form.promptSections = { ...emptyPromptSections(), ...detail.promptSections }
    form.diseaseMappings = (detail.diseaseMappings || []).map((item) => ({
      keywords: formatKeywords(item.keywords),
      hint: item.hint || '',
      priority: item.priority ?? 1,
    }))
    form.defaults = {
      notice: detail.defaults?.notice || '',
      normalConclusion: detail.defaults?.normalConclusion || '',
    }
    outputSchemaText.value = JSON.stringify(detail.outputSchema || {}, null, 2)
    syncPromptTextsFromSections()
    activeFormTab.value = 'basic'
    dialogVisible.value = true
  } finally {
    loading.value = false
  }
}

function addDiseaseMapping() {
  form.diseaseMappings.push(emptyDiseaseMapping())
}

function removeDiseaseMapping(index: number) {
  form.diseaseMappings.splice(index, 1)
}

function buildPayload(): SimulationConfigPayload {
  let outputSchema: Record<string, unknown> | undefined
  if (outputSchemaText.value.trim()) {
    outputSchema = JSON.parse(outputSchemaText.value) as Record<string, unknown>
  }

  return {
    configKey: form.configKey.trim(),
    techCode: form.techCode.trim() || undefined,
    checkName: form.checkName.trim(),
    matchKeywords: form.matchKeywords.trim() || undefined,
    enabled: form.enabled,
    simulationMode: form.simulationMode,
    promptSections: {
      role: form.promptSections.role.trim(),
      scope: form.promptSections.scope.trim(),
      itemCatalog: form.promptSections.itemCatalog.trim(),
      referenceRanges: form.promptSections.referenceRanges.trim(),
      normalRules: form.promptSections.normalRules.trim(),
      abnormalRules: form.promptSections.abnormalRules.trim(),
      outputFormat: form.promptSections.outputFormat.trim(),
    },
    diseaseMappings: form.diseaseMappings
      .filter((item) => item.hint.trim() || String(item.keywords).trim())
      .map((item, index) => ({
        keywords: parseKeywords(String(item.keywords)),
        hint: item.hint.trim(),
        priority: item.priority ?? index + 1,
      })),
    outputSchema,
    defaults: {
      notice: form.defaults?.notice?.trim(),
      normalConclusion: form.defaults?.normalConclusion?.trim() || `${form.checkName.trim()}模拟结果未见明显异常`,
    },
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const promptError = validatePromptTexts()
  if (promptError) {
    ElMessage.warning(promptError)
    activeFormTab.value = 'prompt'
    return
  }

  let payload: SimulationConfigPayload
  try {
    payload = buildPayload()
  } catch {
    ElMessage.error('输出结构 JSON 格式不正确')
    activeFormTab.value = 'advanced'
    return
  }

  saving.value = true
  try {
    if (editingId.value == null) {
      await simulationConfigApi.create(payload)
      ElMessage.success('模拟配置已创建')
    } else {
      await simulationConfigApi.update(editingId.value, payload)
      ElMessage.success('模拟配置已保存')
    }
    dialogVisible.value = false
    await loadRows()
  } finally {
    saving.value = false
  }
}

async function removeRow(row: SimulationConfigDetail) {
  await ElMessageBox.confirm(`确定删除「${row.checkName}」的模拟提示词配置？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await simulationConfigApi.remove(row.id)
  ElMessage.success('已删除')
  await loadRows()
}

onMounted(() => {
  void loadRows()
})
</script>

<template>
  <div class="sim-prompt-admin" :class="{ 'sim-prompt-admin--embedded': props.embedded }">
    <div class="sim-prompt-admin__toolbar" :class="{ 'admin-embedded-toolbar': props.embedded }">
      <ElSpace wrap>
        <ElInput
          v-model="keyword"
          class="sim-prompt-admin__search"
          clearable
          placeholder="搜索配置键 / 检查名称 / 编码"
          @keyup.enter="loadRows"
        >
          <template #prefix>
            <ElIcon><Search /></ElIcon>
          </template>
        </ElInput>
        <ElButton :icon="Search" @click="loadRows">查询</ElButton>
        <ElButton :icon="Refresh" @click="loadRows">刷新</ElButton>
      </ElSpace>
      <ElButton type="primary" :icon="Plus" @click="openCreate">新增配置</ElButton>
    </div>

    <ElTable v-loading="loading" :data="rows" stripe>
      <ElTableColumn prop="configKey" label="配置键" width="100" />
      <ElTableColumn prop="checkName" label="检查名称" min-width="140" />
      <ElTableColumn prop="techCode" label="项目编码" width="110" />
      <ElTableColumn prop="matchKeywords" label="匹配关键词" min-width="180" show-overflow-tooltip />
      <ElTableColumn label="状态" width="90">
        <template #default="{ row }">
          <ElTag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="version" label="版本" width="70" />
      <ElTableColumn label="更新时间" width="170">
        <template #default="{ row }">
          {{ formatBeijingDateTime(row.updatedAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
          <ElButton link type="danger" @click="removeRow(row)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增模拟提示词配置' : '编辑模拟提示词配置'"
      :width="dialogWidth"
      append-to-body
      destroy-on-close
      align-center
      :class="['sim-prompt-admin__dialog', { 'sim-prompt-admin__dialog--expanded': isDialogExpanded }]"
    >
      <ElForm
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="108px"
        class="sim-prompt-admin__dialog-form"
      >
        <ElTabs v-model="activeFormTab" class="sim-prompt-admin__dialog-tabs">
          <ElTabPane label="基本信息" name="basic">
            <ElFormItem label="配置键" prop="configKey">
              <ElInput v-model="form.configKey" :disabled="editingId != null" placeholder="如 XCG" maxlength="64" />
            </ElFormItem>
            <ElFormItem label="检查名称" prop="checkName">
              <ElInput v-model="form.checkName" placeholder="如 血常规" maxlength="128" />
            </ElFormItem>
            <ElFormItem label="项目编码">
              <ElInput v-model="form.techCode" placeholder="与医技项目 techCode 对应，可选" maxlength="64" />
            </ElFormItem>
            <ElFormItem label="匹配关键词">
              <ElInput
                v-model="form.matchKeywords"
                placeholder="逗号分隔，用于按检查名称模糊匹配"
                maxlength="500"
              />
            </ElFormItem>
            <ElFormItem label="模拟模式">
              <ElSelect v-model="form.simulationMode" class="sim-prompt-admin__field-full">
                <ElOption label="检验指标 (lab_items)" value="lab_items" />
                <ElOption label="通用检查 (general_check)" value="general_check" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="启用">
              <ElSwitch v-model="form.enabled" />
            </ElFormItem>
          </ElTabPane>

          <ElTabPane label="提示词片段" name="prompt" class="sim-prompt-admin__prompt-pane">
            <p class="sim-prompt-admin__hint">
              按 Dify 工作流实际组装方式展示为 System / User 两大段，可直接整段复制。
              保存时会根据【检查项目清单】等标记自动拆分回数据库字段。
            </p>

            <div class="sim-prompt-admin__prompt-block">
              <div class="sim-prompt-admin__prompt-header">
                <span class="sim-prompt-admin__prompt-title">System Prompt</span>
                <ElButton link type="primary" @click="copyPromptText(systemPromptText, 'System Prompt')">
                  复制全文
                </ElButton>
              </div>
              <ElInput
                v-model="systemPromptText"
                type="textarea"
                :rows="promptTextareaRows"
                placeholder="角色定义、模拟范围、指标清单、参考范围、输出格式..."
                class="sim-prompt-admin__prompt-textarea"
              />
            </div>

            <div class="sim-prompt-admin__prompt-block">
              <div class="sim-prompt-admin__prompt-header">
                <span class="sim-prompt-admin__prompt-title">User Prompt（规则片段）</span>
                <ElButton link type="primary" @click="copyPromptText(userPromptText, 'User Prompt')">
                  复制全文
                </ElButton>
              </div>
              <ElInput
                v-model="userPromptText"
                type="textarea"
                :rows="userPromptTextareaRows"
                placeholder="正常模式规则、异常模式规则..."
                class="sim-prompt-admin__prompt-textarea"
              />
            </div>
          </ElTabPane>

          <ElTabPane label="疾病映射" name="disease">
            <p class="sim-prompt-admin__hint">
              异常模式下，工作流会按可能疾病关键词筛选前 3 条映射注入 Prompt。
            </p>
            <div class="sim-prompt-admin__mapping-toolbar">
              <ElButton :icon="Plus" @click="addDiseaseMapping">添加映射</ElButton>
            </div>
            <ElTable :data="form.diseaseMappings" empty-text="暂无疾病映射，可点击上方添加">
              <ElTableColumn label="疾病关键词" min-width="220">
                <template #default="{ row }">
                  <ElInput v-model="row.keywords" placeholder="如 细菌, 肺炎" />
                </template>
              </ElTableColumn>
              <ElTableColumn label="指标倾向" min-width="220">
                <template #default="{ row }">
                  <ElInput v-model="row.hint" placeholder="如 WBC↑ NEUT%↑" />
                </template>
              </ElTableColumn>
              <ElTableColumn label="优先级" width="120">
                <template #default="{ row }">
                  <ElInputNumber v-model="row.priority" :min="1" :max="99" />
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="80">
                <template #default="{ $index }">
                  <ElButton link type="danger" @click="removeDiseaseMapping($index)">删除</ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </ElTabPane>

          <ElTabPane label="默认文案" name="defaults">
            <ElFormItem label="免责声明">
              <ElInput v-model="form.defaults!.notice" type="textarea" :rows="2" />
            </ElFormItem>
            <ElFormItem label="正常结论模板">
              <ElInput
                v-model="form.defaults!.normalConclusion"
                placeholder="留空则自动生成：{检查名称}模拟结果未见明显异常"
              />
            </ElFormItem>
          </ElTabPane>

          <ElTabPane label="高级" name="advanced">
            <ElFormItem label="输出结构 JSON">
              <ElInput
                v-model="outputSchemaText"
                type="textarea"
                :rows="12"
                placeholder="output_schema JSON"
              />
            </ElFormItem>
          </ElTabPane>
        </ElTabs>
      </ElForm>

      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitForm">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.sim-prompt-admin {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.sim-prompt-admin__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.sim-prompt-admin .admin-embedded-toolbar {
  margin-block-end: 0;
}

.sim-prompt-admin__search {
  width: min(320px, 100%);
}

.sim-prompt-admin__field-full {
  width: 100%;
}

.sim-prompt-admin__hint {
  margin: 0 0 var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.sim-prompt-admin__mapping-toolbar {
  margin-block-end: var(--space-3);
}

.sim-prompt-admin__prompt-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-block-end: var(--space-5);
}

.sim-prompt-admin__prompt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.sim-prompt-admin__prompt-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text);
}

.sim-prompt-admin__prompt-textarea :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>

<!-- Dialog 使用 append-to-body，样式需非 scoped -->
<style>
.sim-prompt-admin__dialog--expanded.el-dialog {
  display: flex;
  flex-direction: column;
  max-height: 88vh;
  margin-block: 6vh auto;
}

.sim-prompt-admin__dialog--expanded .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>

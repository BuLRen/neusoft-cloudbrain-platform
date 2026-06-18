<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { adminApi, type MedicalTechnologyItem } from '@/shared/api/modules/admin'
import { resultFormApi, type ResultFormCategory } from '@/shared/api/modules/resultForm'
import type { ResultFormField, ResultFormFieldType } from '@/shared/types/resultForm'

const route = useRoute()

const activeTab = ref<'category' | 'tech'>('category')
const loading = ref(false)
const saving = ref(false)

const categories = ref<ResultFormCategory[]>([])
const selectedCategory = ref('')
const categoryFields = ref<ResultFormField[]>([])

const techKeyword = ref('')
const techRows = ref<MedicalTechnologyItem[]>([])
const selectedTechId = ref<number | null>(null)
const baseFields = ref<ResultFormField[]>([])
const extensionFields = ref<ResultFormField[]>([])
const techCategoryCode = ref('')
const techCategoryName = ref('')

const fieldTypeOptions: Array<{ label: string; value: ResultFormFieldType }> = [
  { label: '单行文本', value: 'text' },
  { label: '多行文本', value: 'textarea' },
  { label: '数字', value: 'number' },
]

const selectedTech = computed(() => techRows.value.find((item) => item.id === selectedTechId.value) ?? null)

function emptyField(sortOrder = 1): ResultFormField {
  return {
    fieldKey: '',
    fieldLabel: '',
    fieldType: 'textarea',
    required: false,
    sortOrder,
    placeholder: '',
  }
}

async function loadCategories() {
  categories.value = await resultFormApi.listCategories()
  if (!selectedCategory.value && categories.value.length) {
    selectedCategory.value = categories.value[0].categoryCode
  }
}

async function loadCategoryFields() {
  if (!selectedCategory.value) return
  loading.value = true
  try {
    categoryFields.value = await resultFormApi.listCategoryFields(selectedCategory.value)
    if (!categoryFields.value.length) {
      categoryFields.value = [emptyField()]
    }
  } finally {
    loading.value = false
  }
}

async function loadTechItems() {
  loading.value = true
  try {
    const page = await adminApi.pageExaminationItems({
      techType: 'check',
      keyword: techKeyword.value.trim() || undefined,
      page: 1,
      size: 100,
    })
    techRows.value = page.records
    const routeTechId = Number(route.query.techId || 0)
    if (routeTechId && techRows.value.some((item) => item.id === routeTechId)) {
      selectedTechId.value = routeTechId
      activeTab.value = 'tech'
    } else if (!selectedTechId.value && techRows.value.length) {
      selectedTechId.value = techRows.value[0].id
    }
  } finally {
    loading.value = false
  }
}

async function loadTechExtensions() {
  if (!selectedTechId.value) return
  loading.value = true
  try {
    const context = await resultFormApi.getTechExtensions(selectedTechId.value)
    baseFields.value = context.baseFields
    extensionFields.value = context.extensionFields.length ? context.extensionFields : []
    techCategoryCode.value = context.categoryCode
    techCategoryName.value = context.categoryName
  } finally {
    loading.value = false
  }
}

function addCategoryField() {
  categoryFields.value.push(emptyField(categoryFields.value.length + 1))
}

function removeCategoryField(index: number) {
  categoryFields.value.splice(index, 1)
}

function addExtensionField() {
  extensionFields.value.push(emptyField(baseFields.value.length + extensionFields.value.length + 1))
}

function removeExtensionField(index: number) {
  extensionFields.value.splice(index, 1)
}

function validateEditableFields(fields: ResultFormField[], label: string) {
  const keys = new Set<string>()
  for (const field of fields) {
    if (!field.fieldKey.trim() || !field.fieldLabel.trim()) {
      ElMessage.warning(`${label}的字段标识和标签不能为空`)
      return false
    }
    if (keys.has(field.fieldKey.trim())) {
      ElMessage.warning(`${label}存在重复字段标识：${field.fieldKey}`)
      return false
    }
    keys.add(field.fieldKey.trim())
  }
  return true
}

async function saveCategoryFields() {
  if (!selectedCategory.value) return
  const payload = categoryFields.value.map((field, index) => ({
    ...field,
    fieldKey: field.fieldKey.trim(),
    fieldLabel: field.fieldLabel.trim(),
    sortOrder: field.sortOrder ?? index + 1,
  }))
  if (!validateEditableFields(payload, '分类模板')) return

  saving.value = true
  try {
    await resultFormApi.saveCategoryFields(selectedCategory.value, payload)
    ElMessage.success('分类模板已保存')
    await loadCategoryFields()
  } finally {
    saving.value = false
  }
}

async function saveExtensionFields() {
  if (!selectedTechId.value) return
  const payload = extensionFields.value.map((field, index) => ({
    ...field,
    fieldKey: field.fieldKey.trim(),
    fieldLabel: field.fieldLabel.trim(),
    sortOrder: field.sortOrder ?? baseFields.value.length + index + 1,
  }))
  if (!validateEditableFields(payload, '项目扩展字段')) return

  saving.value = true
  try {
    await resultFormApi.saveTechExtensions(selectedTechId.value, payload)
    ElMessage.success('项目扩展字段已保存')
    await loadTechExtensions()
  } finally {
    saving.value = false
  }
}

watch(selectedCategory, () => {
  void loadCategoryFields()
})

watch(selectedTechId, () => {
  if (selectedTechId.value) {
    void loadTechExtensions()
  }
})

onMounted(async () => {
  if (route.query.tab === 'tech') {
    activeTab.value = 'tech'
  }
  await Promise.all([loadCategories(), loadTechItems()])
  if (selectedCategory.value) {
    await loadCategoryFields()
  }
  if (selectedTechId.value) {
    await loadTechExtensions()
  }
})
</script>

<template>
  <div class="result-form-admin">
    <PageHeader
      title="检查结果表单配置"
      description="维护分类通用模板，并为具体检查项目追加专属字段。"
    />

    <ElTabs v-model="activeTab">
      <ElTabPane label="分类通用模板" name="category">
        <ElCard shadow="never" class="result-form-admin__card">
          <div class="result-form-admin__toolbar">
            <ElFormItem label="表单分类" label-width="80px" class="result-form-admin__selector">
              <ElSelect v-model="selectedCategory" class="result-form-admin__field-full">
                <ElOption
                  v-for="item in categories"
                  :key="item.categoryCode"
                  :label="`${item.categoryName} (${item.categoryCode})`"
                  :value="item.categoryCode"
                />
              </ElSelect>
            </ElFormItem>
            <ElButton :icon="Refresh" :loading="loading" @click="loadCategoryFields">刷新</ElButton>
            <ElButton type="primary" :icon="Plus" @click="addCategoryField">新增字段</ElButton>
            <ElButton type="success" :loading="saving" @click="saveCategoryFields">保存分类模板</ElButton>
          </div>

          <ElTable v-loading="loading" :data="categoryFields" border>
            <ElTableColumn label="字段标识" min-width="140">
              <template #default="{ row }">
                <ElInput v-model="row.fieldKey" placeholder="如 findings" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="显示标签" min-width="140">
              <template #default="{ row }">
                <ElInput v-model="row.fieldLabel" placeholder="如 所见" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="类型" width="130">
              <template #default="{ row }">
                <ElSelect v-model="row.fieldType" class="result-form-admin__field-full">
                  <ElOption v-for="opt in fieldTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </ElSelect>
              </template>
            </ElTableColumn>
            <ElTableColumn label="必填" width="80" align="center">
              <template #default="{ row }">
                <ElSwitch v-model="row.required" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="排序" width="90">
              <template #default="{ row }">
                <ElInput v-model.number="row.sortOrder" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="占位提示" min-width="160">
              <template #default="{ row }">
                <ElInput v-model="row.placeholder" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="90" align="center">
              <template #default="{ $index }">
                <ElButton link type="danger" @click="removeCategoryField($index)">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="检查项目扩展" name="tech">
        <ElCard shadow="never" class="result-form-admin__card">
          <div class="result-form-admin__toolbar">
            <ElInput v-model="techKeyword" placeholder="搜索项目编码或名称" @keyup.enter="loadTechItems" />
            <ElButton :icon="Refresh" :loading="loading" @click="loadTechItems">查询项目</ElButton>
            <ElSelect v-model="selectedTechId" placeholder="选择检查项目" class="result-form-admin__tech-select">
              <ElOption
                v-for="item in techRows"
                :key="item.id"
                :label="`${item.techName} (${item.techCode})`"
                :value="item.id"
              />
            </ElSelect>
            <ElButton type="primary" :icon="Plus" :disabled="!selectedTechId" @click="addExtensionField">新增扩展字段</ElButton>
            <ElButton type="success" :loading="saving" :disabled="!selectedTechId" @click="saveExtensionFields">保存扩展字段</ElButton>
          </div>

          <div v-if="selectedTech" class="result-form-admin__context">
            <p><strong>当前项目：</strong>{{ selectedTech.techName }}（{{ selectedTech.techCode }}）</p>
            <p><strong>继承分类：</strong>{{ techCategoryName }}（{{ techCategoryCode }}）</p>
          </div>

          <h4 class="result-form-admin__subtitle">继承的基础字段（只读）</h4>
          <ElTable :data="baseFields" border class="result-form-admin__readonly-table">
            <ElTableColumn prop="fieldKey" label="字段标识" />
            <ElTableColumn prop="fieldLabel" label="显示标签" />
            <ElTableColumn prop="fieldType" label="类型" width="100" />
            <ElTableColumn label="必填" width="80" align="center">
              <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
            </ElTableColumn>
          </ElTable>

          <h4 class="result-form-admin__subtitle">项目专属扩展字段</h4>
          <ElTable v-loading="loading" :data="extensionFields" border>
            <ElTableColumn label="字段标识" min-width="140">
              <template #default="{ row }">
                <ElInput v-model="row.fieldKey" placeholder="如 contrastReaction" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="显示标签" min-width="140">
              <template #default="{ row }">
                <ElInput v-model="row.fieldLabel" placeholder="如 造影剂反应" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="类型" width="130">
              <template #default="{ row }">
                <ElSelect v-model="row.fieldType" class="result-form-admin__field-full">
                  <ElOption v-for="opt in fieldTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </ElSelect>
              </template>
            </ElTableColumn>
            <ElTableColumn label="必填" width="80" align="center">
              <template #default="{ row }">
                <ElSwitch v-model="row.required" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="排序" width="90">
              <template #default="{ row }">
                <ElInput v-model.number="row.sortOrder" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="占位提示" min-width="160">
              <template #default="{ row }">
                <ElInput v-model="row.placeholder" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="90" align="center">
              <template #default="{ $index }">
                <ElButton link type="danger" @click="removeExtensionField($index)">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>
    </ElTabs>
  </div>
</template>

<style scoped>
.result-form-admin {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.result-form-admin__card {
  border-radius: var(--radius-xl);
}

.result-form-admin__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  margin-block-end: var(--space-4);
}

.result-form-admin__selector {
  margin: 0;
}

.result-form-admin__tech-select {
  min-width: 240px;
}

.result-form-admin__field-full {
  width: 100%;
}

.result-form-admin__context {
  margin-block-end: var(--space-4);
  color: var(--color-text-muted);
}

.result-form-admin__subtitle {
  margin: var(--space-4) 0 var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.result-form-admin__readonly-table {
  margin-block-end: var(--space-4);
}
</style>

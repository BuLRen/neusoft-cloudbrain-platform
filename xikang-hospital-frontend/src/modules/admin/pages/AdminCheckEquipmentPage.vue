<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSpace,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import {
  adminApi,
  type DepartmentOption,
  type ExaminationItemPayload,
  type MedicalTechnologyItem,
} from '@/shared/api/modules/admin'
import {
  MEDTECH_CHECK_CATEGORY_OPTIONS,
  MEDTECH_INSPECTION_CATEGORY_OPTIONS,
  aiCategoryLabel,
  defaultAiCategoryForTechType,
  isCtCategoryCode,
} from '@/modules/medtech/constants/medtechCategory'

const { embedded = false } = defineProps<{ embedded?: boolean }>()

const router = useRouter()

const TECH_TYPE_LABEL: Record<MedicalTechnologyItem['techType'], string> = {
  check: '检查',
  inspection: '检验',
  disposal: '处置',
}

const TECH_TYPE_TAG: Record<MedicalTechnologyItem['techType'], 'primary' | 'success' | 'warning'> = {
  check: 'primary',
  inspection: 'success',
  disposal: 'warning',
}

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const typeFilter = ref<string>('')
const rows = ref<MedicalTechnologyItem[]>([])
const departments = ref<DepartmentOption[]>([])

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<ExaminationItemPayload>({
  techCode: '',
  techName: '',
  techFormat: '',
  techPrice: 0,
  techType: 'check',
  priceType: '检查费',
  deptmentId: undefined,
  aiCategoryCode: 'general_check',
})

const formRules = {
  techCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  techName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  techType: [{ required: true, message: '请选择检查类型', trigger: 'change' }],
  techPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
}

const formRef = ref<InstanceType<typeof ElForm>>()

function formatPrice(value: number) {
  return Number(value).toFixed(2)
}

function techTypeLabel(type: MedicalTechnologyItem['techType']) {
  return TECH_TYPE_LABEL[type]
}

function techTypeTag(type: MedicalTechnologyItem['techType']) {
  return TECH_TYPE_TAG[type]
}

function castItem(row: unknown): MedicalTechnologyItem {
  return row as MedicalTechnologyItem
}

function resetForm() {
  form.techCode = ''
  form.techName = ''
  form.techFormat = ''
  form.techPrice = 0
  form.techType = 'check'
  form.priceType = '检查费'
  form.deptmentId = undefined
  form.aiCategoryCode = 'general_check'
}

function categoryOptionsForType(type: ExaminationItemPayload['techType']) {
  if (type === 'inspection') return MEDTECH_INSPECTION_CATEGORY_OPTIONS
  if (type === 'check') return MEDTECH_CHECK_CATEGORY_OPTIONS
  return []
}

function onTechTypeChange(type: ExaminationItemPayload['techType']) {
  if (type === 'check') {
    form.priceType = '检查费'
    form.aiCategoryCode = defaultAiCategoryForTechType('check')
  } else if (type === 'inspection') {
    form.priceType = '检验费'
    form.aiCategoryCode = defaultAiCategoryForTechType('inspection')
  } else {
    form.priceType = '处置费'
    form.aiCategoryCode = undefined
  }
}

async function loadDepartments() {
  try {
    departments.value = await adminApi.departments()
  } catch {
    departments.value = []
  }
}

function rowIndex(index: number) {
  return (page.value - 1) * pageSize.value + index + 1
}

async function loadList(resetPage = false) {
  if (resetPage) page.value = 1
  loading.value = true
  try {
    const result = await adminApi.pageExaminationItems({
      techType: typeFilter.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: pageSize.value,
    })
    rows.value = result.records
    total.value = result.total
    page.value = result.page
    pageSize.value = result.size
    if (result.total > 0 && result.totalPages > 0 && rows.value.length === 0 && page.value > 1) {
      page.value = result.totalPages
      await loadList()
    }
  } finally {
    loading.value = false
  }
}

function onPageChange() {
  void loadList()
}

function onPageSizeChange() {
  page.value = 1
  void loadList()
}

function onSearch() {
  void loadList(true)
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: MedicalTechnologyItem) {
  editingId.value = row.id
  form.techCode = row.techCode
  form.techName = row.techName
  form.techFormat = row.techFormat || ''
  form.techPrice = row.techPrice
  form.techType = row.techType
  form.priceType = row.priceType || ''
  form.deptmentId = row.deptmentId
  form.aiCategoryCode = row.aiCategoryCode || defaultAiCategoryForTechType(row.techType)
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createExaminationItem({ ...form })
      ElMessage.success('项目已新增')
    } else {
      const { techCode: _c, ...payload } = form
      await adminApi.updateExaminationItem(editingId.value, payload)
      ElMessage.success('项目已更新')
    }
    dialogVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

function goResultForm(row: MedicalTechnologyItem) {
  router.push({ path: '/admin/medtech-items', query: { tab: 'result-form', techId: String(row.id) } })
}

async function confirmDelete(row: MedicalTechnologyItem) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.techName}」？已被开单引用的项目无法删除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await adminApi.deleteExaminationItem(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch {
    // http 已提示
  }
}

onMounted(() => {
  void loadDepartments()
  void loadList()
})
</script>

<template>
  <div class="check-admin" :class="{ 'admin-embedded-surface': embedded }">
    <PageHeader
      v-if="!embedded"
      title="检查项目"
      description="维护医生「开立检查检验」可选项目。检查类型（检查/检验/处置）即项目业务分类，与开单下拉一致。"
      eyebrow="管理端"
    />

    <div v-if="embedded" class="admin-section-header">
      <div class="admin-section-header__text">
        <h3>项目目录</h3>
        <p>维护医生「开立检查检验」可选项目，与开单下拉数据一致。</p>
      </div>
    </div>

    <div
      class="check-admin__panel"
      :class="embedded ? 'admin-tab-pane' : 'check-admin__panel--standalone'"
    >
      <div class="check-admin__toolbar" :class="{ 'admin-embedded-toolbar': embedded }">
        <ElInput
          v-model="keyword"
          clearable
          placeholder="搜索编码或名称"
          class="check-admin__search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        >
          <template #prefix>
            <ElIcon><Search /></ElIcon>
          </template>
        </ElInput>
        <ElSelect v-model="typeFilter" clearable placeholder="检查类型" class="check-admin__filter" @change="onSearch">
          <ElOption label="检查" value="check" />
          <ElOption label="检验" value="inspection" />
          <ElOption label="处置" value="disposal" />
        </ElSelect>
        <ElSpace wrap>
          <ElButton :icon="Refresh" :loading="loading" @click="() => loadList()">刷新</ElButton>
          <ElButton type="primary" :icon="Plus" @click="openCreate">新增项目</ElButton>
        </ElSpace>
      </div>

      <ElTable
        v-loading="loading"
        :data="rows"
        border
        class="admin-data-table"
        style="width: 100%"
        empty-text="暂无项目"
      >
        <ElTableColumn label="#" width="56" align="center">
          <template #default="{ $index }">{{ rowIndex($index) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="techCode" label="编码" width="110" show-overflow-tooltip />
        <ElTableColumn prop="techName" label="名称" min-width="120" show-overflow-tooltip />
        <ElTableColumn label="检查类型" width="96" align="center">
          <template #default="{ row }">
            <ElTag size="small" :type="techTypeTag(castItem(row).techType)">{{ techTypeLabel(castItem(row).techType) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="业务子类" min-width="128" show-overflow-tooltip>
          <template #default="{ row }">
            <ElTag v-if="isCtCategoryCode(castItem(row).aiCategoryCode)" type="primary" size="small" effect="plain">
              CT
            </ElTag>
            {{ aiCategoryLabel(castItem(row).aiCategoryCode) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="techFormat" label="规格" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ castItem(row).techFormat || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="单价（元）" width="110" align="right">
          <template #default="{ row }">{{ formatPrice(castItem(row).techPrice) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="priceType" label="费用分类" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ castItem(row).priceType || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="deptName" label="执行科室" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ castItem(row).deptName || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="220" align="center">
          <template #default="{ row }">
            <ElButton v-if="castItem(row).techType === 'check'" link type="primary" @click="goResultForm(castItem(row))">结果表单</ElButton>
            <ElButton link type="primary" @click="openEdit(castItem(row))">编辑</ElButton>
            <ElButton link type="danger" @click="confirmDelete(castItem(row))">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="check-admin__pagination">
        <ElPagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>
    </div>

    <ElDialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增项目' : '编辑项目'"
      width="520px"
      destroy-on-close
      align-center
    >
      <ElForm ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <ElFormItem label="项目编码" prop="techCode">
          <ElInput v-model="form.techCode" :disabled="editingId != null" placeholder="如 XJCT" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="项目名称" prop="techName">
          <ElInput v-model="form.techName" placeholder="如 胸部CT" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="检查类型" prop="techType">
          <ElSelect v-model="form.techType" class="check-admin__field-full" @change="onTechTypeChange">
            <ElOption label="检查" value="check" />
            <ElOption label="检验" value="inspection" />
            <ElOption label="处置" value="disposal" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="form.techType === 'check' || form.techType === 'inspection'" label="业务子类">
          <ElSelect v-model="form.aiCategoryCode" class="check-admin__field-full" placeholder="选择业务子类">
            <ElOption
              v-for="option in categoryOptionsForType(form.techType)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </ElSelect>
          <p class="check-admin__hint">
            CT 影像项目请选择「CT 影像」分类，执行检查时将进入全屏阅片页。
          </p>
        </ElFormItem>
        <ElFormItem label="规格">
          <ElInput v-model="form.techFormat" placeholder="如 平扫" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="单价" prop="techPrice">
          <ElInputNumber v-model="form.techPrice" :min="0" :precision="2" :step="10" class="check-admin__field-full" />
        </ElFormItem>
        <ElFormItem label="费用分类">
          <ElInput v-model="form.priceType" placeholder="检查费 / 检验费 / 处置费" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="执行科室">
          <ElSelect v-model="form.deptmentId" clearable placeholder="选择科室" class="check-admin__field-full">
            <ElOption v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submitForm">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.check-admin {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.check-admin__panel--standalone {
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
}

.check-admin__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.check-admin .admin-embedded-toolbar {
  margin-block-end: 0;
}

.check-admin__search {
  width: min(280px, 100%);
}

.check-admin__filter {
  width: 140px;
}

.check-admin__field-full {
  width: 100%;
}

.check-admin__hint {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.check-admin__pagination {
  display: flex;
  justify-content: flex-end;
  margin-block-start: var(--space-4);
}
</style>

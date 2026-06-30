<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useClientPagination } from '@/modules/admin/composables/useClientPagination'
import { registrationApi } from '@/shared/api/modules/registration'
import { DOSAGE_FORMS } from '@/shared/constants/pharmacy'
import type { DepartmentOption, RegistLevelOption, SettleCategoryOption } from '@/shared/types/registration'
import type { DrugOption } from '@/shared/types/pharmacy'

type TabKey = 'departments' | 'registLevels' | 'settleCategories' | 'drugs'

const DEPT_TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '临床科室', value: '临床科室' },
  { label: '医技科室', value: '医技科室' },
] as const

const route = useRoute()
const activeTab = ref<TabKey>('departments')

const loading = ref(false)
const drugsLoading = ref(false)
const departments = ref<DepartmentOption[]>([])
const registLevels = ref<RegistLevelOption[]>([])
const settleCategories = ref<SettleCategoryOption[]>([])
const drugs = ref<DrugOption[]>([])
const drugPage = ref(1)
const drugPageSize = ref(20)
const drugTotal = ref(0)
const drugTotalPages = ref(0)

const deptTypeFilter = ref('')
const drugKeyword = ref('')
const drugDosageForm = ref('')
const drugCategory = ref('')
const drugCategoryOptions = ref<string[]>([])

const drugLoadError = ref('')

const {
  page: deptPage,
  size: deptPageSize,
  total: deptTotal,
  totalPages: deptTotalPages,
  pagedRecords: deptPagedRecords,
  onPageChange: onDeptPageChange,
  onPageSizeChange: onDeptPageSizeChange,
  resetPage: resetDeptPage,
} = useClientPagination(departments)

// 科室编辑/新增
const deptDialogVisible = ref(false)
const editingDeptId = ref<number | null>(null)
const deptForm = reactive({
  code: '',
  name: '',
  type: '临床科室',
  description: '',
})

// 挂号级别编辑/新增
const levelDialogVisible = ref(false)
const editingLevelId = ref<number | null>(null)
const levelForm = reactive({
  code: '',
  name: '',
  fee: 0,
  quota: 0,
})

// 药品详情（只读弹窗）
const drugDialogVisible = ref(false)
const selectedDrug = ref<DrugOption | null>(null)

async function loadDepartments() {
  departments.value = deptTypeFilter.value
    ? await registrationApi.departments(deptTypeFilter.value)
    : await registrationApi.departments()
  resetDeptPage()
}

async function loadBaseData() {
  loading.value = true
  try {
    const [level, settle] = await Promise.all([
      registrationApi.registLevels(),
      registrationApi.settleCategories(),
    ])
    registLevels.value = level
    settleCategories.value = settle
    await loadDepartments()
  } finally {
    loading.value = false
  }
}

async function loadDrugCategories() {
  try {
    drugCategoryOptions.value = (await registrationApi.adminDrugCategories()) ?? []
  } catch (error) {
    drugCategoryOptions.value = []
    console.error('加载药品分类失败', error)
  }
}

async function loadDrugs() {
  drugsLoading.value = true
  drugLoadError.value = ''
  try {
    const page = await registrationApi.adminDrugs({
      keyword: drugKeyword.value || undefined,
      dosageForm: drugDosageForm.value || undefined,
      category: drugCategory.value || undefined,
      page: drugPage.value,
      size: drugPageSize.value,
    })
    drugs.value = page.records ?? []
    drugTotal.value = page.total ?? 0
    drugTotalPages.value = page.totalPages ?? 0
    drugPage.value = page.page ?? drugPage.value
    drugPageSize.value = page.size ?? drugPageSize.value
  } catch (error) {
    drugs.value = []
    drugTotal.value = 0
    drugTotalPages.value = 0
    drugLoadError.value = error instanceof Error ? error.message : '药品目录加载失败'
    ElMessage.error(drugLoadError.value)
  } finally {
    drugsLoading.value = false
  }
}

function searchDrugs() {
  drugPage.value = 1
  void loadDrugs()
}

function onDrugPageChange(page: number) {
  drugPage.value = page
  void loadDrugs()
}

function onDrugPageSizeChange(size: number) {
  drugPageSize.value = size
  drugPage.value = 1
  void loadDrugs()
}

async function loadDrugTab() {
  await Promise.all([loadDrugCategories(), loadDrugs()])
}

async function loadAll() {
  await loadBaseData()
  if (activeTab.value === 'drugs') {
    await loadDrugTab()
  }
}

function onDeptTypeChange() {
  void loadDepartments()
}

watch(
  activeTab,
  (tab) => {
    if (tab === 'drugs' && !drugsLoading.value) {
      void loadDrugTab()
    }
  },
  { immediate: true },
)

// ==================== 科室 CRUD ====================
function openCreateDept() {
  editingDeptId.value = null
  deptForm.code = ''
  deptForm.name = ''
  deptForm.type = '临床科室'
  deptForm.description = ''
  deptDialogVisible.value = true
}

function openEditDept(row: DepartmentOption) {
  editingDeptId.value = row.id
  deptForm.code = row.code || ''
  deptForm.name = row.name
  deptForm.type = row.type || '临床科室'
  deptForm.description = row.description || ''
  deptDialogVisible.value = true
}

async function saveDept() {
  if (!deptForm.name.trim() || !deptForm.code.trim()) {
    ElMessage.warning('请填写科室名称和编码')
    return
  }
  const payload = {
    code: deptForm.code.trim(),
    name: deptForm.name.trim(),
    type: deptForm.type,
    description: deptForm.description.trim(),
  }
  if (editingDeptId.value == null) {
    await registrationApi.post('/registration/departments', payload)
    ElMessage.success('科室已新增')
  } else {
    await registrationApi.put(`/registration/departments/${editingDeptId.value}`, payload)
    ElMessage.success('科室已更新')
  }
  deptDialogVisible.value = false
  await loadDepartments()
}

async function removeDept(row: DepartmentOption) {
  await ElMessageBox.confirm(`确认删除科室「${row.name}」？`, '删除确认', { type: 'warning' })
  await registrationApi.delete(`/registration/departments/${row.id}`)
  ElMessage.success('已删除')
  await loadDepartments()
}

// ==================== 挂号级别 CRUD ====================
function openCreateLevel() {
  editingLevelId.value = null
  levelForm.code = ''
  levelForm.name = ''
  levelForm.fee = 0
  levelForm.quota = 0
  levelDialogVisible.value = true
}

function openEditLevel(row: RegistLevelOption) {
  editingLevelId.value = row.id
  levelForm.code = row.id != null ? String(row.id) : ''
  levelForm.name = row.name
  levelForm.fee = row.price ?? 0
  levelForm.quota = 0
  levelDialogVisible.value = true
}

async function saveLevel() {
  if (!levelForm.name.trim() || !levelForm.code.trim()) {
    ElMessage.warning('请填写级别名称和编码')
    return
  }
  const payload = {
    code: levelForm.code.trim(),
    name: levelForm.name.trim(),
    price: levelForm.fee,
    quota: levelForm.quota,
  }
  if (editingLevelId.value == null) {
    await registrationApi.post('/registration/regist-levels', payload)
    ElMessage.success('挂号级别已新增')
  } else {
    await registrationApi.put(`/registration/regist-levels/${editingLevelId.value}`, payload)
    ElMessage.success('挂号级别已更新')
  }
  levelDialogVisible.value = false
  await loadBaseData()
}

async function removeLevel(row: RegistLevelOption) {
  await ElMessageBox.confirm(`确认删除挂号级别「${row.name}」？`, '删除确认', { type: 'warning' })
  await registrationApi.delete(`/registration/regist-levels/${row.id}`)
  ElMessage.success('已删除')
  await loadBaseData()
}

function openDrugDetail(row: DrugOption) {
  selectedDrug.value = row
  drugDialogVisible.value = true
}

function drugStatusLabel(status?: number) {
  return status === 0 ? '已停用' : '启用'
}

function drugStatusTone(status?: number) {
  return status === 0 ? 'neutral' : 'success'
}

function castDrug(row: unknown): DrugOption {
  return row as DrugOption
}

function castDept(row: unknown): DepartmentOption {
  return row as DepartmentOption
}

function castLevel(row: unknown): RegistLevelOption {
  return row as RegistLevelOption
}

function resolveInitialTab(): TabKey {
  const tab = route.query.tab
  if (tab === 'departments' || tab === 'registLevels' || tab === 'settleCategories' || tab === 'drugs') {
    return tab
  }
  return 'departments'
}

onMounted(async () => {
  activeTab.value = resolveInitialTab()
  await loadBaseData()
})
</script>

<template>
  <div class="master-data-page u-page-grid">
    <PageHeader
      title="基础资料管理"
      description="维护科室、挂号级别、结算类别与药品目录。检查/检验项目请前往「医技项目」管理。"
      eyebrow="Role Admin / Master Data"
    >
      <template #actions>
        <ElButton @click="loadAll">刷新</ElButton>
        <ElButton
          v-if="activeTab === 'departments'"
          type="primary"
          @click="openCreateDept"
        >新增科室</ElButton>
        <ElButton
          v-else-if="activeTab === 'registLevels'"
          type="primary"
          @click="openCreateLevel"
        >新增挂号级别</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="panel admin-shell-card admin-panel-surface" v-loading="loading">
      <ElTabs v-model="activeTab" class="master-data-tabs">
        <!-- 科室 -->
        <ElTabPane label="科室资料" name="departments">
          <div class="admin-tab-pane">
            <div class="admin-section-header">
              <div class="admin-section-header__text">
                <h3>科室资料</h3>
                <p>来源于 registration-service · department 表，支持新增、编辑、删除。</p>
              </div>
              <StatusTag tone="primary">{{ deptTotal }} 条</StatusTag>
            </div>
            <div class="filter-bar">
              <ElSelect
                v-model="deptTypeFilter"
                placeholder="科室类型"
                clearable
                class="filter-bar__type"
                @change="onDeptTypeChange"
              >
                <ElOption
                  v-for="opt in DEPT_TYPE_OPTIONS"
                  :key="opt.value || 'all'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </div>
            <ElTable :data="deptPagedRecords" class="admin-data-table" border>
            <ElTableColumn prop="code" label="科室编码" min-width="120" />
            <ElTableColumn prop="name" label="科室名称" min-width="140" />
            <ElTableColumn prop="type" label="类型" min-width="110" />
            <ElTableColumn prop="description" label="简介" min-width="280" show-overflow-tooltip />
            <ElTableColumn label="操作" min-width="160" align="center">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openEditDept(castDept(row))">编辑</ElButton>
                <ElButton link type="danger" @click="removeDept(castDept(row))">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
            <div v-if="deptTotal > 0" class="admin-pagination-bar">
              <p class="table-footer">
                共 {{ deptTotal }} 个科室
                <template v-if="deptTotalPages > 0">，第 {{ deptPage }} / {{ deptTotalPages }} 页</template>
              </p>
              <ElPagination
                v-model:current-page="deptPage"
                v-model:page-size="deptPageSize"
                :total="deptTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @current-change="onDeptPageChange"
                @size-change="onDeptPageSizeChange"
              />
            </div>
          </div>
        </ElTabPane>

        <!-- 挂号级别 -->
        <ElTabPane label="挂号级别" name="registLevels">
          <div class="admin-tab-pane">
            <div class="admin-section-header">
              <div class="admin-section-header__text">
                <h3>挂号级别</h3>
                <p>来源于 registration-service · regist_level 表，支持新增、编辑、删除。</p>
              </div>
              <StatusTag tone="primary">{{ registLevels.length }} 条</StatusTag>
            </div>
            <ElTable :data="registLevels" class="admin-data-table" border>
            <ElTableColumn prop="code" label="编码" min-width="100" />
            <ElTableColumn prop="name" label="名称" min-width="140" />
            <ElTableColumn label="挂号费" min-width="100" align="right">
              <template #default="{ row }">
                <span class="price-value">¥ {{ row.price ?? 0 }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" min-width="160" align="center">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openEditLevel(castLevel(row))">编辑</ElButton>
                <ElButton link type="danger" @click="removeLevel(castLevel(row))">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          </div>
        </ElTabPane>

        <!-- 结算类别 -->
        <ElTabPane label="结算类别" name="settleCategories">
          <div class="admin-tab-pane">
            <div class="admin-section-header">
              <div class="admin-section-header__text">
                <h3>结算类别</h3>
                <p>来源于 registration-service · settle_category 表，当前版本只读展示。</p>
              </div>
              <StatusTag tone="primary">{{ settleCategories.length }} 条</StatusTag>
            </div>
            <ElTable :data="settleCategories" class="admin-data-table" border>
            <ElTableColumn prop="code" label="编码" min-width="120" />
            <ElTableColumn prop="name" label="名称" min-width="140" />
            <ElTableColumn prop="description" label="说明" min-width="220" show-overflow-tooltip />
          </ElTable>
          </div>
        </ElTabPane>

        <!-- 药品 -->
        <ElTabPane label="药品目录" name="drugs">
          <div class="admin-tab-pane">
            <div class="admin-section-header">
              <div class="admin-section-header__text">
                <h3>药品目录</h3>
                <p>来源于 registration-service · drug_info 表，展示完整药品主数据（点击行查看详情）。</p>
              </div>
              <StatusTag tone="primary">{{ drugTotal }} 条</StatusTag>
            </div>
            <div class="filter-bar">
              <div class="filter-bar__inputs">
                <ElInput v-model="drugKeyword" placeholder="药品名称 / 通用名" clearable class="filter-bar__keyword" />
                <ElSelect v-model="drugDosageForm" placeholder="剂型" clearable class="filter-bar__select">
                  <ElOption v-for="form in DOSAGE_FORMS" :key="form" :label="form" :value="form" />
                </ElSelect>
                <ElSelect v-model="drugCategory" placeholder="分类" clearable class="filter-bar__select">
                  <ElOption v-for="c in drugCategoryOptions" :key="c" :label="c" :value="c" />
                </ElSelect>
              </div>
              <ElButton type="primary" @click="searchDrugs">查询</ElButton>
            </div>
            <ElEmpty
              v-if="!drugsLoading && drugLoadError"
              :description="drugLoadError"
            >
              <ElButton type="primary" @click="loadDrugTab">重试</ElButton>
            </ElEmpty>
            <ElEmpty
              v-else-if="!drugsLoading && drugTotal === 0"
              description="暂无药品数据"
            />
            <ElTable
              v-else
              v-loading="drugsLoading"
              :data="drugs"
              class="admin-data-table admin-data-table--clickable"
              border
              @row-click="(row) => openDrugDetail(castDrug(row))"
            >
              <ElTableColumn label="药品名称" min-width="180" fixed="left">
                <template #default="{ row }">
                  <div class="drug-name-cell">
                    <span>{{ row.name }}</span>
                    <ElTag v-if="row.status === 0" type="info" size="small">已停用</ElTag>
                    <ElTag v-if="row.category" size="small" effect="plain">{{ row.category }}</ElTag>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="genericName" label="通用名" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.genericName || '—' }}</template>
              </ElTableColumn>
              <ElTableColumn prop="brandName" label="商品品牌" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.brandName || '—' }}</template>
              </ElTableColumn>
              <ElTableColumn prop="dosageForm" label="剂型" min-width="90">
                <template #default="{ row }">{{ row.dosageForm || '—' }}</template>
              </ElTableColumn>
              <ElTableColumn prop="specification" label="规格" min-width="130" show-overflow-tooltip />
              <ElTableColumn prop="unit" label="单位" min-width="72" align="center">
                <template #default="{ row }">{{ row.unit || '—' }}</template>
              </ElTableColumn>
              <ElTableColumn label="单价" min-width="96" align="right">
                <template #default="{ row }">
                  <span class="price-value">¥ {{ row.price ?? 0 }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="approvalNumber" label="批准文号" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.approvalNumber || '—' }}</template>
              </ElTableColumn>
              <ElTableColumn prop="manufacturer" label="生产厂家" min-width="150" show-overflow-tooltip />
              <ElTableColumn label="库存" min-width="110">
                <template #default="{ row }">
                  <StatusTag
                    :tone="(row.stockQuantity ?? 0) <= (row.lowStockThreshold ?? 0) ? 'danger' : 'success'"
                  >
                    {{ row.stockQuantity ?? 0 }} {{ row.unit || '' }}
                  </StatusTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="状态" width="88" align="center">
                <template #default="{ row }">
                  <StatusTag :tone="drugStatusTone(row.status)">
                    {{ drugStatusLabel(row.status) }}
                  </StatusTag>
                </template>
              </ElTableColumn>
            </ElTable>
            <div v-if="drugTotal > 0" class="admin-pagination-bar">
              <p class="table-footer">
                共 {{ drugTotal }} 种药品
                <template v-if="drugTotalPages > 0">，第 {{ drugPage }} / {{ drugTotalPages }} 页</template>
              </p>
              <ElPagination
                v-model:current-page="drugPage"
                v-model:page-size="drugPageSize"
                :total="drugTotal"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                background
                @current-change="onDrugPageChange"
                @size-change="onDrugPageSizeChange"
              />
            </div>
          </div>
        </ElTabPane>
      </ElTabs>
    </GlassCard>

    <!-- 科室编辑弹窗 -->
    <ElDialog
      v-model="deptDialogVisible"
      :title="editingDeptId == null ? '新增科室' : '编辑科室'"
      width="520px"
    >
      <ElForm label-position="top">
        <ElFormItem label="科室编码">
          <ElInput v-model="deptForm.code" placeholder="如 NK" />
        </ElFormItem>
        <ElFormItem label="科室名称">
          <ElInput v-model="deptForm.name" placeholder="如 内科" />
        </ElFormItem>
        <ElFormItem label="科室类型">
          <ElSelect v-model="deptForm.type" class="full-width">
            <ElOption label="临床科室" value="临床科室" />
            <ElOption label="医技科室" value="医技科室" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="简介">
          <ElInput v-model="deptForm.description" type="textarea" :rows="3" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="deptDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveDept">保存</ElButton>
      </template>
    </ElDialog>

    <!-- 挂号级别编辑弹窗 -->
    <ElDialog
      v-model="levelDialogVisible"
      :title="editingLevelId == null ? '新增挂号级别' : '编辑挂号级别'"
      width="520px"
    >
      <ElForm label-position="top">
        <ElFormItem label="级别编码">
          <ElInput v-model="levelForm.code" placeholder="如 PT" />
        </ElFormItem>
        <ElFormItem label="级别名称">
          <ElInput v-model="levelForm.name" placeholder="如 普通号" />
        </ElFormItem>
        <ElFormItem label="挂号费（元）">
          <ElInputNumber v-model="levelForm.fee" :min="0" :controls="false" class="full-width" />
        </ElFormItem>
        <ElFormItem label="每日限额">
          <ElInputNumber v-model="levelForm.quota" :min="0" :controls="false" class="full-width" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="levelDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveLevel">保存</ElButton>
      </template>
    </ElDialog>

    <!-- 药品详情弹窗（只读） -->
    <ElDialog v-model="drugDialogVisible" title="药品详情" width="720px">
      <template v-if="selectedDrug">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="药品名称">{{ selectedDrug.name }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">{{ drugStatusLabel(selectedDrug.status) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="通用名">{{ selectedDrug.genericName || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="商品品牌">{{ selectedDrug.brandName || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="分类">{{ selectedDrug.category || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="剂型">{{ selectedDrug.dosageForm || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="规格">{{ selectedDrug.specification || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="单位">{{ selectedDrug.unit || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="单价">¥ {{ selectedDrug.price ?? 0 }}</ElDescriptionsItem>
          <ElDescriptionsItem label="批准文号">{{ selectedDrug.approvalNumber || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="生产厂家" :span="2">{{ selectedDrug.manufacturer || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="库存">
            {{ selectedDrug.stockQuantity ?? 0 }} {{ selectedDrug.unit || '' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="低库存阈值">
            {{ selectedDrug.lowStockThreshold ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="储存条件" :span="2">
            {{ selectedDrug.storageConditions || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="用药指导" :span="2">
            {{ selectedDrug.instructions || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="禁忌" :span="2">
            {{ selectedDrug.contraindications || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="不良反应" :span="2">
            {{ selectedDrug.adverseReactions || '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.panel {
  padding: var(--space-5);
}

.admin-data-table--clickable :deep(.el-table__body tr) {
  cursor: pointer;
}

.full-width {
  width: 100%;
}

.price-value {
  font-weight: 600;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.filter-bar__inputs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
  min-width: 0;
}

.filter-bar__type {
  width: 180px;
}

.filter-bar__keyword {
  width: min(280px, 100%);
}

.filter-bar__select {
  width: 140px;
}

.drug-name-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}
</style>

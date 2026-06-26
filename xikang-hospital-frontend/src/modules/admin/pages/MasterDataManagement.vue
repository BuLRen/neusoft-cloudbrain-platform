<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { registrationApi } from '@/shared/api/modules/registration'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type { DepartmentOption, RegistLevelOption, SettleCategoryOption } from '@/shared/types/registration'
import type { DrugOption } from '@/shared/types/pharmacy'

type TabKey = 'departments' | 'registLevels' | 'settleCategories' | 'drugs'

const activeTab = ref<TabKey>('departments')

const loading = ref(false)
const departments = ref<DepartmentOption[]>([])
const registLevels = ref<RegistLevelOption[]>([])
const settleCategories = ref<SettleCategoryOption[]>([])
const drugs = ref<DrugOption[]>([])

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

async function loadAll() {
  loading.value = true
  try {
    const [dept, level, settle, drug] = await Promise.all([
      registrationApi.departments(),
      registrationApi.registLevels(),
      registrationApi.settleCategories(),
      pharmacyApi.drugs(),
    ])
    departments.value = dept
    registLevels.value = level
    settleCategories.value = settle
    drugs.value = drug
  } finally {
    loading.value = false
  }
}

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
  await loadAll()
}

async function removeDept(row: DepartmentOption) {
  await ElMessageBox.confirm(`确认删除科室「${row.name}」？`, '删除确认', { type: 'warning' })
  await registrationApi.delete(`/registration/departments/${row.id}`)
  ElMessage.success('已删除')
  await loadAll()
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
  await loadAll()
}

async function removeLevel(row: RegistLevelOption) {
  await ElMessageBox.confirm(`确认删除挂号级别「${row.name}」？`, '删除确认', { type: 'warning' })
  await registrationApi.delete(`/registration/regist-levels/${row.id}`)
  ElMessage.success('已删除')
  await loadAll()
}

function openDrugDetail(row: DrugOption) {
  selectedDrug.value = row
  drugDialogVisible.value = true
}

onMounted(loadAll)
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
              <StatusTag tone="primary">{{ departments.length }} 条</StatusTag>
            </div>
            <ElTable :data="departments" class="admin-data-table" border>
            <ElTableColumn prop="code" label="科室编码" min-width="120" />
            <ElTableColumn prop="name" label="科室名称" min-width="140" />
            <ElTableColumn prop="type" label="类型" min-width="110" />
            <ElTableColumn prop="description" label="简介" min-width="280" show-overflow-tooltip />
            <ElTableColumn label="操作" min-width="160" align="center">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openEditDept(row)">编辑</ElButton>
                <ElButton link type="danger" @click="removeDept(row)">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
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
                <ElButton link type="primary" @click="openEditLevel(row)">编辑</ElButton>
                <ElButton link type="danger" @click="removeLevel(row)">删除</ElButton>
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
                <p>来源于 pharmacy-service · drug_info 表，当前版本只读展示（点击行查看详情）。</p>
              </div>
              <StatusTag tone="primary">{{ drugs.length }} 条</StatusTag>
            </div>
            <ElTable :data="drugs" class="admin-data-table admin-data-table--clickable" border @row-click="openDrugDetail">
            <ElTableColumn prop="name" label="药品名称" min-width="160" />
            <ElTableColumn prop="specification" label="规格" min-width="140" />
            <ElTableColumn prop="dosageForm" label="剂型" min-width="100" />
            <ElTableColumn prop="manufacturer" label="生产厂家" min-width="180" show-overflow-tooltip />
            <ElTableColumn label="单价" min-width="100" align="right">
              <template #default="{ row }">
                <span class="price-value">¥ {{ row.price ?? 0 }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="库存" min-width="120">
              <template #default="{ row }">
                <StatusTag
                  :tone="(row.stockQuantity ?? 0) <= (row.lowStockThreshold ?? 0) ? 'danger' : 'success'"
                >
                  {{ row.stockQuantity ?? 0 }} {{ row.unit || '' }}
                </StatusTag>
              </template>
            </ElTableColumn>
          </ElTable>
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
    <ElDialog v-model="drugDialogVisible" title="药品详情" width="560px">
      <template v-if="selectedDrug">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="药品名称">{{ selectedDrug.name }}</ElDescriptionsItem>
          <ElDescriptionsItem label="规格">{{ selectedDrug.specification || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="剂型">{{ selectedDrug.dosageForm || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="单位">{{ selectedDrug.unit || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="生产厂家">{{ selectedDrug.manufacturer || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="单价">¥ {{ selectedDrug.price ?? 0 }}</ElDescriptionsItem>
          <ElDescriptionsItem label="库存">
            {{ selectedDrug.stockQuantity ?? 0 }} {{ selectedDrug.unit || '' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="低库存阈值">
            {{ selectedDrug.lowStockThreshold ?? 0 }}
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
</style>

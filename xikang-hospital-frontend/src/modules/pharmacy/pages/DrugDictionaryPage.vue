<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import DrugFormDialog from '@/modules/pharmacy/components/DrugFormDialog.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import { PAGE_SIZE_DEFAULT } from '@/shared/constants/pharmacy'
import type { DrugOption } from '@/shared/types/pharmacy'

const drugs = ref<DrugOption[]>([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const category = ref('')
const categoryOptions = ref<string[]>([])

const page = ref(1)
const pageSize = ref(PAGE_SIZE_DEFAULT)

const formVisible = ref(false)
const editingDrug = ref<DrugOption | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await pharmacyApi.drugs({
      keyword: keyword.value || undefined,
      category: category.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    drugs.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 筛选条件变化：重置到第 1 页再查 */
async function search() {
  page.value = 1
  await load()
}

function onPageChange(p: number) {
  page.value = p
  void load()
}

function onSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  void load()
}

async function loadFilters() {
  categoryOptions.value = await pharmacyApi.categories()
}

function openCreate() {
  editingDrug.value = null
  formVisible.value = true
}

function openEdit(drug: DrugOption) {
  editingDrug.value = { ...drug }
  formVisible.value = true
}

async function toggleStatus(drug: DrugOption) {
  const next = drug.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确认${action}药品「${drug.drugName}」？停用后该药品不再出现在发药流程中。`,
      `${action}确认`,
      { type: 'warning', confirmButtonText: `确认${action}`, cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await pharmacyApi.updateDrug(drug.id, { ...drug, status: next })
  ElMessage.success(`已${action}`)
  await load()
}

async function removeDrug(drug: DrugOption) {
  try {
    await ElMessageBox.confirm(
      `确认删除药品「${drug.drugName}」？此操作将软删除（status=0），不会清除历史流水。`,
      '删除确认',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await pharmacyApi.deleteDrug(drug.id)
  ElMessage.success('已删除')
  await load()
}

function onSaved() {
  void load()
}

onMounted(() => {
  void load()
  void loadFilters()
})
</script>

<template>
  <div class="dict-page u-page-grid">
    <PageHeader
      title="药品字典"
      description="维护药品主数据：新增、改价、停用、删除。所有发药、库存、统计都依赖这里的字典。"
      eyebrow="Role B / Pharmacy · ⑤"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
        <ElButton type="primary" @click="openCreate">+ 新增药品</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="dict-card">
      <div class="filter-bar">
        <div class="filter-bar__inputs">
          <ElInput
            v-model="keyword"
            placeholder="药品名称 / 编码 / 助记码"
            clearable
            class="field-grow"
            @keyup.enter="search"
          />
          <ElSelect v-model="category" placeholder="分类" clearable class="field-fixed">
            <ElOption v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </ElSelect>
        </div>
        <ElButton type="primary" @click="search">查询</ElButton>
      </div>

      <div class="section-title">
        <h3>药品主数据</h3>
        <StatusTag tone="primary">{{ total }} 条</StatusTag>
      </div>

      <ElTable v-loading="loading" :data="drugs" row-key="id">
        <ElTableColumn prop="drugName" label="药品名称" min-width="180">
          <template #default="{ row }">
            <div class="name-cell">
              <span>{{ row.drugName }}</span>
              <ElTag v-if="row.status === 0" type="info" size="small">已停用</ElTag>
              <ElTag v-if="row.drugType" size="small" effect="plain">{{ row.drugType }}</ElTag>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="drugDosage" label="剂型" min-width="100" />
        <ElTableColumn prop="drugFormat" label="规格" min-width="130" />
        <ElTableColumn prop="drugUnit" label="单位" min-width="80" />
        <ElTableColumn prop="drugPrice" label="单价" min-width="90" align="right" />
        <ElTableColumn prop="manufacturer" label="厂家" min-width="150" show-overflow-tooltip />
        <ElTableColumn label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <ElButton link size="small" @click="openEdit(row as DrugOption)">编辑</ElButton>
            <ElButton
              link
              size="small"
              :type="(row as DrugOption).status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row as DrugOption)"
            >
              {{ (row as DrugOption).status === 1 ? '停用' : '启用' }}
            </ElButton>
            <ElButton link size="small" type="danger" @click="removeDrug(row as DrugOption)">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElEmpty v-if="!loading && drugs.length === 0" description="尚未维护任何药品，点右上角新增" />

      <div class="pagination-row">
        <ElPagination
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          small
          background
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </GlassCard>

    <DrugFormDialog v-model="formVisible" :drug="editingDrug" @saved="onSaved" />
  </div>
</template>

<style scoped>
.dict-card {
  padding: var(--space-5);
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block: var(--space-5) var(--space-4);
}

.section-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-block-end: var(--space-4);
  border-block-end: 1px solid var(--color-border);
}

.filter-bar__inputs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  flex: 1 1 360px;
}

.field-grow {
  flex: 1 1 200px;
  min-width: 160px;
}

.field-fixed {
  width: 140px;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-block-start: var(--space-4);
}

@media (max-width: 1080px) {
  .field-grow,
  .field-fixed {
    width: 100%;
    flex-basis: 100%;
  }
}
</style>

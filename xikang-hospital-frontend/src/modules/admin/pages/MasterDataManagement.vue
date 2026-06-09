<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElMessage,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { masterDataSections } from '@/shared/mock/admin'
import type { MasterDataRecord } from '@/shared/types/admin'

const activeTab = ref<'departments' | 'doctors' | 'drugs' | 'items'>('departments')
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const records = ref<Record<string, MasterDataRecord[]>>({
  departments: masterDataSections.departments.map((item) => ({ ...item })),
  doctors: masterDataSections.doctors.map((item) => ({ ...item })),
  drugs: masterDataSections.drugs.map((item) => ({ ...item })),
  items: masterDataSections.items.map((item) => ({ ...item })),
})

const tabs = [
  { key: 'departments', label: '科室资料' },
  { key: 'doctors', label: '医生资料' },
  { key: 'drugs', label: '药品目录' },
  { key: 'items', label: '检查检验项目' },
] as const

const form = reactive<MasterDataRecord>({
  id: 0,
  name: '',
  code: '',
  category: '',
  status: 'enabled',
  owner: '',
  description: '',
})

const currentRecords = computed(() => records.value[activeTab.value] || [])
const currentLabel = computed(() => tabs.find((item) => item.key === activeTab.value)?.label || '基础资料')

function statusTone(status: MasterDataRecord['status']) {
  return status === 'enabled' ? 'success' : 'warning'
}

function openCreate() {
  editingId.value = null
  form.id = 0
  form.name = ''
  form.code = ''
  form.category = currentLabel.value.replace('资料', '').replace('目录', '')
  form.status = 'enabled'
  form.owner = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(record: MasterDataRecord) {
  editingId.value = record.id
  Object.assign(form, record)
  dialogVisible.value = true
}

function saveRecord() {
  if (!form.name.trim() || !form.code.trim()) {
    ElMessage.warning('请先填写名称和编码')
    return
  }

  const target = records.value[activeTab.value]
  if (editingId.value == null) {
    target.unshift({
      ...form,
      id: Date.now(),
      name: form.name.trim(),
      code: form.code.trim(),
      owner: form.owner.trim(),
      description: form.description.trim(),
    })
    ElMessage.success('资料已新增')
  } else {
    const index = target.findIndex((item) => item.id === editingId.value)
    if (index >= 0) {
      target[index] = {
        ...form,
        name: form.name.trim(),
        code: form.code.trim(),
        owner: form.owner.trim(),
        description: form.description.trim(),
      }
      ElMessage.success('资料已更新')
    }
  }
  dialogVisible.value = false
}

function toggleStatus(record: MasterDataRecord) {
  record.status = record.status === 'enabled' ? 'disabled' : 'enabled'
  ElMessage.success(record.status === 'enabled' ? '已启用' : '已停用')
}
</script>

<template>
  <div class="master-data-page u-page-grid">
    <PageHeader
      title="基础资料管理"
      description="统一维护管理员端关注的主数据，包括科室、医生、药品与检查检验项目目录。"
      eyebrow="Role Admin / Master Data"
    >
      <template #actions>
        <ElButton @click="activeTab = 'departments'">回到科室资料</ElButton>
        <ElButton type="primary" @click="openCreate">新增资料</ElButton>
      </template>
    </PageHeader>

    <GlassCard class="panel">
      <ElTabs v-model="activeTab">
        <ElTabPane v-for="tab in tabs" :key="tab.key" :label="tab.label" :name="tab.key">
          <div class="section-header">
            <div>
              <h3>{{ tab.label }}</h3>
              <p>前端已提供完整列表、编辑与启停交互，后续可直接替换为真实接口。</p>
            </div>
            <StatusTag tone="primary">{{ currentRecords.length }} 条</StatusTag>
          </div>

          <ElTable :data="currentRecords">
            <ElTableColumn prop="name" label="名称" min-width="180" />
            <ElTableColumn prop="code" label="编码" min-width="160" />
            <ElTableColumn prop="category" label="分类" min-width="120" />
            <ElTableColumn prop="owner" label="归属" min-width="120" />
            <ElTableColumn label="状态" min-width="100">
              <template #default="{ row }">
                <StatusTag :tone="statusTone(row.status)">{{ row.status === 'enabled' ? '启用中' : '已停用' }}</StatusTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="description" label="说明" min-width="220" />
            <ElTableColumn label="操作" min-width="180" fixed="right">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
                <ElButton link :type="row.status === 'enabled' ? 'danger' : 'success'" @click="toggleStatus(row)">
                  {{ row.status === 'enabled' ? '停用' : '启用' }}
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </GlassCard>

    <ElDialog v-model="dialogVisible" :title="editingId == null ? `新增${currentLabel}` : `编辑${currentLabel}`" width="560px">
      <ElForm label-position="top">
        <ElFormItem label="名称">
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem label="编码">
          <ElInput v-model="form.code" />
        </ElFormItem>
        <ElFormItem label="分类">
          <ElInput v-model="form.category" />
        </ElFormItem>
        <ElFormItem label="归属">
          <ElInput v-model="form.owner" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="form.status" class="field">
            <ElOption label="启用" value="enabled" />
            <ElOption label="停用" value="disabled" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="说明">
          <ElInput v-model="form.description" type="textarea" :rows="3" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveRecord">保存</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.panel {
  padding: var(--space-5);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-4);
}

.section-header p {
  margin-block-start: var(--space-2);
  color: var(--color-text-muted);
  line-height: 1.7;
}

.field {
  width: 100%;
}
</style>

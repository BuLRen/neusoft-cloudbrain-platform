<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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
  ElTable,
  ElTableColumn,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import {
  adminApi,
  type CheckEquipmentPayload,
  type DepartmentOption,
  type MedicalTechnologyItem,
} from '@/shared/api/modules/admin'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rows = ref<MedicalTechnologyItem[]>([])
const departments = ref<DepartmentOption[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive<CheckEquipmentPayload>({
  techCode: '',
  techName: '',
  techFormat: '',
  techPrice: 0,
  techType: 'check',
  priceType: '检查费',
  deptmentId: undefined,
})

const formRules = {
  techCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  techName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  techPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
}

const formRef = ref<InstanceType<typeof ElForm>>()

function resetForm() {
  form.techCode = ''
  form.techName = ''
  form.techFormat = ''
  form.techPrice = 0
  form.techType = 'check'
  form.priceType = '检查费'
  form.deptmentId = undefined
}

async function loadDepartments() {
  try {
    departments.value = await adminApi.departments()
  } catch {
    departments.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    rows.value = await adminApi.listCheckEquipment(keyword.value.trim() || undefined)
  } finally {
    loading.value = false
  }
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
  form.techType = 'check'
  form.priceType = row.priceType || '检查费'
  form.deptmentId = row.deptmentId
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (editingId.value == null) {
      await adminApi.createCheckEquipment({ ...form })
      ElMessage.success('检查项目已新增')
    } else {
      const { techCode: _code, ...payload } = form
      await adminApi.updateCheckEquipment(editingId.value, payload)
      ElMessage.success('检查项目已更新')
    }
    dialogVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
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
    await adminApi.deleteCheckEquipment(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch {
    // http 层已提示错误信息
  }
}

onMounted(() => {
  void loadDepartments()
  void loadList()
})
</script>

<template>
  <div class="check-equipment u-page-grid">
    <PageHeader
      title="检查设备"
      description="维护检查类医技项目目录（编码、名称、规格、价格、执行科室）。医生开单与 AI 推荐均引用此数据。"
      eyebrow="管理端"
    >
      <template #actions>
        <ElButton type="primary" @click="openCreate">新增检查项目</ElButton>
      </template>
    </PageHeader>

    <GlassCard>
      <div class="check-equipment__toolbar">
        <ElInput
          v-model="keyword"
          clearable
          placeholder="搜索编码或名称"
          class="check-equipment__search"
          @keyup.enter="loadList"
        />
        <ElButton :loading="loading" @click="loadList">查询</ElButton>
      </div>

      <ElTable v-loading="loading" :data="rows" empty-text="暂无检查项目，可点击右上角新增">
        <ElTableColumn prop="techCode" label="编码" width="120" />
        <ElTableColumn prop="techName" label="名称" min-width="140" />
        <ElTableColumn prop="techFormat" label="规格" width="100" />
        <ElTableColumn prop="techPrice" label="单价（元）" width="110" />
        <ElTableColumn prop="priceType" label="费用分类" width="100" />
        <ElTableColumn prop="deptName" label="执行科室" min-width="120" />
        <ElTableColumn label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
            <ElButton link type="danger" @click="confirmDelete(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </GlassCard>

    <ElDialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增检查项目' : '编辑检查项目'"
      width="520px"
      destroy-on-close
    >
      <ElForm ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <ElFormItem label="项目编码" prop="techCode">
          <ElInput v-model="form.techCode" :disabled="editingId != null" placeholder="如 XJCT" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="项目名称" prop="techName">
          <ElInput v-model="form.techName" placeholder="如 胸部CT" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="规格">
          <ElInput v-model="form.techFormat" placeholder="如 平扫" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="单价" prop="techPrice">
          <ElInputNumber v-model="form.techPrice" :min="0" :precision="2" :step="10" class="check-equipment__price" />
        </ElFormItem>
        <ElFormItem label="费用分类">
          <ElInput v-model="form.priceType" placeholder="检查费" maxlength="64" />
        </ElFormItem>
        <ElFormItem label="执行科室">
          <ElSelect v-model="form.deptmentId" clearable placeholder="选择科室" class="check-equipment__select">
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
.check-equipment__toolbar {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-block-end: var(--space-4);
}

.check-equipment__search {
  max-width: 280px;
}

.check-equipment__price,
.check-equipment__select {
  width: 100%;
}
</style>

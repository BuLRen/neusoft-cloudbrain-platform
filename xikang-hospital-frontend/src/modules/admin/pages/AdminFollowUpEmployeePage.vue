<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { adminFollowUpApi, type FollowUpAdminRecord } from '@/shared/api/modules/adminFollowUp'
import { registrationApi } from '@/shared/api/modules/registration'
import type { DepartmentOption } from '@/shared/types/registration'

const { embedded = false } = defineProps<{ embedded?: boolean }>()

const loading = ref(false)
const records = ref<FollowUpAdminRecord[]>([])
const clinicalDepartments = ref<DepartmentOption[]>([])
const total = ref(0)
const totalPages = ref(0)

const filters = reactive({
  departmentId: undefined as number | undefined,
  keyword: '',
  includeDisabled: false,
  page: 1,
  size: 20,
})

const dialogVisible = ref(false)
const accountDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const accountTarget = ref<FollowUpAdminRecord | null>(null)

const form = reactive({
  realname: '',
  deptmentId: undefined as number | undefined,
  createAccount: true,
  username: '',
  password: 'followup123',
})

const accountForm = reactive({
  username: '',
  password: 'followup123',
})

async function loadBaseData() {
  clinicalDepartments.value = await registrationApi.departments('临床科室')
}

async function loadRecords() {
  loading.value = true
  try {
    const page = await adminFollowUpApi.list({
      departmentId: filters.departmentId,
      keyword: filters.keyword || undefined,
      includeDisabled: filters.includeDisabled,
      page: filters.page,
      size: filters.size,
    })
    records.value = page.records
    total.value = page.total
    totalPages.value = page.totalPages
    filters.page = page.page
    filters.size = page.size
  } finally {
    loading.value = false
  }
}

function searchRecords() {
  filters.page = 1
  void loadRecords()
}

function onPageChange(page: number) {
  filters.page = page
  void loadRecords()
}

function onPageSizeChange(size: number) {
  filters.size = size
  filters.page = 1
  void loadRecords()
}

function openCreate() {
  editingId.value = null
  form.realname = ''
  form.deptmentId = clinicalDepartments.value[0]?.id
  form.createAccount = true
  form.username = ''
  form.password = 'followup123'
  dialogVisible.value = true
}

function openEdit(row: FollowUpAdminRecord) {
  editingId.value = row.id
  form.realname = row.realname
  form.deptmentId = row.deptmentId
  form.createAccount = false
  form.username = row.username || ''
  form.password = 'followup123'
  dialogVisible.value = true
}

async function saveFollowUpEmployee() {
  if (!form.realname.trim() || !form.deptmentId) {
    ElMessage.warning('请填写完整随访人员信息')
    return
  }
  const payload = {
    realname: form.realname.trim(),
    deptmentId: form.deptmentId,
    createAccount: editingId.value == null ? form.createAccount : undefined,
    username: form.username.trim() || undefined,
    password: form.password || undefined,
  }
  if (editingId.value == null) {
    await adminFollowUpApi.create(payload)
    ElMessage.success('随访人员已新增')
  } else {
    await adminFollowUpApi.update(editingId.value, payload)
    ElMessage.success('随访人员信息已更新')
  }
  dialogVisible.value = false
  await loadRecords()
}

async function toggleEmployeeStatus(row: FollowUpAdminRecord) {
  const enabled = row.delmark !== 0
  const action = enabled ? '启用' : '停用'
  await ElMessageBox.confirm(`确认${action}随访人员「${row.realname}」？`, '提示', { type: 'warning' })
  await adminFollowUpApi.updateStatus(row.id, enabled)
  ElMessage.success(`随访人员已${action}`)
  await loadRecords()
}

function openAccountDialog(row: FollowUpAdminRecord) {
  accountTarget.value = row
  accountForm.username = row.username || `followup_${row.id}`
  accountForm.password = 'followup123'
  accountDialogVisible.value = true
}

async function submitAccount() {
  if (!accountTarget.value) return
  if (accountTarget.value.userId) {
    await adminFollowUpApi.resetPassword(accountTarget.value.id, accountForm.password)
    ElMessage.success('密码已重置')
  } else {
    await adminFollowUpApi.createAccount(accountTarget.value.id, {
      username: accountForm.username.trim() || undefined,
      password: accountForm.password || undefined,
    })
    ElMessage.success('账号已创建')
  }
  accountDialogVisible.value = false
  await loadRecords()
}

async function toggleAccountStatus(row: FollowUpAdminRecord) {
  if (!row.userId) {
    ElMessage.warning('请先创建登录账号')
    return
  }
  const enabled = row.accountStatus !== 1
  await adminFollowUpApi.updateAccountStatus(row.id, enabled)
  ElMessage.success(enabled ? '账号已启用' : '账号已停用')
  await loadRecords()
}

function employeeStatusLabel(delmark: number) {
  return delmark === 0 ? '在职' : '已停用'
}

function employeeStatusTone(delmark: number) {
  return delmark === 0 ? 'success' : 'warning'
}

function accountStatusLabel(status?: number) {
  if (status == null) return '未建号'
  return status === 1 ? '启用' : '停用'
}

function accountStatusTone(status?: number) {
  if (status == null) return 'neutral'
  return status === 1 ? 'success' : 'danger'
}

onMounted(async () => {
  await loadBaseData()
  await loadRecords()
})
</script>

<template>
  <div class="followup-employee-management" :class="{ 'u-page-grid': !embedded, 'admin-embedded-surface': embedded }">
    <div v-if="embedded" class="admin-section-header">
      <div class="admin-section-header__text">
        <h3>随访人员</h3>
        <p>维护各临床科室随访护士档案，登录后仅可访问随访系统。</p>
      </div>
    </div>
    <PageHeader
      v-if="!embedded"
      title="随访人员维护"
      description="维护临床科室随访护士档案。随访人员登录后仅可访问随访工作台、疗效评估与医患沟通。"
      eyebrow="管理员"
    />

    <GlassCard class="panel">
      <div class="personnel-toolbar">
        <div class="personnel-toolbar__filters">
          <ElSelect v-model="filters.departmentId" clearable placeholder="临床科室" class="field">
            <ElOption
              v-for="dept in clinicalDepartments"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </ElSelect>
          <ElInput v-model="filters.keyword" clearable placeholder="搜索姓名" class="field field--keyword" />
          <label class="switch-label">
            <span>含已停用</span>
            <ElSwitch v-model="filters.includeDisabled" @change="searchRecords" />
          </label>
          <ElButton type="primary" @click="searchRecords">查询</ElButton>
        </div>
        <div class="personnel-toolbar__actions">
          <ElButton type="primary" @click="openCreate">新增随访人员</ElButton>
          <ElButton @click="loadRecords">刷新</ElButton>
        </div>
      </div>

      <ElTable v-loading="loading" :data="records" stripe>
        <ElTableColumn prop="realname" label="姓名" min-width="120" />
        <ElTableColumn prop="deptName" label="临床科室" min-width="120" />
        <ElTableColumn label="档案状态" width="100">
          <template #default="{ row }">
            <StatusTag :tone="employeeStatusTone(row.delmark)">
              {{ employeeStatusLabel(row.delmark) }}
            </StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="username" label="登录账号" min-width="120">
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="账号状态" width="100">
          <template #default="{ row }">
            <StatusTag :tone="accountStatusTone(row.accountStatus)">
              {{ accountStatusLabel(row.accountStatus) }}
            </StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
            <ElButton link type="primary" @click="openAccountDialog(row)">
              {{ row.userId ? '重置密码' : '创建账号' }}
            </ElButton>
            <ElButton
              v-if="row.userId"
              link
              :type="row.accountStatus === 1 ? 'warning' : 'success'"
              @click="toggleAccountStatus(row)"
            >
              {{ row.accountStatus === 1 ? '停用账号' : '启用账号' }}
            </ElButton>
            <ElButton
              link
              :type="row.delmark === 0 ? 'warning' : 'success'"
              @click="toggleEmployeeStatus(row)"
            >
              {{ row.delmark === 0 ? '停用档案' : '启用档案' }}
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="pagination-bar">
        <p class="table-footer">
          共 {{ total }} 名随访人员
          <template v-if="totalPages > 0">，第 {{ filters.page }} / {{ totalPages }} 页</template>
        </p>
        <ElPagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>
    </GlassCard>

    <ElDialog v-model="dialogVisible" :title="editingId == null ? '新增随访人员' : '编辑随访人员'" width="520px">
      <ElForm label-width="100px">
        <ElFormItem label="姓名" required>
          <ElInput v-model="form.realname" placeholder="如：内分泌护士" />
        </ElFormItem>
        <ElFormItem label="临床科室" required>
          <ElSelect v-model="form.deptmentId" placeholder="选择临床科室" style="width: 100%">
            <ElOption v-for="dept in clinicalDepartments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </ElSelect>
        </ElFormItem>
        <template v-if="editingId == null">
          <ElFormItem label="创建账号">
            <ElSwitch v-model="form.createAccount" />
          </ElFormItem>
          <ElFormItem v-if="form.createAccount" label="用户名">
            <ElInput v-model="form.username" placeholder="留空则自动生成 followup_{id}" />
          </ElFormItem>
          <ElFormItem v-if="form.createAccount" label="初始密码">
            <ElInput v-model="form.password" placeholder="默认 followup123" />
          </ElFormItem>
        </template>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveFollowUpEmployee">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="accountDialogVisible"
      :title="accountTarget?.userId ? '重置密码' : '创建登录账号'"
      width="480px"
    >
      <ElForm label-width="100px">
        <ElFormItem v-if="!accountTarget?.userId" label="用户名">
          <ElInput v-model="accountForm.username" placeholder="留空则自动生成" />
        </ElFormItem>
        <ElFormItem :label="accountTarget?.userId ? '新密码' : '初始密码'">
          <ElInput v-model="accountForm.password" placeholder="默认 followup123" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="accountDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitAccount">确认</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.panel {
  padding: var(--space-4);
}

.table-footer {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 13px;
}

.pagination-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-4);
}
</style>

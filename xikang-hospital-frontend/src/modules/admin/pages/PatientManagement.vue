<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
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
import { adminPatientApi, type PatientAdminRecord } from '@/shared/api/modules/adminPatient'
import { adminPaymentApi } from '@/shared/api/modules/adminPayment'
import { useAuthStore } from '@/app/stores/auth'

const authStore = useAuthStore()

const loading = ref(false)
const detailLoading = ref(false)
const records = ref<PatientAdminRecord[]>([])
const total = ref(0)
const totalPages = ref(0)

const filters = reactive({
  keyword: '',
  includeDisabled: false,
  page: 1,
  size: 20,
})

const dialogVisible = ref(false)
const detailDrawerVisible = ref(false)
const rechargeDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const detailRecord = ref<PatientAdminRecord | null>(null)
const rechargeTarget = ref<PatientAdminRecord | null>(null)
const rechargeAmount = ref<number>(100)
const rechargeRemark = ref('窗口充值')
const recharging = ref(false)

const form = reactive({
  realName: '',
  idCard: '',
  gender: '男',
  birthdate: '',
  phone: '',
  homeAddress: '',
  allergyHistory: '',
})

function maskIdCard(idCard: string) {
  if (!idCard || idCard.length < 10) return idCard
  return idCard.replace(/^(.{6}).+(.{4})$/, '$1********$2')
}

function formatBalance(value?: number) {
  if (value == null) return '0.00'
  return Number(value).toFixed(2)
}

function patientStatusLabel(delmark: number) {
  return delmark === 0 ? '正常' : '已归档'
}

function patientStatusTone(delmark: number) {
  return delmark === 0 ? 'success' : 'warning'
}

async function loadRecords() {
  loading.value = true
  try {
    const page = await adminPatientApi.list({
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

function resetForm() {
  form.realName = ''
  form.idCard = ''
  form.gender = '男'
  form.birthdate = ''
  form.phone = ''
  form.homeAddress = ''
  form.allergyHistory = ''
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: PatientAdminRecord) {
  editingId.value = row.id
  form.realName = row.realName
  form.idCard = row.idCard
  form.gender = row.gender
  form.birthdate = row.birthdate?.slice(0, 10) || ''
  form.phone = row.phone || ''
  form.homeAddress = row.homeAddress || ''
  form.allergyHistory = row.allergyHistory || ''
  dialogVisible.value = true
}

async function savePatient() {
  if (!form.realName.trim() || !form.idCard.trim() || !form.gender) {
    ElMessage.warning('请填写姓名、身份证与性别')
    return
  }
  const payload = {
    realName: form.realName.trim(),
    idCard: form.idCard.trim(),
    gender: form.gender,
    birthdate: form.birthdate || undefined,
    phone: form.phone.trim() || undefined,
    homeAddress: form.homeAddress.trim() || undefined,
    allergyHistory: form.allergyHistory.trim() || undefined,
  }
  if (editingId.value == null) {
    await adminPatientApi.create(payload)
    ElMessage.success('患者档案已创建')
  } else {
    await adminPatientApi.update(editingId.value, payload)
    ElMessage.success('患者档案已更新')
  }
  dialogVisible.value = false
  await loadRecords()
  if (detailRecord.value?.id === editingId.value) {
    await openDetail({ id: editingId.value } as PatientAdminRecord)
  }
}

async function togglePatientStatus(row: PatientAdminRecord) {
  const archived = row.delmark !== 0
  const action = archived ? '取消归档' : '归档'
  const hint = archived
    ? `确认恢复患者「${row.realName}」的档案？恢复后可在医疗业务中再次使用。`
    : `确认归档患者「${row.realName}」？归档后将从待诊接诊、医技队列、药房待发药等医疗业务中隐藏，且无法继续诊疗。`
  await ElMessageBox.confirm(hint, `${action}确认`, { type: 'warning' })
  await adminPatientApi.updateStatus(row.id, archived ? 0 : 1)
  ElMessage.success(archived ? '已取消归档' : '患者档案已归档')
  await loadRecords()
  if (detailRecord.value?.id === row.id) {
    detailRecord.value = await adminPatientApi.get(row.id)
  }
}

async function openDetail(row: PatientAdminRecord) {
  detailDrawerVisible.value = true
  detailLoading.value = true
  try {
    detailRecord.value = await adminPatientApi.get(row.id)
  } finally {
    detailLoading.value = false
  }
}

function openRecharge(row: PatientAdminRecord) {
  rechargeTarget.value = row
  rechargeAmount.value = 100
  rechargeRemark.value = '窗口充值'
  rechargeDialogVisible.value = true
}

async function submitRecharge() {
  if (!rechargeTarget.value) return
  if (!rechargeAmount.value || rechargeAmount.value <= 0) {
    ElMessage.warning('请输入有效充值金额')
    return
  }
  recharging.value = true
  try {
    const result = await adminPaymentApi.rechargePatient(
      rechargeTarget.value.id,
      rechargeAmount.value,
      rechargeRemark.value || undefined,
      {
        operatorId: authStore.userId ? Number(authStore.userId) : undefined,
        operatorName: authStore.realName || authStore.username || '管理员',
      },
    )
    ElMessage.success(result.message || '充值成功')
    rechargeDialogVisible.value = false
    await loadRecords()
    if (detailRecord.value?.id === rechargeTarget.value.id) {
      detailRecord.value = await adminPatientApi.get(rechargeTarget.value.id)
    }
  } finally {
    recharging.value = false
  }
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <div class="patient-management u-page-grid">
    <PageHeader
      title="患者档案管理"
      description="维护全院患者主档案；归档后将从待诊接诊等医疗业务中隐藏，管理员可在「含已归档」中查看与恢复。"
      eyebrow="管理员"
    />

    <GlassCard class="panel">
      <div class="personnel-toolbar">
        <div class="personnel-toolbar__filters">
          <ElInput
            v-model="filters.keyword"
            clearable
            placeholder="搜索姓名 / 手机 / 身份证 / 档案号"
            class="field field--keyword"
            @keyup.enter="searchRecords"
          />
          <label class="switch-label">
            <span>含已归档</span>
            <ElSwitch v-model="filters.includeDisabled" @change="searchRecords" />
          </label>
          <ElButton type="primary" @click="searchRecords">查询</ElButton>
        </div>
        <div class="personnel-toolbar__actions">
          <ElButton type="primary" @click="openCreate">新建档案</ElButton>
          <ElButton @click="loadRecords">刷新</ElButton>
        </div>
      </div>

      <ElTable v-loading="loading" :data="records" stripe>
        <ElTableColumn prop="id" label="档案号" width="90" />
        <ElTableColumn prop="realName" label="姓名" min-width="100" />
        <ElTableColumn prop="gender" label="性别" width="70" />
        <ElTableColumn prop="phone" label="手机" min-width="120">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="身份证" min-width="160">
          <template #default="{ row }">{{ maskIdCard(row.idCard) }}</template>
        </ElTableColumn>
        <ElTableColumn label="余额" width="100">
          <template #default="{ row }">¥{{ formatBalance(row.accountBalance) }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :tone="patientStatusTone(row.delmark)">
              {{ patientStatusLabel(row.delmark) }}
            </StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(row)">详情</ElButton>
            <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
            <ElButton v-if="row.delmark === 0" link type="primary" @click="openRecharge(row)">充值</ElButton>
            <ElButton
              link
              :type="row.delmark === 0 ? 'warning' : 'success'"
              @click="togglePatientStatus(row)"
            >
              {{ row.delmark === 0 ? '归档' : '取消归档' }}
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="pagination-bar admin-pagination-bar">
        <p class="table-footer">
          共 {{ total }} 名患者
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

    <ElDialog v-model="dialogVisible" :title="editingId == null ? '新建患者档案' : '编辑患者档案'" width="520px">
      <ElForm label-width="90px">
        <ElFormItem label="姓名" required>
          <ElInput v-model="form.realName" placeholder="患者真实姓名" />
        </ElFormItem>
        <ElFormItem label="身份证" required>
          <ElInput
            v-model="form.idCard"
            placeholder="18 位身份证号"
            :disabled="editingId != null"
          />
        </ElFormItem>
        <ElFormItem label="性别" required>
          <ElSelect v-model="form.gender" style="width: 100%">
            <ElOption label="男" value="男" />
            <ElOption label="女" value="女" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="出生日期">
          <ElInput v-model="form.birthdate" placeholder="YYYY-MM-DD，留空则从身份证解析" />
        </ElFormItem>
        <ElFormItem label="手机">
          <ElInput v-model="form.phone" placeholder="联系电话" />
        </ElFormItem>
        <ElFormItem label="住址">
          <ElInput v-model="form.homeAddress" type="textarea" :rows="2" placeholder="家庭住址" />
        </ElFormItem>
        <ElFormItem label="过敏史">
          <ElInput v-model="form.allergyHistory" type="textarea" :rows="2" placeholder="药物或食物过敏史" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="savePatient">保存</ElButton>
      </template>
    </ElDialog>

    <ElDrawer v-model="detailDrawerVisible" title="患者档案详情" size="480px">
      <div v-loading="detailLoading">
        <template v-if="detailRecord">
          <dl class="detail-list">
            <div><dt>档案号</dt><dd>{{ detailRecord.id }}</dd></div>
            <div><dt>姓名</dt><dd>{{ detailRecord.realName }}</dd></div>
            <div><dt>性别</dt><dd>{{ detailRecord.gender }}</dd></div>
            <div><dt>出生日期</dt><dd>{{ detailRecord.birthdate?.slice(0, 10) || '—' }}</dd></div>
            <div><dt>身份证</dt><dd>{{ detailRecord.idCard }}</dd></div>
            <div><dt>手机</dt><dd>{{ detailRecord.phone || '—' }}</dd></div>
            <div><dt>住址</dt><dd>{{ detailRecord.homeAddress || '—' }}</dd></div>
            <div><dt>过敏史</dt><dd>{{ detailRecord.allergyHistory || '—' }}</dd></div>
            <div>
              <dt>账户余额</dt>
              <dd class="balance-row">
                <span>¥{{ formatBalance(detailRecord.accountBalance) }}</span>
                <ElButton
                  v-if="detailRecord.delmark === 0"
                  size="small"
                  type="primary"
                  @click="openRecharge(detailRecord)"
                >窗口充值</ElButton>
              </dd>
            </div>
            <div>
              <dt>档案状态</dt>
              <dd>
                <StatusTag :tone="patientStatusTone(detailRecord.delmark)">
                  {{ patientStatusLabel(detailRecord.delmark) }}
                </StatusTag>
              </dd>
            </div>
          </dl>

          <h4 class="section-title">关联登录账号</h4>
          <ElTable
            v-if="detailRecord.managedUsers?.length"
            :data="detailRecord.managedUsers"
            size="small"
            stripe
          >
            <ElTableColumn prop="userId" label="用户 ID" width="90" />
            <ElTableColumn prop="username" label="用户名" min-width="120">
              <template #default="{ row }">{{ row.username || '—' }}</template>
            </ElTableColumn>
            <ElTableColumn prop="relation" label="关系" width="80" />
          </ElTable>
          <p v-else class="empty-hint">暂无关联登录账号</p>
        </template>
      </div>
    </ElDrawer>

    <ElDialog v-model="rechargeDialogVisible" title="窗口充值" width="420px">
      <ElForm label-width="90px">
        <ElFormItem label="患者">
          <span>{{ rechargeTarget?.realName }}（#{{ rechargeTarget?.id }}）</span>
        </ElFormItem>
        <ElFormItem label="当前余额">
          <span>¥{{ formatBalance(rechargeTarget?.accountBalance) }}</span>
        </ElFormItem>
        <ElFormItem label="充值金额" required>
          <ElInputNumber v-model="rechargeAmount" :min="0.01" :precision="2" :step="50" style="width: 100%" />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="rechargeRemark" placeholder="充值备注" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="rechargeDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="recharging" @click="submitRecharge">确认充值</ElButton>
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

.detail-list {
  display: grid;
  gap: var(--space-3);
  margin: 0 0 var(--space-5);
}

.detail-list > div {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: var(--space-2);
  align-items: start;
}

.detail-list dt {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 13px;
}

.detail-list dd {
  margin: 0;
  font-size: 14px;
}

.balance-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.section-title {
  margin: 0 0 var(--space-3);
  font-size: 14px;
  font-weight: 600;
}

.empty-hint {
  margin: 0;
  color: var(--color-text-soft);
  font-size: 13px;
}
</style>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { patientApi } from '@/shared/api/modules/patient'
import type { PatientBalanceTransaction, BalanceTransactionType } from '@/shared/api/modules/patient'
import { authApi } from '@/shared/api/modules/auth'
import { useAuthStore } from '@/app/stores/auth'

const authStore = useAuthStore()

// 加载状态
const loading = ref(false)

// 添加就诊人弹窗
const showAddDialog = ref(false)
const addForm = reactive({
  realName: '',
  gender: '',
  idCard: '',
  phone: '',
  relation: '父亲',
})

// 修改密码弹窗
const showPasswordDialog = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const passwordError = ref('')
const isChangingPassword = ref(false)

// 个人信息
interface PatientInfo {
  id: number
  realName: string
  idCard: string
  gender: string
  birthdate: string
  phone?: string
  avatar?: string
  homeAddress?: string
  allergyHistory?: string
  delmark: number
  relation?: string
  isPrimary?: number
  accountBalance?: number
}

const currentPatient = ref<PatientInfo | null>(null)
const familyPatients = ref<PatientInfo[]>([])

// 交易记录（来自后端真实钱包流水）
const transactionsCardRef = ref<{ $el?: HTMLElement } | null>(null)
function scrollToTransactions() {
  const el = transactionsCardRef.value?.$el
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  loadTransactions()
}

interface TransactionEntry {
  id: string
  type: 'recharge' | 'payment' | 'refund'
  title: string
  amount: number
  status: string
  statusName: string
  direction: 'in' | 'out'
  time?: string
  raw: PatientBalanceTransaction
}
const transactions = ref<TransactionEntry[]>([])
const transactionsLoading = ref(false)
const transactionFilter = ref<'all' | 'recharge' | 'payment' | 'refund'>('all')

// 编辑状态
const isEditing = ref(false)
const editForm = reactive({
  realName: '',
  gender: '',
  birthdate: '',
  phone: '',
  homeAddress: '',
  allergyHistory: '',
})

// 脱敏函数
function maskIdCard(idCard: string): string {
  if (!idCard || idCard.length < 10) return idCard
  return idCard.slice(0, 3) + '********' + idCard.slice(-4)
}

function maskPhone(phone: string): string {
  if (!phone || phone.length < 11) return phone
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

function maskEmail(email: string): string {
  if (!email || !email.includes('@')) return email
  const [name, domain] = email.split('@')
  if (!name) return email
  return name.slice(0, 1) + '***@' + domain
}
// 保留 maskEmail 以备后续账号安全模块使用
const maskers = { maskIdCard, maskPhone, maskEmail }
void maskers

// 计算年龄
const age = computed(() => {
  if (!currentPatient.value?.birthdate) return ''
  const birth = new Date(currentPatient.value.birthdate)
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const m = today.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
    age--
  }
  return age
})

// 获取用户信息
async function loadData() {
  loading.value = true
  try {
    const userId = parseInt(authStore.userId || '0')
    if (!userId) {
      loading.value = false
      return
    }

    // 调用 API 获取完整患者信息
    const patients = await patientApi.getPatientList(userId)

    if (patients && patients.length > 0) {
      // 通过 relation 判断本人和家人
      currentPatient.value = patients.find(p => p.relation === '本人') || patients[0]
      familyPatients.value = patients.filter(p => p.relation !== '本人')
    }
  } catch (error) {
    console.error('加载患者信息失败:', error)
  } finally {
    loading.value = false
  }
}

// 开始编辑
function startEdit() {
  if (!currentPatient.value) return
  editForm.realName = currentPatient.value.realName || ''
  editForm.gender = currentPatient.value.gender || ''
  editForm.birthdate = currentPatient.value.birthdate || ''
  editForm.phone = currentPatient.value.phone || ''
  editForm.homeAddress = currentPatient.value.homeAddress || ''
  editForm.allergyHistory = currentPatient.value.allergyHistory || ''
  isEditing.value = true
}

// 取消编辑
function cancelEdit() {
  isEditing.value = false
}

// 保存编辑
async function saveEdit() {
  if (!currentPatient.value) return
  try {
    await patientApi.updatePatient(currentPatient.value.id, {
      realName: editForm.realName,
      gender: editForm.gender,
      birthdate: editForm.birthdate,
      homeAddress: editForm.homeAddress,
      allergyHistory: editForm.allergyHistory,
    })

    // 更新本地数据（手机号和身份证号不更新）
    currentPatient.value.realName = editForm.realName
    currentPatient.value.gender = editForm.gender
    currentPatient.value.birthdate = editForm.birthdate
    currentPatient.value.homeAddress = editForm.homeAddress
    currentPatient.value.allergyHistory = editForm.allergyHistory

    isEditing.value = false
    ElMessage.success('个人信息已更新')
  } catch {
    ElMessage.error('更新失败')
  }
}

// 设置默认就诊人
async function setDefaultMember(patientId: number) {
  try {
    const userId = parseInt(authStore.userId || '0')
    await patientApi.setDefaultPatient(patientId, userId)
    // 更新本地状态
    familyPatients.value.forEach(p => {
      if (p.id === patientId) {
        p.isPrimary = 1
      } else {
        p.isPrimary = 0
      }
    })
    // 更新 currentPatient
    const newPrimary = familyPatients.value.find(p => p.id === patientId)
    if (newPrimary) {
      currentPatient.value = newPrimary
    }
    ElMessage.success('已设为默认就诊人')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function rechargePatient(patient: PatientInfo) {
  try {
    const { value } = await ElMessageBox.prompt(`为 ${patient.realName} 充值`, '账户充值', {
      confirmButtonText: '确认充值',
      cancelButtonText: '取消',
      inputPattern: /^([1-9]\d{0,5})(\.\d{1,2})?$/,
      inputErrorMessage: '请输入大于0的金额，最多两位小数',
    })
    const amount = Number(value)
    const result = await patientApi.rechargeBalance(patient.id, amount)
    const newBalance = Number(result.accountBalance || 0)
    patient.accountBalance = newBalance
    authStore.setPatientBalance(patient.id, newBalance)
    ElMessage.success(result.message || '充值成功')
    await loadTransactions()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('充值失败')
    }
  }
}

async function loadTransactions() {
  if (!currentPatient.value) return
  transactionsLoading.value = true
  try {
    const records = await patientApi.getBalanceTransactions(currentPatient.value.id)
    transactions.value = (records || []).map(toTransactionFromLedger)
  } catch (err) {
    console.error('加载交易记录失败:', err)
    ElMessage.error('加载交易记录失败')
  } finally {
    transactionsLoading.value = false
  }
}

function toTransactionFromLedger(tx: PatientBalanceTransaction): TransactionEntry {
  const type = transactionTypeToUi(tx.transactionType)
  return {
    id: `ledger-${tx.id}`,
    type,
    title: transactionTitle(tx),
    amount: Number(tx.amount || 0),
    status: tx.transactionType,
    statusName: transactionStatusName(tx.transactionType),
    direction: type === 'refund' || type === 'recharge' ? 'in' : 'out',
    time: tx.transactionTime,
    raw: tx,
  }
}

function transactionTypeToUi(type: BalanceTransactionType): TransactionEntry['type'] {
  if (type === 'RECHARGE') return 'recharge'
  if (type === 'REFUND') return 'refund'
  return 'payment'
}

function transactionTitle(tx: PatientBalanceTransaction): string {
  if (tx.transactionType === 'RECHARGE') return '账户充值'
  if (tx.transactionType === 'REFUND') {
    return tx.businessType ? `${describeBusiness(tx.businessType)}退款` : '余额退款'
  }
  return tx.businessType ? `${describeBusiness(tx.businessType)}扣款` : '余额消费'
}

function describeBusiness(businessType: string): string {
  if (businessType === 'REGISTRATION') return '挂号费'
  if (businessType === 'RECHARGE') return '账户充值'
  return businessType
}

function transactionStatusName(type: BalanceTransactionType): string {
  if (type === 'RECHARGE') return '充值成功'
  if (type === 'REFUND') return '已退款'
  return '已扣款'
}

function transactionTone(entry: TransactionEntry) {
  if (entry.type === 'refund' || entry.direction === 'in') return 'success'
  if (entry.status === '3') return 'warning'
  if (entry.status === '0') return 'warning'
  return 'primary'
}

function transactionSign(entry: TransactionEntry) {
  return entry.direction === 'in' ? '+' : '-'
}

const filteredTransactions = computed(() => {
  if (transactionFilter.value === 'all') return transactions.value
  return transactions.value.filter(item => item.type === transactionFilter.value)
})

const transactionSummary = computed(() => {
  let income = 0
  let expense = 0
  for (const item of transactions.value) {
    if (item.direction === 'in') income += Number(item.amount || 0)
    else expense += Number(item.amount || 0)
  }
  return {
    income: income.toFixed(2),
    expense: expense.toFixed(2),
    count: transactions.value.length,
  }
})

function formatTransactionTime(value?: string) {
  if (!value) return '-'
  const text = String(value).trim()
  const isoMatch = text.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$/)
  if (isoMatch) {
    return `${isoMatch[1]}-${isoMatch[2]}-${isoMatch[3]} ${isoMatch[4]}:${isoMatch[5]}`
  }
  const fallback = new Date(text)
  if (!Number.isNaN(fallback.getTime())) {
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${fallback.getFullYear()}-${pad(fallback.getMonth() + 1)}-${pad(fallback.getDate())} ${pad(fallback.getHours())}:${pad(fallback.getMinutes())}`
  }
  return text
}

// 删除就诊人
function removeMember(patientId: number, patientName: string) {
  ElMessageBox.confirm(`确定删除就诊人 "${patientName}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await patientApi.deletePatient(patientId)
      familyPatients.value = familyPatients.value.filter(p => p.id !== patientId)
      ElMessage.success('已删除就诊人')
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// 打开修改密码弹窗
function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordError.value = ''
  showPasswordDialog.value = true
}

// 确认修改密码
async function handlePasswordChange() {
  passwordError.value = ''

  // 验证旧密码
  if (!passwordForm.oldPassword) {
    passwordError.value = '请输入旧密码'
    return
  }

  // 验证新密码
  if (!passwordForm.newPassword) {
    passwordError.value = '请输入新密码'
    return
  }
  if (passwordForm.newPassword.length < 6) {
    passwordError.value = '新密码长度不能少于6位'
    return
  }
  if (passwordForm.newPassword === passwordForm.oldPassword) {
    passwordError.value = '新密码不能与旧密码相同'
    return
  }

  // 验证确认密码
  if (!passwordForm.confirmPassword) {
    passwordError.value = '请输入确认密码'
    return
  }
  if (passwordForm.confirmPassword !== passwordForm.newPassword) {
    passwordError.value = '两次输入的密码不一致'
    return
  }

  isChangingPassword.value = true
  try {
    await authApi.changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码修改成功')
    showPasswordDialog.value = false
  } catch (error: unknown) {
    const err = error as { message?: string }
    ElMessage.error(err.message || '密码修改失败')
  } finally {
    isChangingPassword.value = false
  }
}

// 格式化日期显示
function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 打添加诊人弹窗
function openAddDialog() {
  addForm.realName = ''
  addForm.gender = ''
  addForm.idCard = ''
  addForm.phone = ''
  addForm.relation = '家人'
  showAddDialog.value = true
}

// 确认添加就诊人
async function confirmAddMember() {
  if (!addForm.realName.trim()) {
    ElMessage.warning('请输入姓名')
    return
  }
  if (!addForm.idCard.trim()) {
    ElMessage.warning('请输入身份证号')
    return
  }
  if (!addForm.relation || addForm.relation.trim() === '') {
    ElMessage.warning('请选择或输入关系')
    return
  }

  try {
    const userId = parseInt(authStore.userId || '0')
    // 添加家人（会自动检查身份证号是否已存在，已存在则直接关联）
    await patientApi.addFamilyMember(userId, {
      realName: addForm.realName,
      gender: addForm.gender,
      idCard: addForm.idCard,
      phone: addForm.phone,
    }, addForm.relation)

    // 刷新列表
    await loadData()
    showAddDialog.value = false
    ElMessage.success('添加就诊人成功')
  } catch {
    ElMessage.error('添加失败')
  }
}

onMounted(() => {
  loadData()
  loadTransactions()
})
</script>

<template>
  <div class="patient-profile" v-loading="loading">
    <!-- 个人信息卡片 -->
    <GlassCard class="profile-card">
      <div class="card-header">
        <div class="card-title">
          <span class="card-icon">👤</span>
          <span>个人信息</span>
        </div>
        <button v-if="!isEditing" class="btn-edit" @click="startEdit">
          <span class="btn-icon">✏️</span>
          编辑
        </button>
        <div v-else class="edit-actions">
          <button class="btn-cancel" @click="cancelEdit">取消</button>
          <button class="btn-save" @click="saveEdit">保存</button>
        </div>
      </div>

      <div class="profile-content">
        <!-- 头像区域 -->
        <div class="profile-avatar">
          <div class="avatar-circle">
            {{ currentPatient?.realName?.charAt(0) || '?' }}
          </div>
          <span class="avatar-name">{{ currentPatient?.realName || '-' }}</span>
          <span class="avatar-gender">{{ currentPatient?.gender || '-' }}</span>
        </div>

        <!-- 信息展示 -->
        <div class="profile-fields">
          <template v-if="!isEditing">
            <div class="field-row">
              <div class="field-item">
                <label>姓名</label>
                <span class="field-value">{{ currentPatient?.realName || '-' }}</span>
              </div>
              <div class="field-item">
                <label>性别</label>
                <span class="field-value">{{ currentPatient?.gender || '-' }}</span>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>出生日期</label>
                <span class="field-value">{{ formatDate(currentPatient?.birthdate || '') }}</span>
              </div>
              <div class="field-item">
                <label>年龄</label>
                <span class="field-value">{{ age }} 岁</span>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>身份证号</label>
                <span class="field-value sensitive">{{ maskIdCard(currentPatient?.idCard || '') }}</span>
              </div>
              <div class="field-item">
                <label>手机号</label>
                <span class="field-value sensitive">{{ maskPhone(currentPatient?.phone || '') }}</span>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>账户余额</label>
                <span class="field-value balance-value">¥{{ Number(currentPatient?.accountBalance || 0).toFixed(2) }}</span>
              </div>
              <div class="field-item">
                <label>账户操作</label>
                <div class="field-actions">
                  <button v-if="currentPatient" class="btn-outline btn-sm" @click="rechargePatient(currentPatient)">充值</button>
                  <button v-if="currentPatient" class="btn-link btn-sm" @click="scrollToTransactions">查看交易记录</button>
                </div>
              </div>
            </div>
          </template>

          <!-- 编辑模式 -->
          <template v-else>
            <div class="field-row">
              <div class="field-item">
                <label>姓名</label>
                <input v-model="editForm.realName" class="field-input" placeholder="请输入姓名" />
              </div>
              <div class="field-item">
                <label>性别</label>
                <select v-model="editForm.gender" class="field-input">
                  <option value="">请选择</option>
                  <option value="男">男</option>
                  <option value="女">女</option>
                </select>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>出生日期</label>
                <input v-model="editForm.birthdate" type="date" class="field-input" />
              </div>
              <div class="field-item">
                <label>手机号</label>
                <input v-model="editForm.phone" class="field-input" placeholder="手机号不可修改" disabled />
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>身份证号</label>
                <input :value="maskIdCard(currentPatient?.idCard || '')" class="field-input" disabled />
              </div>
            </div>
          </template>
        </div>
      </div>
    </GlassCard>

    <!-- 健康信息卡片 -->
    <GlassCard class="health-card">
      <div class="card-header">
        <div class="card-title">
          <span class="card-icon">🏠</span>
          <span>健康信息</span>
        </div>
      </div>

      <div class="health-content">
        <template v-if="!isEditing">
          <div class="health-item">
            <span class="health-label">家庭地址</span>
            <span class="health-value">{{ currentPatient?.homeAddress || '未填写' }}</span>
          </div>
          <div class="health-item">
            <span class="health-label">过敏史</span>
            <span class="health-value" :class="{ 'empty-value': !currentPatient?.allergyHistory }">
              {{ currentPatient?.allergyHistory || '无' }}
            </span>
          </div>
        </template>
        <template v-else>
          <div class="health-item full">
            <span class="health-label">家庭地址</span>
            <input v-model="editForm.homeAddress" class="field-input" placeholder="请输入家庭地址" />
          </div>
          <div class="health-item full">
            <span class="health-label">过敏史</span>
            <input v-model="editForm.allergyHistory" class="field-input" placeholder="请输入过敏史" />
          </div>
        </template>
      </div>
    </GlassCard>

    <!-- 就诊人管理卡片 -->
    <GlassCard class="family-card">
      <div class="card-header">
        <div class="card-title">
          <span class="card-icon">👨‍👩‍👧</span>
          <span>就诊人管理</span>
        </div>
        <button class="btn-add" @click="openAddDialog">
          <span>+</span> 添加就诊人
        </button>
      </div>

      <div class="family-list">
        <!-- 本人 -->
        <div v-if="currentPatient" class="family-item primary">
          <div class="member-info">
            <div class="member-main">
              <span class="member-name">{{ currentPatient.realName }}</span>
              <StatusTag tone="primary" size="small">本人</StatusTag>
            </div>
            <div class="member-detail">
              <span>身份证: {{ maskIdCard(currentPatient.idCard) }}</span>
              <span>手机: {{ maskPhone(currentPatient.phone || '') }}</span>
              <span>余额: ¥{{ Number(currentPatient.accountBalance || 0).toFixed(2) }}</span>
            </div>
          </div>
        </div>

        <!-- 其他就诊人 -->
        <div
          v-for="member in familyPatients"
          :key="member.id"
          class="family-item"
        >
          <div class="member-info">
            <div class="member-main">
              <span class="member-name">{{ member.realName }}</span>
              <span class="member-relation">{{ member.relation || '家人' }}</span>
            </div>
            <div class="member-detail">
              <span>身份证: {{ maskIdCard(member.idCard) }}</span>
              <span>手机: {{ maskPhone(member.phone || '') }}</span>
              <span>余额: ¥{{ Number(member.accountBalance || 0).toFixed(2) }}</span>
            </div>
          </div>
          <div class="member-actions">
            <button class="btn-link" @click="rechargePatient(member)">充值</button>
            <button class="btn-link" @click="setDefaultMember(member.id)">设为默认</button>
            <button class="btn-link danger" @click="removeMember(member.id, member.realName)">删除</button>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="!currentPatient && familyPatients.length === 0" class="empty-state">
          <span>暂无就诊人信息</span>
        </div>
      </div>
    </GlassCard>

    <!-- 交易记录卡片 -->
    <GlassCard class="transactions-card" ref="transactionsCardRef">
      <div class="card-header">
        <div class="card-title">
          <span class="card-icon">💳</span>
          <span>交易记录</span>
        </div>
        <div class="header-actions">
          <button class="btn-outline btn-sm" @click="loadTransactions" :disabled="transactionsLoading">
            {{ transactionsLoading ? '刷新中...' : '刷新' }}
          </button>
        </div>
      </div>

      <div class="transaction-summary">
        <div class="summary-item">
          <span class="summary-label">收入</span>
          <span class="summary-value income">+¥{{ transactionSummary.income }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">支出</span>
          <span class="summary-value expense">-¥{{ transactionSummary.expense }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">交易笔数</span>
          <span class="summary-value neutral">{{ transactionSummary.count }}</span>
        </div>
      </div>

      <div class="transaction-filter">
        <button
          v-for="opt in [
            { key: 'all', label: '全部' },
            { key: 'recharge', label: '充值' },
            { key: 'payment', label: '消费' },
            { key: 'refund', label: '退款' },
          ]"
          :key="opt.key"
          class="filter-chip"
          :class="{ 'is-active': transactionFilter === opt.key }"
          @click="transactionFilter = (opt.key as any)"
        >
          {{ opt.label }}
        </button>
      </div>

      <div v-if="transactionsLoading" class="empty-state small">交易记录加载中...</div>
      <div v-else-if="filteredTransactions.length === 0" class="empty-state small">
        暂无{{ transactionFilter === 'all' ? '' : transactionFilter === 'recharge' ? '充值' : transactionFilter === 'payment' ? '消费' : '退款' }}记录
      </div>
      <div v-else class="transaction-list">
        <div v-for="item in filteredTransactions" :key="item.id" class="transaction-item">
          <div class="transaction-icon" :data-type="item.type">
            <span v-if="item.type === 'recharge'">＋</span>
            <span v-else-if="item.type === 'refund'">↺</span>
            <span v-else>－</span>
          </div>
          <div class="transaction-main">
            <div class="transaction-title-row">
              <span class="transaction-title">{{ item.title }}</span>
              <StatusTag :tone="transactionTone(item)">{{ item.statusName }}</StatusTag>
            </div>
            <div class="transaction-meta">
              <span>{{ formatTransactionTime(item.time) }}</span>
              <span>流水号 {{ item.raw.transactionNo }}</span>
              <span v-if="item.raw.businessId">业务 #{{ item.raw.businessType }}:{{ item.raw.businessId }}</span>
            </div>
          </div>
          <div class="transaction-amount" :class="{ income: item.direction === 'in', expense: item.direction === 'out' }">
            {{ transactionSign(item) }}¥{{ item.amount.toFixed(2) }}
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- 账号安全卡片 -->
    <GlassCard class="security-card">
      <div class="card-header">
        <div class="card-title">
          <span class="card-icon">🔒</span>
          <span>账号安全</span>
        </div>
      </div>

      <div class="security-list">
        <div class="security-item">
          <div class="security-info">
            <span class="security-label">用户名</span>
            <span class="security-value">{{ authStore.username }}</span>
          </div>
        </div>
        <div class="security-item">
          <div class="security-info">
            <span class="security-label">手机号</span>
            <span class="security-value sensitive">{{ maskPhone(currentPatient?.phone || '') }}</span>
          </div>
          <button class="btn-outline">更换</button>
        </div>
        <div class="security-item">
          <div class="security-info">
            <span class="security-label">登录密码</span>
            <span class="security-value">已设置</span>
          </div>
          <button class="btn-outline" @click="openPasswordDialog">修改</button>
        </div>
      </div>
    </GlassCard>

    <!-- 添加就诊人弹窗 -->
    <el-dialog v-model="showAddDialog" title="添加就诊人" width="450px">
      <el-form label-width="80px">
        <el-form-item label="姓名" required>
          <el-input v-model="addForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="addForm.gender" placeholder="请选择">
            <el-option value="男" label="男" />
            <el-option value="女" label="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份证号" required>
          <el-input v-model="addForm.idCard" placeholder="请输入18位身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="addForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="关系">
          <el-select v-model="addForm.relation" placeholder="请选择或输入" filterable allow-create default-first-option>
            <el-option value="父亲" label="父亲" />
            <el-option value="母亲" label="母亲" />
            <el-option value="配偶" label="配偶" />
            <el-option value="子女" label="子女" />
            <el-option value="祖父" label="祖父" />
            <el-option value="祖母" label="祖母" />
            <el-option value="兄弟" label="兄弟" />
            <el-option value="姐妹" label="姐妹" />
            <el-option value="其他" label="其他" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddMember">确认</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="showPasswordDialog" title="修改密码" width="450px">
      <el-form label-width="80px">
        <el-form-item label="旧密码" required>
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入旧密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码（至少6位）"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
        <div v-if="passwordError" class="password-error">{{ passwordError }}</div>
      </el-form>
      <template #footer>
        <el-button @click="showPasswordDialog = false" :disabled="isChangingPassword">取消</el-button>
        <el-button type="primary" @click="handlePasswordChange" :loading="isChangingPassword">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.patient-profile {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 80%;
  margin: 0 10%;
  padding: 24px 0;
}

/* 卡片通用样式 */
.profile-card,
.health-card,
.family-card,
.security-card,
.transactions-card {
  padding: 24px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 255, 255, 0.7) 100%);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  backdrop-filter: blur(10px);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.patient-profile > :hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

/* 卡片头部 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.card-icon {
  font-size: 20px;
}

/* 按钮样式 */
.btn-edit,
.btn-add {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-edit {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-edit:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-icon {
  font-size: 14px;
}

.btn-add {
  background: #f0f2f5;
  color: #667eea;
  font-weight: 500;
}

.btn-add:hover {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.edit-actions {
  display: flex;
  gap: 10px;
}

.btn-cancel,
.btn-save {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel {
  background: #f0f2f5;
  color: #666;
}

.btn-cancel:hover {
  background: #e0e2e5;
}

.btn-save {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-save:hover {
  transform: scale(1.05);
}

/* 个人信息内容 */
.profile-content {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 32px;
}

.profile-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  color: white;
}

.avatar-circle {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 4px;
}

.avatar-name {
  font-size: 16px;
  font-weight: 600;
}

.avatar-gender {
  font-size: 12px;
  opacity: 0.8;
}

.profile-fields {
  display: grid;
  gap: 16px;
}

.field-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-item label {
  font-size: 12px;
  color: #888;
  font-weight: 500;
}

.field-value {
  font-size: 15px;
  color: #1a1a2e;
}

.field-value.sensitive {
  font-family: 'SF Mono', monospace;
  letter-spacing: 1px;
}

.balance-value {
  font-weight: 700;
  color: #667eea;
}

.empty-value {
  color: #bbb;
  font-style: italic;
}

/* 输入框样式 */
.field-input {
  padding: 10px 14px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  background: #fafbfc;
  transition: all 0.2s ease;
}

.field-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
  background: white;
}

/* 健康信息 */
.health-content {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.health-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.health-item.full {
  grid-column: 1 / -1;
}

.health-label {
  font-size: 12px;
  color: #888;
  font-weight: 500;
}

.health-value {
  font-size: 15px;
  color: #1a1a2e;
}

/* 就诊人列表 */
.family-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.family-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #f8f9fc;
  border-radius: 12px;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}

.family-item.primary {
  background: linear-gradient(135deg, #667eea20 0%, #764ba220 100%);
  border-color: rgba(102, 126, 234, 0.2);
}

.family-item:hover {
  border-color: #667eea40;
  transform: translateX(4px);
}

.member-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.member-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.member-relation {
  font-size: 12px;
  color: #888;
  padding: 2px 8px;
  background: #e8eaf0;
  border-radius: 4px;
}

.member-detail {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #666;
}

.member-actions {
  display: flex;
  gap: 8px;
}

.btn-link {
  padding: 6px 12px;
  background: transparent;
  border: none;
  color: #667eea;
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.btn-link:hover {
  background: rgba(102, 126, 234, 0.1);
}

.btn-link.danger {
  color: #f56c6c;
}

.btn-link.danger:hover {
  background: rgba(245, 108, 108, 0.1);
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 32px;
  color: #999;
  font-size: 14px;
}

/* 账号安全 */
.security-list {
  display: grid;
  gap: 16px;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #f8f9fc;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.security-item:hover {
  background: #f0f2f5;
}

.security-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.security-label {
  font-size: 12px;
  color: #888;
  font-weight: 500;
}

.security-value {
  font-size: 14px;
  color: #1a1a2e;
}

.security-value.sensitive {
  font-family: 'SF Mono', monospace;
  letter-spacing: 1px;
}

.btn-outline {
  padding: 8px 14px;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-outline:hover {
  border-color: #667eea;
  color: #667eea;
}

.password-error {
  color: #f56c6c;
  font-size: 13px;
  margin-top: -10px;
  margin-bottom: 10px;
  padding-left: 80px;
}

/* 响应式 */
@media (max-width: 768px) {
  .patient-profile {
    padding: 16px;
  }

  .profile-content {
    grid-template-columns: 1fr;
  }

  .profile-avatar {
    flex-direction: row;
    justify-content: flex-start;
    gap: 16px;
    padding: 12px 16px;
  }

  .avatar-circle {
    width: 56px;
    height: 56px;
    font-size: 22px;
  }

  .field-row,
  .health-content {
    grid-template-columns: 1fr;
  }

  .member-detail {
    flex-direction: column;
    gap: 4px;
  }
}

/* 交易记录 */
.transactions-card .card-header {
  margin-bottom: 12px;
}

.transactions-card .header-actions {
  display: flex;
  gap: 8px;
}

.field-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.transaction-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.transaction-summary .summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  background: linear-gradient(135deg, rgba(31, 140, 255, 0.08) 0%, rgba(31, 140, 255, 0.02) 100%);
  border: 1px solid rgba(31, 140, 255, 0.18);
  border-radius: 12px;
}

.transaction-summary .summary-label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.transaction-summary .summary-value {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.transaction-summary .summary-value.income {
  color: #1f8cff;
}

.transaction-summary .summary-value.expense {
  color: #6b7280;
}

.transaction-summary .summary-value.neutral {
  color: var(--color-text);
}

.transaction-filter {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.filter-chip {
  padding: 6px 14px;
  font-size: 12px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.filter-chip:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.filter-chip.is-active {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

.transaction-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 480px;
  overflow-y: auto;
}

.transaction-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: #fff;
  transition: background 0.2s ease;
}

.transaction-item:hover {
  background: #f7f8fc;
}

.transaction-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  color: #fff;
  background: var(--color-primary);
}

.transaction-icon[data-type="recharge"] {
  background: #1f8cff;
}

.transaction-icon[data-type="payment"] {
  background: #6b7280;
}

.transaction-icon[data-type="refund"] {
  background: #20b486;
}

.transaction-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.transaction-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.transaction-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.transaction-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
  flex-wrap: wrap;
}

.transaction-amount {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
}

.transaction-amount.income {
  color: #1f8cff;
}

.transaction-amount.expense {
  color: #6b7280;
}

.empty-state.small {
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

@media (max-width: 768px) {
  .transaction-summary {
    grid-template-columns: 1fr;
  }
}
</style>
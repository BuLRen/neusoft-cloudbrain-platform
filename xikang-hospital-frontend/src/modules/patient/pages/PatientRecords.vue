<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElDatePicker, ElRadio, ElRadioButton, ElRadioGroup } from 'element-plus'
import StatusTag from '@/shared/components/StatusTag.vue'
import ClinicalRecordDrawer from '@/modules/physician/components/ClinicalRecordDrawer.vue'
import { clinicalRecordApi, type ClinicalVisitSummary } from '@/shared/api/modules/clinicalRecord'
import { useAuthStore } from '@/app/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const records = ref<ClinicalVisitSummary[]>([])
const loading = ref(false)
const selectedRegisterId = ref<number | null>(null)
const drawerVisible = ref(false)

const patientId = computed(() => authStore.currentPatientId || authStore.currentPatient?.patientId)

// ========== 列表筛选 / 排序 ==========
type RecordTab = 'all' | 'ongoing' | 'done'
const activeTab = ref<RecordTab>('all')
const visitDateRange = ref<[string, string] | null>(null)
const sortBy = ref<'visitDate' | 'departmentName' | 'physicianName'>('visitDate')
const sortDir = ref<'desc' | 'asc'>('desc')

// tab 过滤：进行中(1,2,5,6) / 已完成(3)；"全部"排除爽约(7)
function tabFilter(visitState?: number): boolean {
  switch (activeTab.value) {
    case 'ongoing': return [1, 2, 5, 6].includes(visitState ?? 0)
    case 'done':    return visitState === 3
    default:        return visitState !== 7
  }
}

// 就诊状态 → 中文
function visitStateLabel(visitState?: number): string {
  const map: Record<number, string> = {
    1: '已挂号', 2: '医生接诊', 3: '看诊结束',
    5: '检查检验中', 6: '检查检验完成', 7: '爽约',
  }
  return map[visitState ?? 0] || '未知'
}

function visitStateTone(visitState?: number): 'primary' | 'success' | 'warning' | 'danger' {
  if (visitState === 3) return 'success'
  if (visitState === 7) return 'danger'
  if ([1, 2, 5, 6].includes(visitState ?? 0)) return 'primary'
  return 'warning'
}

function visitDateOf(record: ClinicalVisitSummary): string {
  return record.visitDate ? String(record.visitDate).slice(0, 10) : ''
}

function fieldComparable(record: ClinicalVisitSummary): string {
  if (sortBy.value === 'departmentName') return record.departmentName || ''
  if (sortBy.value === 'physicianName')  return record.physicianName || ''
  return record.visitDate || ''
}

const filteredRecords = computed(() => {
  // 第一层：tab 状态过滤
  const byTab = records.value.filter((r) => tabFilter(r.visitState))
  // 第二层：日期范围过滤
  const range = visitDateRange.value
  const byDate = (!range || !range[0] || !range[1])
    ? byTab
    : byTab.filter((r) => {
        const day = visitDateOf(r)
        return !!day && day >= range[0] && day <= range[1]
      })
  // 第三层：排序
  const dir = sortDir.value === 'asc' ? 1 : -1
  return byDate.slice().sort((a, b) => {
    const va = fieldComparable(a)
    const vb = fieldComparable(b)
    if (va === vb) return (a.registerId - b.registerId)
    return va < vb ? -dir : dir
  })
})

function clearFilters() {
  activeTab.value = 'all'
  visitDateRange.value = null
}

// 分母：排除爽约(7)后的总数，与 tabFilter 的"全部"语义一致
const visibleRecordsCount = computed(() =>
  records.value.filter((r) => r.visitState !== 7).length,
)

const selectedRecord = computed(() =>
  records.value.find((record) => record.registerId === selectedRegisterId.value) ?? null,
)

const affixSubtitle = computed(() => {
  const record = selectedRecord.value
  if (!record) return ''
  return [record.departmentName, record.physicianName].filter(Boolean).join(' · ')
})

async function loadRecords() {
  if (!patientId.value) return
  loading.value = true
  try {
    records.value = await clinicalRecordApi.patientVisits(patientId.value)
    if (selectedRegisterId.value && !records.value.some((item) => item.registerId === selectedRegisterId.value)) {
      selectedRegisterId.value = null
    }
  } catch (error) {
    console.warn('加载电子病历失败:', error)
  } finally {
    loading.value = false
  }
}

function formatDate(record: ClinicalVisitSummary) {
  const raw = record.visitDate
  if (!raw) return '-'
  return String(raw).slice(0, 10)
}

function formatVisitTime(record: ClinicalVisitSummary): string {
  if (!record.visitDate) return '-'
  return String(record.visitDate).replace('T', ' ').slice(0, 16)
}

function selectRecord(record: ClinicalVisitSummary) {
  selectedRegisterId.value = record.registerId
}

function openRecordDrawer(record: ClinicalVisitSummary) {
  selectRecord(record)
  drawerVisible.value = true
}

onMounted(loadRecords)
</script>

<template>
  <div class="record-list-page">
    <!-- toolbar：标题 + 跳转长期档案 -->
    <div class="list-toolbar">
      <div>
        <h2>电子病历</h2>
        <p>这里汇总您所有就诊中的和已完成的病历记录，医生归档后可查看完整内容。</p>
      </div>
      <button class="btn-outline" @click="router.push('/patient/clinical-profile')">长期健康档案</button>
    </div>

    <!-- 筛选区：tab + 日期 + 排序 + 计数 -->
    <div class="list-filters">
      <div class="filter-field filter-field--tabs">
        <label>就诊状态</label>
        <ElRadioGroup v-model="activeTab">
          <ElRadioButton value="all">全部</ElRadioButton>
          <ElRadioButton value="ongoing">进行中</ElRadioButton>
          <ElRadioButton value="done">已完成</ElRadioButton>
        </ElRadioGroup>
      </div>
      <div class="filter-field">
        <label>就诊日期</label>
        <ElDatePicker
          v-model="visitDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          clearable
          class="field"
        />
      </div>
      <div class="filter-field">
        <label>排序字段</label>
        <select v-model="sortBy" class="form-input">
          <option value="visitDate">就诊时间</option>
          <option value="departmentName">科室</option>
          <option value="physicianName">医生</option>
        </select>
      </div>
      <div class="filter-field">
        <label>排序方向</label>
        <ElRadioGroup v-model="sortDir">
          <ElRadio value="desc">降序</ElRadio>
          <ElRadio value="asc">升序</ElRadio>
        </ElRadioGroup>
      </div>
      <div class="filter-field filter-summary">
        <span>已显示 {{ filteredRecords.length }} / {{ visibleRecordsCount }} 条</span>
      </div>
    </div>

    <!-- 加载 / 全空 / 筛选空 / 列表 四态 -->
    <div v-if="loading" class="empty-state">正在加载电子病历...</div>
    <div v-else-if="records.length === 0" class="empty-state rich-empty">
      <strong>暂无电子病历</strong>
      <span>完成挂号并就诊后，医生归档的病历会显示在这里。包括主诉、诊断、处方和检查结果等完整内容。</span>
      <button class="btn-primary" @click="router.push('/patient/registration')">去预约挂号</button>
    </div>
    <div v-else-if="filteredRecords.length === 0" class="empty-state">
      当前筛选条件下没有病历记录，<button class="link-btn" @click="clearFilters">清空筛选</button>
    </div>
    <div v-else class="record-card-list">
      <div
        v-for="record in filteredRecords"
        :key="record.registerId"
        class="record-card"
        :class="{ 'record-card--active': selectedRegisterId === record.registerId }"
        @click="selectRecord(record)"
      >
        <div class="record-date-block">
          <span class="date-day">{{ formatDate(record).split('-')[2] || '-' }}</span>
          <span class="date-month">{{ formatDate(record).slice(0, 7) }}</span>
        </div>
        <div class="record-main">
          <div class="record-title-row">
            <strong>{{ record.departmentName || '未分配科室' }}</strong>
            <StatusTag :tone="visitStateTone(record.visitState)">
              {{ visitStateLabel(record.visitState) }}
            </StatusTag>
            <StatusTag :tone="record.archived ? 'success' : 'warning'">
              {{ record.archived ? '已归档' : '待医生归档' }}
            </StatusTag>
          </div>
          <div class="record-meta">
            <span>医生：{{ record.physicianName || '待分配' }}</span>
            <span>就诊时间：{{ formatVisitTime(record) }}</span>
            <span>病历号：{{ record.caseNumber || record.registerId }}</span>
            <span v-if="record.archived && record.diagnosis">诊断：{{ record.diagnosis }}</span>
            <span v-else-if="!record.archived" class="record-hint">完整病历将在医生归档后开放查看</span>
          </div>
        </div>
        <div class="record-actions">
          <button class="btn-outline btn-sm" @click.stop="openRecordDrawer(record)">查看病历</button>
        </div>
      </div>
    </div>

    <ClinicalRecordDrawer
      v-model:visible="drawerVisible"
      :register-id="selectedRegisterId"
      mode="patient"
      :subtitle="affixSubtitle"
    />
  </div>
</template>

<style scoped>
/* ===================== 列表页容器（与挂号页一致） ===================== */
.record-list-page {
  width: 88%;
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--space-8);
  background: var(--color-surface);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
}

/* —— toolbar —— */
.list-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-5);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--color-border);
}

.list-toolbar h2 {
  margin: 0 0 var(--space-2);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.list-toolbar p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
  font-size: 13.5px;
  max-width: 640px;
}

/* —— 筛选条 —— */
.list-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  align-items: flex-end;
  margin-bottom: var(--space-5);
  padding: var(--space-4) var(--space-5);
  background: var(--color-control);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 168px;
}

.filter-field label {
  font-size: 11.5px;
  color: var(--color-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.filter-field .form-input,
.filter-field :deep(.el-date-editor) {
  height: 36px;
  padding: 0 var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-strong);
  font-size: 13px;
  font-family: inherit;
  transition: border-color var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard);
}

.filter-field .form-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.filter-field .field {
  width: 100%;
}

.filter-field--tabs {
  min-width: auto;
}

.filter-summary {
  min-width: auto;
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 12px;
  padding-bottom: 8px;
}

.link-btn {
  background: none;
  border: none;
  color: var(--color-primary);
  cursor: pointer;
  font-size: inherit;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* —— 病历卡片 —— */
.record-card-list {
  display: grid;
  gap: var(--space-4);
}

.record-card {
  position: relative;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--color-surface-strong);
  box-shadow: var(--shadow-sm);
  transition: transform var(--duration-base) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard),
              border-color var(--duration-base) var(--ease-standard);
  overflow: hidden;
  cursor: pointer;
}

.record-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--gradient-primary);
  opacity: 0.85;
}

.record-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-border-strong);
}

.record-card--active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary-soft), var(--shadow-md);
}

.record-date-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 72px;
  padding: var(--space-3) var(--space-2);
  background: var(--color-control);
  border-radius: var(--radius-md);
}

.date-day {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary-strong);
  line-height: 1;
}

.date-month {
  font-size: 12px;
  color: var(--color-text-soft);
}

.record-main {
  flex: 1;
  display: grid;
  gap: var(--space-3);
  padding-left: var(--space-2);
  align-content: center;
}

.record-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.record-title-row strong {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.record-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2) var(--space-5);
  color: var(--color-text-muted);
  font-size: 13px;
}

.record-hint {
  color: var(--color-text-soft);
  font-style: italic;
}

.record-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: var(--space-2);
}

/* —— 空状态 —— */
.empty-state {
  padding: var(--space-8) var(--space-4);
  color: var(--color-text-muted);
  font-size: 14px;
  text-align: center;
  background: var(--color-control);
  border-radius: var(--radius-lg);
}

.rich-empty {
  display: grid;
  gap: var(--space-3);
  justify-items: center;
  padding: var(--space-8);
}

.rich-empty strong {
  font-size: 18px;
  color: var(--color-text);
}

.rich-empty span {
  max-width: 560px;
  line-height: 1.7;
  color: var(--color-text-muted);
  font-size: 13.5px;
  text-align: center;
}

/* —— 按钮 —— */
.btn-primary,
.btn-outline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-family: inherit;
  font-weight: 600;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: transform var(--duration-fast) var(--ease-standard),
              box-shadow var(--duration-base) var(--ease-standard),
              background var(--duration-base) var(--ease-standard),
              border-color var(--duration-base) var(--ease-standard),
              color var(--duration-base) var(--ease-standard);
  white-space: nowrap;
  user-select: none;
}

.btn-primary {
  padding: 10px 22px;
  background: var(--gradient-primary);
  color: #fff;
  border: none;
  font-size: 14px;
  box-shadow: 0 6px 18px rgba(31, 140, 255, 0.28);
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 26px rgba(31, 140, 255, 0.36);
}

.btn-outline {
  padding: 10px 20px;
  background: var(--color-surface-strong);
  color: var(--color-text);
  border: 1px solid var(--color-border-strong);
  font-size: 14px;
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.btn-sm {
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  font-weight: 600;
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface-strong);
}

.btn-sm:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

/* —— 响应式 —— */
@media (max-width: 880px) {
  .record-card {
    flex-wrap: wrap;
  }
  .record-actions {
    flex-direction: row;
    width: 100%;
  }
}
</style>

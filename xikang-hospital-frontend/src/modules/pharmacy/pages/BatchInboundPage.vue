<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type { BatchInboundItem, BatchInboundResult, DrugOption } from '@/shared/types/pharmacy'

const router = useRouter()

/** 表内一行编辑态 */
interface Row extends BatchInboundItem {
  /** 客户端临时 id，用于 v-for key */
  _key: number
  /** 行级错误标记（drugId/数量/日期等校验失败时填） */
  _err?: string
}

const drugs = ref<DrugOption[]>([])
const loadingDrugs = ref(false)
const rows = ref<Row[]>([])
const submitting = ref(false)
const lastResult = ref<BatchInboundResult | null>(null)
let keySeed = 1

function newRow(): Row {
  return {
    _key: keySeed++,
    drugId: null,
    drugName: '',
    quantity: null,
    batchNumber: '',
    productionDate: '',
    expiryDate: '',
    location: '',
  }
}

function addRow() {
  rows.value.push(newRow())
}

function addMany(n: number) {
  for (let i = 0; i < n; i += 1) addRow()
}

function removeRow(idx: number) {
  rows.value.splice(idx, 1)
}

function clearAll() {
  try {
    if (rows.value.length === 0) return
  } catch {
    /* noop */
  }
  rows.value = []
  lastResult.value = null
}

/** 选中药品后回填名称（便于离线查看） */
function onDrugChange(row: Row, val: number | null) {
  row.drugId = val
  const found = drugs.value.find((d) => d.id === val)
  row.drugName = found?.name ?? ''
  row._err = undefined
}

/** 行级校验：drugId/数量/批号/生产日期/失效日期/货位 全必填 + 日期先后 */
function validateRow(row: Row): string | null {
  if (!row.drugId) return '请选择药品'
  if (!row.quantity || row.quantity <= 0) return '数量必须 > 0'
  if (!row.batchNumber.trim()) return '请输入批号'
  if (!row.productionDate) return '请选择生产日期'
  if (!row.expiryDate) return '请选择失效日期'
  if (row.expiryDate <= row.productionDate) return '失效日期必须晚于生产日期'
  if (!row.location.trim()) return '请输入货位'
  return null
}

const validCount = computed(
  () => rows.value.filter((r) => validateRow(r) === null).length,
)
const totalQuantity = computed(() =>
  rows.value.reduce((sum, r) => sum + (r.quantity ?? 0), 0),
)
const totalAmount = computed(() => {
  let sum = 0
  for (const r of rows.value) {
    if (!r.drugId || !r.quantity) continue
    const drug = drugs.value.find((d) => d.id === r.drugId)
    if (drug?.price) sum += drug.price * r.quantity
  }
  return sum
})

/** 顶部快捷条：粘贴 Excel/TSV（无需额外依赖）
 *  每行格式：药品名称  数量  批号  生产日期(YYYY-MM-DD)  失效日期(YYYY-MM-DD)  货位
 *  Tab 分隔，从 Excel 直接复制即可。
 */
const pasteText = ref('')
function applyPaste() {
  const text = pasteText.value.trim()
  if (!text) {
    ElMessage.warning('请先粘贴 Excel 数据')
    return
  }
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  if (lines.length === 0) return

  const newRows: Row[] = []
  const unmatched: string[] = []
  for (const line of lines) {
    const cols = line.split(/\t|,/).map((c) => c.trim())
    if (cols.length < 6) {
      unmatched.push(line)
      continue
    }
    const [name, qtyStr, batch, prod, exp, loc] = cols
    const matched = drugs.value.find((d) => d.name === name)
    if (!matched) {
      unmatched.push(line)
      continue
    }
    const qty = Number(qtyStr)
    newRows.push({
      _key: keySeed++,
      drugId: matched.id,
      drugName: matched.name,
      quantity: Number.isFinite(qty) && qty > 0 ? qty : null,
      batchNumber: batch,
      productionDate: prod,
      expiryDate: exp,
      location: loc,
    })
  }

  if (unmatched.length > 0) {
    ElMessage.warning(`有 ${unmatched.length} 行未匹配药品，已忽略。请检查药品名称或先在字典中新增。`)
  }
  if (newRows.length === 0) return

  rows.value = [...rows.value, ...newRows]
  pasteText.value = ''
  ElMessage.success(`已导入 ${newRows.length} 行`)
}

async function submit() {
  if (rows.value.length === 0) {
    ElMessage.warning('请先添加入库行')
    return
  }
  // 行级校验
  let firstErrRow = -1
  let firstErrMsg = ''
  rows.value.forEach((r, idx) => {
    const msg = validateRow(r)
    if (msg) {
      r._err = msg
      if (firstErrRow < 0) {
        firstErrRow = idx
        firstErrMsg = msg
      }
    } else {
      r._err = undefined
    }
  })
  if (firstErrRow >= 0) {
    ElMessage.error(`第 ${firstErrRow + 1} 行：${firstErrMsg}`)
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认批量入库 ${rows.value.length} 条记录（共 ${totalQuantity.value} 件）？` +
        `\n提交后任一行失败将整体回滚。`,
      '批量入库确认',
      { type: 'warning', confirmButtonText: '确认入库', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload: BatchInboundItem[] = rows.value.map((r) => ({
      drugId: r.drugId,
      drugName: r.drugName,
      quantity: r.quantity,
      batchNumber: r.batchNumber.trim(),
      productionDate: r.productionDate,
      expiryDate: r.expiryDate,
      location: r.location.trim(),
    }))
    const res = await pharmacyApi.batchInbound(payload)
    lastResult.value = res
    ElMessage.success(
      `批量入库成功 · ${res.successCount ?? rows.value.length} 条 · 共 ${res.totalQuantity ?? totalQuantity.value} 件`,
    )
  } catch (e) {
    // 后端抛业务异常时整体回滚，前端只提示
    const msg = e instanceof Error ? e.message : '批量入库失败，请重试'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

function goToDrugDictionary() {
  void router.push('/pharmacy/drug-dictionary')
}

function backToInventory() {
  void router.push('/pharmacy/inventory')
}

async function loadDrugs() {
  loadingDrugs.value = true
  try {
    drugs.value = await pharmacyApi.drugs()
  } finally {
    loadingDrugs.value = false
  }
}

onMounted(() => {
  void loadDrugs()
  // 默认给两行空白
  addMany(2)
})
</script>

<template>
  <div class="batch-page u-page-grid">
    <PageHeader
      title="批量入库"
      description="一次提交多批药品入库。后端采用单事务，任一行校验失败将整体回滚。"
      eyebrow="Role B / Pharmacy · ②·批量"
    >
      <template #actions>
        <ElButton @click="backToInventory">返回库存</ElButton>
        <ElButton @click="clearAll" :disabled="rows.length === 0">清空表格</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submit">
          确认批量入库
        </ElButton>
      </template>
    </PageHeader>

    <!-- 顶部：操作工具条 + 粘贴导入区 -->
    <GlassCard class="toolbar-card">
      <div class="toolbar">
        <div class="toolbar__left">
          <ElButton type="primary" plain @click="addRow">+ 添加行</ElButton>
          <ElButton @click="addMany(5)">+ 添加 5 行</ElButton>
          <ElButton @click="goToDrugDictionary">+ 新增药品（字典）</ElButton>
        </div>
        <div class="toolbar__summary">
          <ElTag type="info" effect="plain">共 {{ rows.length }} 条</ElTag>
          <ElTag :type="validCount === rows.length ? 'success' : 'warning'" effect="plain">
            有效 {{ validCount }} / {{ rows.length }}
          </ElTag>
          <ElTag type="primary" effect="plain">合计数量 {{ totalQuantity.toLocaleString() }} 件</ElTag>
          <ElTag type="primary" effect="plain">合计金额 ¥ {{ totalAmount.toFixed(2) }}</ElTag>
        </div>
      </div>

      <details class="paste-block">
        <summary>📋 从 Excel 粘贴导入（点击展开）</summary>
        <p class="paste-hint">
          在 Excel 中按以下列准备数据并复制（Tab 分隔），粘贴到下方文本框后点击「导入到表格」：
          <code>药品名称 | 数量 | 批号 | 生产日期(YYYY-MM-DD) | 失效日期(YYYY-MM-DD) | 货位</code>
        </p>
        <ElInput
          v-model="pasteText"
          type="textarea"
          :rows="4"
          placeholder="粘贴 Excel 内容，例如：&#10;阿莫西林胶囊	50	LOT-2026-A1	2026-01-15	2028-01-15	A-3-02&#10;布洛芬片	100	LOT-2026-B2	2026-02-01	2029-02-01	B-1-05"
        />
        <div class="paste-actions">
          <ElButton type="primary" plain :disabled="!pasteText.trim()" @click="applyPaste">
            导入到表格
          </ElButton>
          <ElButton link @click="pasteText = ''">清空粘贴区</ElButton>
        </div>
      </details>
    </GlassCard>

    <!-- 入库明细表 -->
    <GlassCard class="table-card">
      <ElTable :data="rows" row-key="_key" border empty-text="请点击「+ 添加行」开始批量入库">
        <ElTableColumn label="#" width="56" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </ElTableColumn>

        <ElTableColumn label="药品" min-width="220">
          <template #default="{ row }">
            <ElSelect
              :model-value="(row as Row).drugId"
              placeholder="选择药品"
              filterable
              clearable
              :loading="loadingDrugs"
              class="full"
              @update:model-value="(v: number | null) => onDrugChange(row as Row, v)"
            >
              <ElOption
                v-for="d in drugs"
                :key="d.id"
                :label="d.name"
                :value="d.id"
              >
                <span class="drug-option-name">{{ d.name }}</span>
                <span class="drug-option-meta">
                  {{ d.specification || '-' }} · ¥{{ d.price ?? '-' }}
                </span>
              </ElOption>
            </ElSelect>
            <div v-if="(row as Row)._err && !(row as Row).drugId" class="row-err">{{ (row as Row)._err }}</div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="数量" width="120">
          <template #default="{ row }">
            <ElInputNumber
              :model-value="(row as Row).quantity"
              :min="1"
              :controls="false"
              placeholder="数量"
              class="full"
              @update:model-value="(v: number | undefined) => ((row as Row).quantity = v ?? null)"
            />
            <div v-if="(row as Row)._err && (row as Row).drugId && (!(row as Row).quantity || (row as Row).quantity! <= 0)" class="row-err">
              {{ (row as Row)._err }}
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="批号" width="160">
          <template #default="{ row }">
            <ElInput v-model="(row as Row).batchNumber" placeholder="如 LOT-2026-08" />
            <div v-if="(row as Row)._err && (row as Row).drugId && (row as Row).quantity && !(row as Row).batchNumber.trim()" class="row-err">
              {{ (row as Row)._err }}
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="生产日期" width="170">
          <template #default="{ row }">
            <ElDatePicker
              v-model="(row as Row).productionDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="生产日期"
              class="full"
            />
            <div v-if="(row as Row)._err && (row as Row).drugId && (row as Row).quantity && (row as Row).batchNumber && !(row as Row).productionDate" class="row-err">
              {{ (row as Row)._err }}
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="失效日期" width="170">
          <template #default="{ row }">
            <ElDatePicker
              v-model="(row as Row).expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="失效日期"
              class="full"
            />
            <div v-if="(row as Row)._err && (row as Row).drugId && (row as Row).quantity && (row as Row).batchNumber && (row as Row).productionDate && !(row as Row).expiryDate" class="row-err">
              {{ (row as Row)._err }}
            </div>
            <div v-else-if="(row as Row)._err && (row as Row).productionDate && (row as Row).expiryDate && (row as Row).expiryDate! <= (row as Row).productionDate!" class="row-err">
              {{ (row as Row)._err }}
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="货位" width="140">
          <template #default="{ row }">
            <ElInput v-model="(row as Row).location" placeholder="如 A-3-02" />
            <div v-if="(row as Row)._err && (row as Row).drugId && (row as Row).quantity && (row as Row).batchNumber && (row as Row).productionDate && (row as Row).expiryDate && !(row as Row).location.trim()" class="row-err">
              {{ (row as Row)._err }}
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn label="单价" width="100" align="right">
          <template #default="{ row }">
            <template v-if="(row as Row).drugId">
              <span class="unit-price">¥ {{ (drugs.find((d) => d.id === (row as Row).drugId)?.price ?? 0).toFixed(2) }}</span>
            </template>
            <template v-else>
              <span class="unit-price muted">-</span>
            </template>
          </template>
        </ElTableColumn>

        <ElTableColumn label="小计" width="120" align="right">
          <template #default="{ row }">
            <template v-if="(row as Row).drugId && (row as Row).quantity">
              ¥ {{ ((drugs.find((d) => d.id === (row as Row).drugId)?.price ?? 0) * (row as Row).quantity!).toFixed(2) }}
            </template>
            <template v-else>
              <span class="muted">-</span>
            </template>
          </template>
        </ElTableColumn>

        <ElTableColumn label="操作" width="80" align="center" fixed="right">
          <template #default="{ $index }">
            <ElButton link type="danger" size="small" @click="removeRow($index)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </GlassCard>

    <!-- 提交结果 -->
    <GlassCard v-if="lastResult" class="result-card" :tone="'success'">
      <div class="result-row">
        <div class="result-row__icon">✓</div>
        <div class="result-row__body">
          <strong>批量入库成功</strong>
          <div class="result-stats">
            <span>共 {{ lastResult.successCount ?? rows.length }} 条</span>
            <span>·</span>
            <span>数量 {{ lastResult.totalQuantity ?? totalQuantity }} 件</span>
            <span>·</span>
            <span>金额 ¥ {{ (lastResult.totalAmount ?? totalAmount).toFixed(2) }}</span>
          </div>
        </div>
        <div class="result-row__action">
          <ElButton type="primary" @click="backToInventory">返回库存</ElButton>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.batch-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.toolbar-card {
  padding: var(--space-4) var(--space-5);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.toolbar__left {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.toolbar__summary {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.paste-block {
  margin-block-start: var(--space-4);
  padding-block-start: var(--space-3);
  border-block-start: 1px dashed var(--color-border);
}

.paste-block summary {
  cursor: pointer;
  font-weight: 600;
  color: var(--color-primary);
  user-select: none;
}

.paste-hint {
  margin: var(--space-3) 0;
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.paste-hint code {
  background: var(--color-surface-2, #f5f7fa);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  display: inline-block;
  margin-block-start: 4px;
}

.paste-actions {
  margin-block-start: var(--space-2);
  display: flex;
  gap: var(--space-2);
}

.table-card {
  padding: var(--space-5);
}

.full {
  width: 100%;
}

.drug-option-name {
  font-weight: 500;
}

.drug-option-meta {
  margin-left: 8px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.row-err {
  font-size: 11px;
  color: var(--color-danger, #f56c6c);
  margin-block-start: 2px;
  line-height: 1.3;
}

.unit-price {
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--color-text-muted);
}

.result-card {
  padding: var(--space-4) var(--space-5);
  border-left: 4px solid var(--color-success, #67c23a);
}

.result-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.result-row__icon {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-success, #67c23a);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-row__body {
  flex: 1 1 auto;
}

.result-stats {
  display: flex;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-muted);
  margin-block-start: 2px;
}

.result-row__action {
  flex: 0 0 auto;
}
</style>

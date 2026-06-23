<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElEmpty,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { pharmacyApi } from '@/shared/api/modules/pharmacy'
import type {
  OperatorStatItem,
  StatisticsResult,
  TopDrugItem,
} from '@/shared/types/pharmacy'

const loading = ref(false)
const result = ref<StatisticsResult | null>(null)
const loaded = ref(false)

// 默认查最近 30 天
function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 30)
  return [fmt(start), fmt(end)]
}
function fmt(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const dateRange = ref<[string, string]>(defaultRange())
const topLimit = ref(10)

const overview = computed(() => result.value?.overview ?? {})
const topDrugs = computed<TopDrugItem[]>(() => result.value?.topDrugs ?? [])
const operatorStats = computed<OperatorStatItem[]>(() => result.value?.operatorStats ?? [])

async function load() {
  loading.value = true
  try {
    const params: { startDate: string; endDate: string; topLimit: number } = {
      startDate: `${dateRange.value[0]}T00:00:00`,
      endDate: `${dateRange.value[1]}T23:59:59`,
      topLimit: topLimit.value,
    }
    result.value = await pharmacyApi.statistics(params)
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function setPreset(days: number) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days)
  dateRange.value = [fmt(start), fmt(end)]
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="stats-page u-page-grid">
    <PageHeader
      title="消耗统计"
      description="按时间维度汇总药房工作量：发药单数、发放数量与金额、药品 TOP、药师工作量。"
      eyebrow="Role B / Pharmacy · ⑦"
    >
      <template #actions>
        <ElButton @click="load">刷新</ElButton>
      </template>
    </PageHeader>

    <!-- 时间范围筛选条 -->
    <GlassCard class="filter-card">
      <div class="filter-bar">
        <div class="filter-bar__inputs">
          <ElDatePicker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
          <ElSelect v-model="topLimit" class="field-fixed" placeholder="TOP 数">
            <ElOption :value="5" label="TOP 5" />
            <ElOption :value="10" label="TOP 10" />
            <ElOption :value="20" label="TOP 20" />
          </ElSelect>
        </div>
        <ElButton text @click="setPreset(7)">近 7 天</ElButton>
        <ElButton text @click="setPreset(30)">近 30 天</ElButton>
        <ElButton text @click="setPreset(90)">近 90 天</ElButton>
        <ElButton type="primary" @click="load">查询</ElButton>
      </div>
    </GlassCard>

    <div v-loading="loading">
      <!-- KPI 卡片组 -->
      <div class="kpi-grid">
        <GlassCard class="kpi-card kpi-card--primary">
          <div class="kpi-label">发药单数</div>
          <div class="kpi-value">{{ overview.prescriptionCount ?? 0 }}</div>
          <div class="kpi-hint">期内不同挂号数</div>
        </GlassCard>
        <GlassCard class="kpi-card kpi-card--success">
          <div class="kpi-label">发放数量</div>
          <div class="kpi-value">{{ overview.dispensedQuantity ?? 0 }}</div>
          <div class="kpi-hint">件（按发放流水累加）</div>
        </GlassCard>
        <GlassCard class="kpi-card kpi-card--info">
          <div class="kpi-label">发放金额</div>
          <div class="kpi-value">¥ {{ Number(overview.dispensedAmount ?? 0).toFixed(2) }}</div>
          <div class="kpi-hint">元</div>
        </GlassCard>
        <GlassCard class="kpi-card kpi-card--warning">
          <div class="kpi-label">入库数量</div>
          <div class="kpi-value">{{ overview.inboundQuantity ?? 0 }}</div>
          <div class="kpi-hint">件（同期入库合计）</div>
        </GlassCard>
      </div>

      <ElEmpty
        v-if="loaded && !overview.prescriptionCount"
        description="所选时间范围内暂无发药记录"
      />

      <div v-if="overview.prescriptionCount" class="tables-grid">
        <!-- TOP 药品 -->
        <GlassCard class="table-card">
          <div class="section-title">
            <h3>发放量 TOP {{ topLimit }} 药品</h3>
            <StatusTag tone="primary">{{ topDrugs.length }} 种</StatusTag>
          </div>
          <ElTable :data="topDrugs" size="small">
            <ElTableColumn type="index" label="排名" width="70" align="center" />
            <ElTableColumn prop="drugName" label="药品" min-width="180" />
            <ElTableColumn prop="dispensedQuantity" label="发放数量" min-width="110" align="right" />
            <ElTableColumn prop="dispenseTimes" label="出现次数" min-width="110" align="right" />
          </ElTable>
          <ElEmpty v-if="topDrugs.length === 0" description="无数据" />
        </GlassCard>

        <!-- 药师工作量 -->
        <GlassCard class="table-card">
          <div class="section-title">
            <h3>药师工作量</h3>
            <StatusTag tone="primary">{{ operatorStats.length }} 位</StatusTag>
          </div>
          <ElTable :data="operatorStats" size="small">
            <ElTableColumn prop="operatorName" label="药师" min-width="120" />
            <ElTableColumn prop="operationCount" label="总操作数" min-width="100" align="right" />
            <ElTableColumn prop="dispenseCount" label="发药次数" min-width="100" align="right" />
            <ElTableColumn prop="inboundCount" label="入库次数" min-width="100" align="right" />
          </ElTable>
          <ElEmpty v-if="operatorStats.length === 0" description="无数据" />
        </GlassCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-card {
  padding: var(--space-3) var(--space-4);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.filter-bar__inputs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.field-fixed {
  width: 130px;
}

/* ===== KPI 卡片 ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-4);
  margin-block-start: var(--space-4);
}

.kpi-card {
  padding: var(--space-4) var(--space-5);
  border-left: 4px solid var(--color-primary);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.kpi-card--success { border-left-color: var(--color-success, #67c23a); }
.kpi-card--info    { border-left-color: var(--color-info, #909399); }
.kpi-card--warning { border-left-color: var(--color-warning, #e6a23c); }

.kpi-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.1;
}

.kpi-hint {
  font-size: 12px;
  color: var(--color-text-muted);
}

/* ===== 表格区 ===== */
.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: var(--space-4);
  margin-block-start: var(--space-4);
}

.table-card {
  padding: var(--space-4) var(--space-5);
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-block-end: var(--space-3);
}

.section-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
</style>

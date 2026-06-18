<script setup lang="ts">
import { computed } from 'vue'
import {
  LAB_REPORT_DISCLAIMER,
  statusIndicator,
  type LabReportPdfContext,
} from '@/shared/types/labReportPdf'

const props = defineProps<{
  context: LabReportPdfContext | null
}>()

const rows = computed(() => props.context?.result.resultItems ?? [])
</script>

<template>
  <article v-if="context" class="lab-report-sheet">
    <header class="lab-report-sheet__header">
      <h1 class="lab-report-sheet__hospital">{{ context.hospitalName }}</h1>
      <h2 class="lab-report-sheet__title">{{ context.reportTitle }}</h2>
    </header>

    <section class="lab-report-sheet__meta">
      <div class="lab-report-sheet__meta-row">
        <span><strong>姓名：</strong>{{ context.patientName }}</span>
        <span><strong>病历号：</strong>{{ context.caseNumber }}</span>
        <span><strong>性别：</strong>{{ context.gender }}</span>
        <span><strong>年龄：</strong>{{ context.age }}</span>
      </div>
      <div class="lab-report-sheet__meta-row">
        <span><strong>检验项目：</strong>{{ context.techName }}</span>
        <span><strong>项目编码：</strong>{{ context.techCode }}</span>
      </div>
      <div class="lab-report-sheet__meta-row">
        <span><strong>检验部位：</strong>{{ context.position }}</span>
        <span><strong>目的要求：</strong>{{ context.purpose }}</span>
      </div>
      <div class="lab-report-sheet__meta-row">
        <span><strong>报告时间：</strong>{{ context.reportTime }}</span>
      </div>
    </section>

    <table class="lab-report-sheet__table">
      <thead>
        <tr>
          <th>序号</th>
          <th>检查项目</th>
          <th>结果</th>
          <th>单位</th>
          <th>提示</th>
          <th>参考区间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(item, index) in rows" :key="`${item.itemCode}-${index}`">
          <td>{{ index + 1 }}</td>
          <td>{{ item.itemName }}</td>
          <td>{{ item.value }}</td>
          <td>{{ item.unit || '-' }}</td>
          <td class="lab-report-sheet__indicator">{{ statusIndicator(item.status) }}</td>
          <td>{{ item.referenceRange || '-' }}</td>
        </tr>
      </tbody>
    </table>

    <footer class="lab-report-sheet__footer">
      <p><strong>结论：</strong>{{ context.result.conclusion || '-' }}</p>
      <p v-if="context.result.notice"><strong>备注：</strong>{{ context.result.notice }}</p>
      <p class="lab-report-sheet__disclaimer">{{ LAB_REPORT_DISCLAIMER }}</p>
    </footer>
  </article>
</template>

<style scoped>
.lab-report-sheet {
  box-sizing: border-box;
  width: 210mm;
  min-height: 297mm;
  padding: 12mm 14mm;
  color: #111;
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 12px;
  line-height: 1.5;
  background: #fff;
}

.lab-report-sheet__header {
  text-align: center;
  margin-block-end: 10px;
  padding-block-end: 8px;
  border-block-end: 2px solid #111;
}

.lab-report-sheet__hospital {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}

.lab-report-sheet__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.lab-report-sheet__meta {
  margin-block-end: 12px;
}

.lab-report-sheet__meta-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
  margin-block-end: 4px;
}

.lab-report-sheet__table {
  width: 100%;
  border-collapse: collapse;
  margin-block-end: 16px;
}

.lab-report-sheet__table thead {
  border-block: 1px solid #111;
}

.lab-report-sheet__table th,
.lab-report-sheet__table td {
  padding: 5px 6px;
  text-align: left;
  vertical-align: top;
}

.lab-report-sheet__table th {
  font-weight: 600;
}

.lab-report-sheet__indicator {
  text-align: center;
  font-weight: 700;
}

.lab-report-sheet__footer p {
  margin: 0 0 8px;
  white-space: pre-wrap;
}

.lab-report-sheet__disclaimer {
  margin-block-start: 16px;
  padding-block-start: 8px;
  border-block-start: 1px solid #ccc;
  color: #555;
  font-size: 11px;
}
</style>
